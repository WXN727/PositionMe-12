package com.openpositioning.PositionMe.fragments;

<<<<<<< HEAD:app/src/main/java/com/openpositioning/PositionMe/fragments/HomeFragment.java
=======
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
>>>>>>> 28e5371 (refactor: update UI components to Material Design 3 and enhance functionality across various fragments):app/src/main/java/com/openpositioning/PositionMe/presentation/fragment/HomeFragment.java
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.openpositioning.PositionMe.R;
<<<<<<< HEAD:app/src/main/java/com/openpositioning/PositionMe/fragments/HomeFragment.java
import com.google.android.material.floatingactionbutton.FloatingActionButton;
=======
import com.openpositioning.PositionMe.presentation.activity.RecordingActivity;
>>>>>>> 28e5371 (refactor: update UI components to Material Design 3 and enhance functionality across various fragments):app/src/main/java/com/openpositioning/PositionMe/presentation/fragment/HomeFragment.java

/**
 * A simple {@link Fragment} subclass. The home fragment is the start screen of the application.
 * The home fragment acts as a hub for all other fragments, with buttons and icons for navigation.
 * The default screen when opening the application
 *
 * @see RecordingFragment
 * @see FilesFragment
 * @see MeasurementsFragment
 * @see SettingsFragment
 *
 * @author Mate Stodulka
 */
public class HomeFragment extends Fragment implements OnMapReadyCallback {

    // Interactive UI elements to navigate to other fragments
    private MaterialButton goToInfo;
    private Button start;
    private Button measurements;
    private Button files;
    private TextView gnssStatusTextView;

    // For the map
    private GoogleMap mMap;
    private SupportMapFragment mapFragment;

    public HomeFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    /**
     * {@inheritDoc}
     * Ensure the action bar is shown at the top of the screen. Set the title visible to Home.
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ((AppCompatActivity) getActivity()).getSupportActionBar().show();
        View rootView = inflater.inflate(R.layout.fragment_home, container, false);
        getActivity().setTitle("Home");
        return rootView;
    }

    /**
     * Initialise UI elements and set onClick actions for the buttons.
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Sensor Info button
        goToInfo = view.findViewById(R.id.sensorInfoButton);
        goToInfo.setOnClickListener(v -> {
            NavDirections action = HomeFragmentDirections.actionHomeFragmentToInfoFragment();
            Navigation.findNavController(v).navigate(action);
        });

        // Start/Stop Recording button
        start = view.findViewById(R.id.startStopButton);
        start.setEnabled(!PreferenceManager.getDefaultSharedPreferences(getContext())
                .getBoolean("permanentDeny", false));
<<<<<<< HEAD:app/src/main/java/com/openpositioning/PositionMe/fragments/HomeFragment.java
        this.start.setOnClickListener(new View.OnClickListener() {
            /**
             * {@inheritDoc}
             * Navigate to the {@link StartLocationFragment} using AndroidX Jetpack. Hides the
             * action bar so the map appears on the full screen.
             */
            @Override
            public void onClick(View view) {
                NavDirections action = HomeFragmentDirections.actionHomeFragmentToStartLocationFragment();
                Navigation.findNavController(view).navigate(action);
                //Show action bar
                ((AppCompatActivity)getActivity()).getSupportActionBar().hide();
            }
=======
        start.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), RecordingActivity.class);
            startActivity(intent);
            ((AppCompatActivity) getActivity()).getSupportActionBar().hide();
>>>>>>> 28e5371 (refactor: update UI components to Material Design 3 and enhance functionality across various fragments):app/src/main/java/com/openpositioning/PositionMe/presentation/fragment/HomeFragment.java
        });

        // Measurements button
        measurements = view.findViewById(R.id.measurementButton);
        measurements.setOnClickListener(v -> {
            NavDirections action = HomeFragmentDirections.actionHomeFragmentToMeasurementsFragment();
            Navigation.findNavController(v).navigate(action);
        });

        // Files button
        files = view.findViewById(R.id.filesButton);
        files.setOnClickListener(v -> {
            NavDirections action = HomeFragmentDirections.actionHomeFragmentToFilesFragment();
            Navigation.findNavController(v).navigate(action);
        });

        // TextView to display GNSS disabled message
        gnssStatusTextView = view.findViewById(R.id.gnssStatusTextView);

        // Locate the MapFragment nested in this fragment
        mapFragment = (SupportMapFragment)
                getChildFragmentManager().findFragmentById(R.id.mapFragmentContainer);
        if (mapFragment != null) {
            // Asynchronously initialize the map
            mapFragment.getMapAsync(this);
        }
    }

    /**
     * Callback triggered when the Google Map is ready to be used.
     */
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // Check if GNSS/Location is enabled
        boolean gnssEnabled = isGnssEnabled();
        if (gnssEnabled) {
            // Hide the "GNSS Disabled" message
            gnssStatusTextView.setVisibility(View.GONE);

            // Check runtime permissions for location
            if (ActivityCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(
                            requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {

                // Enable the MyLocation layer of Google Map
                mMap.setMyLocationEnabled(true);

                // Optionally move the camera to last known or default location:
                //   (You could retrieve it from FusedLocationProvider or similar).
                // Here, just leaving it on default.
                // If you want to center on the user as soon as it loads, do something like:
                /*
                FusedLocationProviderClient fusedLocationClient =
                    LocationServices.getFusedLocationProviderClient(requireContext());
                fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                    if (location != null) {
                        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f));
                    }
                });
                */
            } else {
                // If no permission, simply show a default location or prompt for permissions
                showEdinburghAndMessage("Location permission not granted.");
            }
        } else {
            // If GNSS is disabled, show University of Edinburgh + message
            showEdinburghAndMessage("GNSS is disabled");
        }
    }

    /**
     * Checks if GNSS/Location is enabled on the device.
     */
    private boolean isGnssEnabled() {
        LocationManager locationManager =
                (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        // Checks both GPS and network provider. Adjust as needed.
        boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        return (gpsEnabled || networkEnabled);
    }

    /**
     * Move the map to the University of Edinburgh and display a message.
     */
    private void showEdinburghAndMessage(String message) {
        gnssStatusTextView.setText(message);
        gnssStatusTextView.setVisibility(View.VISIBLE);

        LatLng edinburghLatLng = new LatLng(55.944425, -3.188396);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(edinburghLatLng, 15f));
        mMap.addMarker(new MarkerOptions()
                .position(edinburghLatLng)
                .title("University of Edinburgh"));
    }
}
