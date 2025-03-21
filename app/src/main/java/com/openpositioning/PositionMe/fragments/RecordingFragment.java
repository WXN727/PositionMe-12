package com.openpositioning.PositionMe.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.preference.PreferenceManager;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.openpositioning.PositionMe.IndoorMapManager;
import com.openpositioning.PositionMe.R;
import com.openpositioning.PositionMe.UtilFunctions;
import com.openpositioning.PositionMe.sensors.SensorFusion;
import com.openpositioning.PositionMe.sensors.SensorTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple {@link Fragment} subclass. The recording fragment is displayed while the app is actively
 * saving data, with UI elements and a map with a marker indicating current PDR location and
 * direction of movement status. The user's PDR trajectory/path being recorded
 * is drawn on the map as well.
 * An overlay of indoor maps for the building is achieved when the user is in the Nucleus
 * and Library buildings to allow for a better user experience.
 *
 * @see HomeFragment the previous fragment in the nav graph.
 * @see CorrectionFragment the next fragment in the nav graph.
 * @see SensorFusion the class containing sensors and recording.
 * @see IndoorMapManager responsible for overlaying the indoor floor maps
 *
 * @author Mate Stodulka
 * @author Arun Gopalakrishnan
 */
public class RecordingFragment extends Fragment implements SensorFusion.SensorFusionUpdates {
    private Marker gnssMarker;

    private List<Marker> gnssMarkers = new ArrayList<>();
    private Marker wifiMarker;
    private List<Marker> wifiMarkers = new ArrayList<>();
    //Button to end PDR recording
    private Button stopButton;
    private Button cancelButton;
    //Recording icon to show user recording is in progress
    private ImageView recIcon;
    //Loading bar to show time remaining before recording automatically ends
    private ProgressBar timeRemaining;
    //Text views to display distance travelled and elevation since beginning of recording

    private TextView elevation;
    private TextView distanceTravelled;
    // Text view to show the error between current PDR and current GNSS
    private TextView gnssError;

    //App settings
    private SharedPreferences settings;
    //Singleton class to collect all sensor data
    private SensorFusion sensorFusion;
    //Timer to end recording
    private CountDownTimer autoStop;
    // Responsible for updating UI in Loop
    private Handler refreshDataHandler;

    //variables to store data of the trajectory
    private float distance;
    private float previousPosX;
    private float previousPosY;

    // Starting point coordinates
    private static LatLng start;
    // Storing the google map object
    private GoogleMap gMap;
    //Switch Map Dropdown
    private Spinner switchMapSpinner;
    //Map Marker
    private Marker orientationMarker;
    // Current Location coordinates
    private LatLng currentLocation;
    // Next Location coordinates
    private LatLng nextLocation;
    // Stores the polyline object for plotting path
    private Polyline polyline;
    // Manages overlaying of the indoor maps
    public IndoorMapManager indoorMapManager;
    // Floor Up button
    public FloatingActionButton floorUpButton;
    // Floor Down button
    public FloatingActionButton floorDownButton;
    // GNSS Switch
    private Switch gnss;
    private Switch wifi;
    // GNSS marker

    // Button used to switch colour
    private Button switchColor;
    // Current color of polyline
    private boolean isRed=true;
    // Switch used to set auto floor
    private Switch autoFloor;

    /**
     * Public Constructor for the class.
     * Left empty as not required
     */
    public RecordingFragment() {
        // Required empty public constructor
    }

    // 其他变量…

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sensorFusion = SensorFusion.getInstance();
        Context context = getActivity();
        settings = PreferenceManager.getDefaultSharedPreferences(context);
        refreshDataHandler = new Handler();
    }
    //Wifi update from sensor fusion

    @Override
    public void onWifiUpdate(LatLng latlngFromWifiServer){
        requireActivity().runOnUiThread(() -> {
            if (latlngFromWifiServer == null) {
                // 隐藏 WiFi Marker（如果有）
                if (wifiMarker != null) {
                    wifiMarker.remove();
                    wifiMarker = null;
                }
                return;
            }
            // 显示 WiFi Marker
            if (gMap != null) {
//                if (wifiMarker != null) wifiMarker.remove(); // 移除旧的
                wifiMarker = gMap.addMarker(new MarkerOptions()
                        .position(latlngFromWifiServer)
                        .title("WiFi Location")
                        .icon(BitmapDescriptorFactory.fromBitmap(
                                UtilFunctions.getBitmapFromVector(getContext(), R.drawable.blue_hollow_circle))));
            }
        });
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_recording, container, false);
        ((AppCompatActivity) getActivity()).getSupportActionBar().hide();
        getActivity().setTitle("Recording...");
        float[] startPosition = sensorFusion.getGNSSLatitude(true);
        SupportMapFragment supportMapFragment = (SupportMapFragment)
                getChildFragmentManager().findFragmentById(R.id.RecordingMap);
        supportMapFragment.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap map) {
                gMap = map;
                indoorMapManager = new IndoorMapManager(map);
                map.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                map.getUiSettings().setCompassEnabled(true);
                map.getUiSettings().setTiltGesturesEnabled(true);
                map.getUiSettings().setRotateGesturesEnabled(true);
                map.getUiSettings().setScrollGesturesEnabled(true);

                start = new LatLng(startPosition[0], startPosition[1]);
                currentLocation = start;
                orientationMarker = map.addMarker(new MarkerOptions()
                        .position(start)
                        .title("Current Position")
                        .flat(true)
                        .icon(BitmapDescriptorFactory.fromBitmap(
                                UtilFunctions.getBitmapFromVector(getContext(), R.drawable.ic_baseline_navigation_24))));
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(start, 19f));
                PolylineOptions polylineOptions = new PolylineOptions()
                        .color(Color.RED)
                        .add(currentLocation);
                polyline = gMap.addPolyline(polylineOptions);
                indoorMapManager.setCurrentLocation(currentLocation);
                indoorMapManager.setIndicationOfIndoorMap();
            }
        });
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 初始化各个 UI 组件
        timeRemaining = getView().findViewById(R.id.timeRemainingBar);
        elevation = getView().findViewById(R.id.currentElevation);
        distanceTravelled = getView().findViewById(R.id.currentDistanceTraveled);
        gnssError = getView().findViewById(R.id.gnssError);
        gnssError.setVisibility(View.GONE);
        elevation.setText(getString(R.string.elevation, "0"));
        distanceTravelled.setText(getString(R.string.meter, "0"));

        distance = 0f;
        previousPosX = 0f;
        previousPosY = 0f;

        stopButton = getView().findViewById(R.id.stopButton);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 停止计时器并停止记录
                if (autoStop != null) autoStop.cancel();
                sensorFusion.stopRecording();
                NavDirections action = RecordingFragmentDirections.actionRecordingFragmentToCorrectionFragment();
                Navigation.findNavController(view).navigate(action);
            }
        });

        cancelButton = getView().findViewById(R.id.cancelButton);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sensorFusion.stopRecording();
                NavDirections action = RecordingFragmentDirections.actionRecordingFragmentToHomeFragment();
                Navigation.findNavController(view).navigate(action);
                if (autoStop != null) autoStop.cancel();
            }
        });

        mapDropdown();
        switchMap();
        floorUpButton = getView().findViewById(R.id.floorUpButton);
        floorDownButton = getView().findViewById(R.id.floorDownButton);
        autoFloor = getView().findViewById(R.id.autoFloor);
        autoFloor.setChecked(true);
        setFloorButtonVisibility(View.GONE);
        floorUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                autoFloor.setChecked(false);
                indoorMapManager.increaseFloor();
            }
        });
        floorDownButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                autoFloor.setChecked(false);
                indoorMapManager.decreaseFloor();
            }
        });

        // GNSS 开关的监听，仅在关闭时删除 Marker
        gnss = getView().findViewById(R.id.gnssSwitch);
        gnss.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!isChecked) {
                    if (gnssMarker != null) {
                        gnssMarker.remove();
                        gnssMarker = null;
                    }
                    gnssError.setVisibility(View.GONE);
                }
            }
        });
        wifi = getView().findViewById(R.id.Wifiswitch);
        wifi.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!isChecked) {
                    if (wifiMarker != null) {
                        wifiMarker.remove();
                        wifiMarker = null;
                    }

                }
            }
        });

        switchColor = getView().findViewById(R.id.lineColorButton);
        switchColor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isRed) {
                    switchColor.setBackgroundColor(Color.BLACK);
                    polyline.setColor(Color.BLACK);
                    isRed = false;
                } else {
                    switchColor.setBackgroundColor(Color.RED);
                    polyline.setColor(Color.RED);
                    isRed = true;
                }
            }
        });

        blinkingRecording();

        // 如果有自动停止时间限制，则使用 CountDownTimer，否则使用 Handler 刷新任务
        if (settings.getBoolean("split_trajectory", false)) {
            long limit = settings.getInt("split_duration", 30) * 60000L;
            timeRemaining.setMax((int) (limit / 1000));
            timeRemaining.setScaleY(3f);
            autoStop = new CountDownTimer(limit, 1000) {
                @Override
                public void onTick(long l) {
                    timeRemaining.incrementProgressBy(1);
                    updateUIandPosition();
                }
                @Override
                public void onFinish() {
                    sensorFusion.stopRecording();
                    NavDirections action = RecordingFragmentDirections.actionRecordingFragmentToCorrectionFragment();
                    Navigation.findNavController(view).navigate(action);
                }
            }.start();
        } else {
            refreshDataHandler.postDelayed(refreshDataTask, 500);
        }
    }

    private void mapDropdown(){
        switchMapSpinner = (Spinner) getView().findViewById(R.id.mapSwitchSpinner);
        String[] maps = new String[]{getString(R.string.hybrid), getString(R.string.normal), getString(R.string.satellite)};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, maps);
        switchMapSpinner.setAdapter(adapter);
    }

    private void switchMap(){
        switchMapSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position){
                    case 0:
                        gMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                        break;
                    case 1:
                        gMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                        break;
                    case 2:
                        gMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                        break;
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                gMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
            }
        });
    }

    private final Runnable refreshDataTask = new Runnable() {
        @Override
        public void run() {
            updateUIandPosition();
            refreshDataHandler.postDelayed(refreshDataTask, 200);
        }
    };

    /**
     * 更新 UI 和位置，同时添加 GNSS 和 WiFi 定位点。
     */
    private void updateUIandPosition(){
        // 获取新的 PDR 数据（ENU 坐标）
        float[] pdrValues = sensorFusion.getSensorValueMap().get(SensorTypes.PDR);
        distance += Math.sqrt(Math.pow(pdrValues[0] - previousPosX, 2) + Math.pow(pdrValues[1] - previousPosY, 2));
        distanceTravelled.setText(getString(R.string.meter, String.format("%.2f", distance)));

        float[] pdrMoved = {pdrValues[0] - previousPosX, pdrValues[1] - previousPosY};
        if (pdrMoved[0] != 0 || pdrMoved[1] != 0) {
            plotLines(pdrMoved);
        }

        if (indoorMapManager == null) {
            indoorMapManager = new IndoorMapManager(gMap);
        }

        // GNSS 定位：如果开关开启，每次更新时添加一个新的 GNSS Marker保存历史点
        if (gnss.isChecked()) {
            float[] gnssData = sensorFusion.getSensorValueMap().get(SensorTypes.GNSSLATLONG);
            if (gnssData != null && gnssData.length >= 2) {
                LatLng gnssLocation = new LatLng(gnssData[0], gnssData[1]);
                gnssError.setVisibility(View.VISIBLE);
                gnssError.setText(String.format(getString(R.string.gnss_error) + "%.2fm",
                        UtilFunctions.distanceBetweenPoints(currentLocation, gnssLocation)));
                Marker newGnssMarker = gMap.addMarker(new MarkerOptions()
                        .title("GNSS 定位")
                        .position(gnssLocation)
                        .icon(BitmapDescriptorFactory.fromBitmap(
                                UtilFunctions.getBitmapFromVector(getContext(), R.drawable.green_hollow_circle)))
                        .anchor(0.5f, 0.5f));
                gnssMarkers.add(newGnssMarker);
            }
        } else {
            gnssError.setVisibility(View.GONE);
        }

//        // WiFi 定位：假设 sensorFusion.getSensorValueMap().get(SensorTypes.WIFI) 返回 WiFi 定位数据（[lat, lon]）
//        float[] wifiData = sensorFusion.getSensorValueMap().get(SensorTypes.WIFI);
//        if (wifiData != null && wifiData.length >= 2) {
//            wifiPosition = new LatLng(wifiData[0], wifiData[1]);
//            // 这里不设置错误提示，只添加 WiFi Marker
//            Marker newWifiMarker = gMap.addMarker(new MarkerOptions()
//                    .title("WiFi 定位")
//                    .position(wifiPosition)
//                    // 使用自定义图标，此处示例使用蓝色空心图（请确保资源存在）
//                    .icon(BitmapDescriptorFactory.fromBitmap(
//                            UtilFunctions.getBitmapFromVector(getContext(), R.drawable.blue_hollow_circle)))
//                    .anchor(0.5f, 0.5f));
//            wifiMarkers.add(newWifiMarker);
//        }

        indoorMapManager.setCurrentLocation(currentLocation);
        float elevationVal = sensorFusion.getElevation();
        if (indoorMapManager.getIsIndoorMapSet()){
            setFloorButtonVisibility(View.VISIBLE);
            if (autoFloor.isChecked()){
                indoorMapManager.setCurrentFloor((int)(elevationVal / indoorMapManager.getFloorHeight()), true);
            }
        } else {
            setFloorButtonVisibility(View.GONE);
        }

        previousPosX = pdrValues[0];
        previousPosY = pdrValues[1];
        elevation.setText(getString(R.string.elevation, String.format("%.1f", elevationVal)));

        if (orientationMarker != null) {
            orientationMarker.setRotation((float) Math.toDegrees(sensorFusion.passOrientation()));
        }
    }

    private void plotLines(float[] pdrMoved){
        if (currentLocation != null) {
            nextLocation = UtilFunctions.calculateNewPos(currentLocation, pdrMoved);
            try {
                List<LatLng> pointsMoved = polyline.getPoints();
                pointsMoved.add(nextLocation);
                polyline.setPoints(pointsMoved);
                orientationMarker.setPosition(nextLocation);
                gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(nextLocation, 19f));
            } catch (Exception ex) {
                Log.e("PlottingPDR", "Exception: " + ex);
            }
            currentLocation = nextLocation;
        } else {
            float[] location = sensorFusion.getGNSSLatitude(true);
            currentLocation = new LatLng(location[0], location[1]);
            nextLocation = currentLocation;
        }
    }

    private void setFloorButtonVisibility(int visibility) {
        floorUpButton.setVisibility(visibility);
        floorDownButton.setVisibility(visibility);
        autoFloor.setVisibility(visibility);
    }

    private void blinkingRecording() {
        recIcon = getView().findViewById(R.id.redDot);
        Animation blinking_rec = new AlphaAnimation(1, 0);
        blinking_rec.setDuration(800);
        blinking_rec.setInterpolator(new LinearInterpolator());
        blinking_rec.setRepeatCount(Animation.INFINITE);
        blinking_rec.setRepeatMode(Animation.REVERSE);
        recIcon.startAnimation(blinking_rec);
    }

    @Override
    public void onPause() {
        refreshDataHandler.removeCallbacks(refreshDataTask);
        super.onPause();
    }

    @Override
    public void onResume() {
        if (!settings.getBoolean("split_trajectory", false)) {
            refreshDataHandler.postDelayed(refreshDataTask, 500);
        }
        super.onResume();
    }
}