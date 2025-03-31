package com.openpositioning.PositionMe.Fusion;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.openpositioning.PositionMe.sensors.SensorFusion;
import com.openpositioning.PositionMe.utils.CoordinateTransform;
import com.openpositioning.PositionMe.utils.ExponentialSmoothingFilter;
import org.ejml.simple.SimpleMatrix;
/**
 * MidLevelKalmanFilter - A moderately complex Extended Kalman Filter (EKF) for fusing PDR with GNSS/Wi-Fi data.
 * State Vector: [ bearing (rad); X (m); Y (m) ]
 * Prediction Step:
 *   - Updates the state using measured step length and bearing:
 *         X_k+1 = [ measuredBearing;
 *                   X_k + stepLength * cos(measuredBearing);
 *                   Y_k + stepLength * sin(measuredBearing) ]
 *   - Uses a simplified state transition (approx. identity) and maps 2x2 process noise to 3x3 via a Jacobian.
 * Update Step:
 *   - Corrects the prediction using external (GNSS or Wi-Fi) measurements:
 *         Z = [ measuredEast; measuredNorth ]
 *   - Applies a linear observation model (H extracts X and Y),
 *     computes the Kalman Gain, and updates the state and covariance.
 * Extra Notes:
 *   - Computations run on a background thread to keep the UI responsive.
 *   - An exponential smoothing filter is used to stabilize the output.
 *   - Opportunistic updates are only applied if new measurements are timely.
 *
 * @see SensorFusion for the sensor integration and fusion algorithms.
 */
public class EKF implements FusionAlgorithm {

    // ----------------------------
    // 1) Constants and Tunable Parameters
    // ----------------------------
    private ExponentialSmoothingFilter smoothingFilter;
    private static final double DEFAULT_STEP_LENGTH = 0.7; // meters
    private static final double DEFAULT_BEARING_STD = Math.toRadians(10); // bearing noise standard deviation (rad)
    private static final double DEFAULT_STEP_STD = 0.5;    // step length noise standard deviation (m)
    private static final double DEFAULT_GNSS_STD = 5;      // GNSS position measurement standard deviation (m)
    private static final double DEFAULT_WIFI_STD = 8;      // Wi-Fi position measurement standard deviation (m)
    // Fields for opportunistic updates
    private long lastOpUpdateTime = 0; // timestamp of the last opportunistic update
    private static final long RELEVANCE_THRESHOLD_MS = 10000; // threshold for measurement relevance (ms)

    // ----------------------------
    // 2) State, Covariance, and Noise Matrices
    // ----------------------------
    // State vector Xk: 3x1 = [ bearing; x; y ]
    private SimpleMatrix Xk;   // 3x1 state vector
    // Covariance matrix Pk: 3x3
    private SimpleMatrix Pk;   // 3x3 state covariance

    // State transition matrix Fk (updated dynamically during prediction)
    private SimpleMatrix Fk;   // 3x3
    // Process noise matrix Qk
    // (Initially defined for a 2D system [bearing, step], then mapped into a 3x3 matrix via a transform)
    private SimpleMatrix Qk;   // 2x2 for [bearing, step] noise; later mapped to 3x3
    // Observation matrix Hk: 2x3 (observing only the x and y positions)
    private SimpleMatrix Hk;   // 2x3
    // Observation noise matrix Rk: 2x2 (will be switched based on GNSS or Wi-Fi measurements)
    private SimpleMatrix Rk;   // 2x2

    // A HandlerThread for running EKF computations asynchronously to avoid blocking the main thread
    private HandlerThread ekfThread;
    private Handler ekfHandler;

    // ----------------------------
    // 3) Other Parameters
    // ----------------------------
    private boolean stopEKF = false;
    private double prevStepLength = DEFAULT_STEP_LENGTH; // previous step length measurement

    /**
     * Constructor for the EKF.
     * Initializes the state vector, covariance, observation matrix, noise matrices, and starts a background thread.
     */
    public EKF() {
        // Initialize the exponential smoothing filter with a smoothing factor of 0.8 and window size 2
        this.smoothingFilter = new ExponentialSmoothingFilter(0.8, 2);

        // Initialize state: assume initial bearing = 0, and starting coordinates (x, y) = (0, 0)
        this.Xk = new SimpleMatrix(new double[][] {
                { 0.0 },  // bearing (rad)
                { 0.0 },  // x (m)
                { 0.0 }   // y (m)
        });

        // Initialize covariance with relatively high uncertainty
        this.Pk = SimpleMatrix.diag((Math.toRadians(30)) * (Math.toRadians(30)),
                100,
                100);

        // Initialize the observation matrix Hk to extract x and y from the state vector:
        //   z = [ 0 1 0; 0 0 1 ] * Xk
        this.Hk = new SimpleMatrix(new double[][] {
                { 0.0, 1.0, 0.0 },
                { 0.0, 0.0, 1.0 }
        });

        // Initialize observation noise matrix Rk with GNSS standard deviation values
        this.Rk = SimpleMatrix.diag(DEFAULT_GNSS_STD * DEFAULT_GNSS_STD,
                DEFAULT_GNSS_STD * DEFAULT_GNSS_STD);

        // Initialize process noise matrix Qk for the [bearing, step] variables
        // Qk (2x2) = diag(bearingStd^2, stepStd^2)
        this.Qk = SimpleMatrix.diag(
                DEFAULT_BEARING_STD * DEFAULT_BEARING_STD,
                DEFAULT_STEP_STD * DEFAULT_STEP_STD
        );

        // Initialize the background handler for asynchronous EKF computation
        initialiseBackgroundHandler();
    }

    /**
     * Initializes a background HandlerThread and corresponding Handler for the EKF.
     */
    private void initialiseBackgroundHandler() {
        ekfThread = new HandlerThread("MidEKFThread");
        ekfThread.start();
        ekfHandler = new Handler(ekfThread.getLooper());
    }

    // ----------------------------
    // 4) Prediction Step
    // ----------------------------
    /**
     * Performs the prediction step of the EKF.
     * Updates the state using the measured bearing and step length.
     *
     * @param measuredBearing   the measured bearing (in radians)
     * @param measuredStepLength the measured step length (in meters)
     */
    public void predict(final double measuredBearing, final double measuredStepLength) {
        if (stopEKF) return;

        ekfHandler.post(new Runnable() {
            @Override
            public void run() {
                // Compute the new bearing and the trigonometric functions
                double newBearing = measuredBearing; // Simplified: directly set to measured bearing
                double cosB = Math.cos(newBearing);
                double sinB = Math.sin(newBearing);

                // Update state vector:
                // Update bearing
                Xk.set(0, 0, newBearing);
                // Update position using step length and bearing
                double oldX = Xk.get(1, 0);
                double oldY = Xk.get(2, 0);
                double newX = oldX + measuredStepLength * cosB;
                double newY = oldY + measuredStepLength * sinB;
                Xk.set(1, 0, newX);
                Xk.set(2, 0, newY);

                // Build the state transition matrix Fk (simplified as identity here)
                Fk = new SimpleMatrix(new double[][] {
                        {1.0, 0.0, 0.0},  // bearing remains unchanged
                        {0.0, 1.0, 0.0},  // x update: add measured change
                        {0.0, 0.0, 1.0}   // y update: add measured change
                });

                // Construct the mapping matrix L (3x2) that relates the process noise to the state:
                // For x: ∂x/∂bearing ≈ -step*sin(bearing), ∂x/∂step = cos(bearing)
                // For y: ∂y/∂bearing ≈ step*cos(bearing),  ∂y/∂step = sin(bearing)
                double db_dbearing = 1.0; // bearing is directly updated
                double db_dstep = 0.0;
                double dx_dbearing = -measuredStepLength * sinB;
                double dx_dstep = cosB;
                double dy_dbearing = measuredStepLength * cosB;
                double dy_dstep = sinB;
                SimpleMatrix L = new SimpleMatrix(new double[][] {
                        {db_dbearing, db_dstep},
                        {dx_dbearing, dx_dstep},
                        {dy_dbearing, dy_dstep}
                });

                // Map the 2x2 process noise Qk to a 3x3 matrix: Q3x3 = L * Qk * L^T
                SimpleMatrix Q_3x3 = L.mult(Qk).mult(L.transpose());

                // Predict the new covariance: Pk = Fk * Pk * Fk^T + Q_3x3
                SimpleMatrix temp = Fk.mult(Pk).mult(Fk.transpose());
                Pk = temp.plus(Q_3x3);

                prevStepLength = measuredStepLength;
            }
        });
    }

    // ----------------------------
    // 5) Update Step
    // ----------------------------
    /**
     * Performs the update step of the EKF using a new measurement.
     * The measurement can be from GNSS or Wi-Fi.
     *
     * @param measEast  the measured east coordinate
     * @param measNorth the measured north coordinate
     * @param isGNSS    true if the measurement is from GNSS, false if from Wi-Fi
     */
    public void update(final double measEast, final double measNorth, final boolean isGNSS) {
        if (stopEKF) return;

        ekfHandler.post(new Runnable() {
            @Override
            public void run() {
                // Set the observation noise Rk based on the source of measurement
                double stdPos = isGNSS ? DEFAULT_GNSS_STD : DEFAULT_WIFI_STD;
                Rk = SimpleMatrix.diag(stdPos * stdPos, stdPos * stdPos);

                // Form the measurement vector Zk (2x1)
                SimpleMatrix Zk = new SimpleMatrix(new double[][] {
                        { measEast },
                        { measNorth }
                });

                // Compute the innovation (measurement residual): Y = Zk - Hk * Xk
                SimpleMatrix Y = Zk.minus(Hk.mult(Xk));

                // Innovation covariance: S = Hk * Pk * Hk^T + Rk
                SimpleMatrix S = Hk.mult(Pk).mult(Hk.transpose()).plus(Rk);

                // Kalman Gain: K = Pk * Hk^T * S^-1
                SimpleMatrix K = Pk.mult(Hk.transpose().mult(S.invert()));

                // Update state: Xk = Xk + K * Y
                Xk = Xk.plus(K.mult(Y));

                // Update covariance: Pk = (I - K * Hk) * Pk
                SimpleMatrix I3 = SimpleMatrix.identity(3);
                Pk = I3.minus(K.mult(Hk)).mult(Pk);
            }
        });
    }

    // ----------------------------
    // 6) Get Current State
    // ----------------------------
    /**
     * Returns the current state vector as an array: [ bearing, x, y ]
     *
     * @return a double array containing the state values.
     */
    public double[] getState() {
        return new double[] {
                Xk.get(0, 0),
                Xk.get(1, 0),
                Xk.get(2, 0)
        };
    }

    // ----------------------------
    // 7) Stop Fusion and Release Resources
    // ----------------------------
    /**
     * Stops the EKF processing and releases the background thread.
     */
    public void stopFusion() {
        stopEKF = true;
        ekfHandler.post(new Runnable() {
            @Override
            public void run() {
                ekfThread.quitSafely();
            }
        });
    }

    // ----------------------------
    // Opportunistic Update
    // ----------------------------
    /**
     * Processes an opportunistic update from a new measurement.
     * It checks the relevance based on the time threshold before invoking the normal update.
     *
     * @param measEast  the measured east coordinate
     * @param measNorth the measured north coordinate
     * @param isGNSS    true if the measurement is from GNSS, false otherwise
     * @param refTime   the timestamp of the new measurement
     */
    public void onOpportunisticUpdate(final double measEast, final double measNorth,
                                      final boolean isGNSS, final long refTime) {
        if (stopEKF) return;

        ekfHandler.post(new Runnable() {
            @Override
            public void run() {
                // Check if the new measurement is within the relevance threshold
                if (!checkRelevance(refTime)) {
                    return;
                }
                // Update the last update time
                lastOpUpdateTime = refTime;
                // Call the standard update step
                update(measEast, measNorth, isGNSS);
            }
        });
    }

    /**
     * Processes a step detection event, updating the filter based on the PDR measurement.
     * It combines the PDR data with available GNSS data to update the fused location.
     *
     * @param pdrEast the east component from PDR
     * @param pdrNorth the north component from PDR
     * @param altitude the current altitude
     * @param refTime the reference time for the measurement
     */
    public void onStepDetected(double pdrEast, double pdrNorth, double altitude, long refTime) {
        if (stopEKF) return;

        ekfHandler.post(() -> {
            double[] lastGNSS = SensorFusion.getInstance().getGNSSLatLngAlt(false);
            double[] startRef = SensorFusion.getInstance().getGNSSLatLngAlt(true);

            if (lastGNSS != null && startRef != null) {
                double[] enu = CoordinateTransform.geodeticToEnu(
                        lastGNSS[0], lastGNSS[1], lastGNSS[2],
                        startRef[0], startRef[1], startRef[2]
                );
                double observationEast = enu[0];
                double observationNorth = enu[1];

                // Compute the innovation based on the discrepancy between PDR and GNSS
                double[] observation = new double[] {
                        pdrEast - observationEast,
                        pdrNorth - observationNorth
                };

                update(observation[0], observation[1], true);
            } else {
                double predictedEast = Xk.get(1, 0);
                double predictedNorth = Xk.get(2, 0);
                double[] observation = new double[] {
                        pdrEast - predictedEast,
                        pdrNorth - predictedNorth
                };
                update(observation[0], observation[1], false); // Use a penalty factor if necessary
            }

            // Smooth the updated state for UI display
            double[] smoothed = smoothingFilter.applySmoothing(new double[] {
                    Xk.get(1, 0), Xk.get(2, 0)
            });

            double[] ecefRef = SensorFusion.getInstance().getEcefRefCoords();
            SensorFusion.getInstance().notifyFusedUpdate(
                    CoordinateTransform.enuToGeodetic(smoothed[0], smoothed[1], altitude, startRef[0], startRef[1], ecefRef)
            );
        });
    }

    // ----------------------------
    // Additional Methods for FusionAlgorithm Interface
    // ----------------------------
    @Override
    public void init(float[] initialPos) {
        // Set the initial x and y position from the provided coordinates
        Xk.set(1, 0, initialPos[0]);
        Xk.set(2, 0, initialPos[1]);
    }

    @Override
    public void predict(float[] delta) {
        double deltaEast = delta[0];
        double deltaNorth = delta[1];

        double oldX = Xk.get(1, 0);
        double oldY = Xk.get(2, 0);

        double newX = oldX + deltaEast;
        double newY = oldY + deltaNorth;

        Xk.set(1, 0, newX);
        Xk.set(2, 0, newY);
        // Optionally update the covariance matrix Pk if needed
    }

    @Override
    public void updateFromGnss(float[] gnssPos) {
        update(gnssPos[0], gnssPos[1], true);  // isGNSS = true
    }

    @Override
    public void updateFromWifi(float[] wifiPos) {
        update(wifiPos[0], wifiPos[1], false);
    }

    @Override
    public float[] getFusedPosition() {
        return new float[] {
                (float) Xk.get(1, 0),
                (float) Xk.get(2, 0)
        };
    }

    // ----------------------------
    // 8) Utility Methods
    // ----------------------------
    /**
     * Checks whether the incoming measurement is still relevant based on a time threshold.
     *
     * @param refTime the timestamp of the new measurement
     * @return true if the measurement is relevant, false otherwise.
     */
    private boolean checkRelevance(long refTime) {
        long dt = Math.abs(refTime - lastOpUpdateTime);
        return dt <= RELEVANCE_THRESHOLD_MS;
    }
}


