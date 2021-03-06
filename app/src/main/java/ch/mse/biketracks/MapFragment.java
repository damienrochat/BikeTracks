package ch.mse.biketracks;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ch.mse.biketracks.database.DatabaseHelper;
import ch.mse.biketracks.models.Point;
import ch.mse.biketracks.models.Track;
import ch.mse.biketracks.services.TrackerService;
import ch.mse.biketracks.utils.BiketracksAPIClient;
import ch.mse.biketracks.utils.BiketracksAPIInterface;
import ch.mse.biketracks.utils.ContrastColor;
import ch.mse.biketracks.utils.MyTools;
import ch.mse.biketracks.utils.Tuple;
import retrofit2.Call;
import retrofit2.Callback;

public class MapFragment extends Fragment implements OnMapReadyCallback {

    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 2;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION_RECORD = 22;

    private static final float DEFAULT_ZOOM = 8.f;
    private static final String TAG = MapFragment.class.getSimpleName();

    private final LatLng mDefaultLocation = new LatLng(46.523317, 6.610430); // HES-SO Master, Provence, Lausanne

    private GoogleMap mMap;
    private SparseArray<Polyline> polylineSparseArray = new SparseArray<>();
    private SparseArray<Polyline> myTracksPolylineSparseArray = new SparseArray<>();
    private BiketracksAPIInterface apiInterface;

    private Context mContext;
    private SupportMapFragment supportMapFragment;

    // Tracks
    private boolean mLocationPermissionGranted;
    private boolean isTrackSelected = false;
    private Polyline focusedPolyline;
    private Track selectedTrack;
    private Location mLastKnownLocation;
    private FusedLocationProviderClient mFusedLocationProviderClient;
    private ProgressBar progressBar;
    private SparseIntArray tracksColor = new SparseIntArray(); // Define a color for each track to distinguish them <id of track, color of track>
    private SparseArray<Track> fullTracksSparseArray = new SparseArray<>();

    private Marker startMarker;
    private Marker startRecordingMarker;
    private Marker finishMarker;
    private Bitmap startIconSmall;
    private Bitmap finishIconSmall;

    // Record activity
    private Button startRecordingButton;
    private Button stopRecordingButton;
    private View recordingWindow;
    private BottomSheetBehavior recordingWindowBehavior;
    private TextView recordingDistance;
    private TextView recordingDuration;
    private TextView recordingSpeed;
    private boolean isRecording = false;
    private boolean isListening = false;
    private BroadcastReceiver trackingUpdatesReceiver;
    private Track recordedTrack;

    // Bottom sheet controls
    private View trackWindow;
    private BottomSheetBehavior trackWindowBehavior;
    private TextView trackTitle;
    private TextView trackType;
    private TextView trackDistance;
    private TextView trackClimb;
    private TextView trackDescent;

    ArrayList<Track> myTracks = new ArrayList<>();

    public MapFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mContext = getActivity();

        setHasOptionsMenu(true);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        FragmentManager fm = getFragmentManager(); /// getChildFragmentManager();
        supportMapFragment = SupportMapFragment.newInstance();
        fm.beginTransaction().replace(R.id.map, supportMapFragment).commit();
        supportMapFragment.getMapAsync(this);

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            isRecording = savedInstanceState.getBoolean("isRecording");
        }


    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mContext = getActivity();
        apiInterface = BiketracksAPIClient.getClient().create(BiketracksAPIInterface.class); // API to retrieve tracks
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(mContext);

        startRecordingButton = getView().findViewById(R.id.start_recording);
        startRecordingButton.setOnClickListener(view -> {
            if (!isRecording) {
                if (MyTools.isLocationEnabled(mContext)) {
                    isRecording = true;
                    displayRecordingPanel();
                    startRecordingWrapper();
                }
                else {
                    Toast.makeText(mContext, R.string.enable_location_first,
                            Toast.LENGTH_LONG).show();
                }
            }
        });

        stopRecordingButton = getView().findViewById(R.id.stop_recording);
        stopRecordingButton.setOnClickListener(view -> {
            if (isRecording) {
                isRecording = false;
                stopRecording();
                hideRecordingPanel();
            }
        });

        trackingUpdatesReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                recordedTrack = (Track) intent.getSerializableExtra("track");
                updateRecording();
            }
        };

        progressBar = getView().findViewById(R.id.progressBarMap);

        // Record controls
        startRecordingButton = getView().findViewById(R.id.start_recording);
        stopRecordingButton = getView().findViewById(R.id.stop_recording);

        // Get track bottom sheet controls
        trackWindow = getView().findViewById(R.id.track_window);
        trackTitle = getView().findViewById(R.id.track_title);
        trackType = getView().findViewById(R.id.track_type);
        trackDistance = getView().findViewById(R.id.track_distance);
        trackClimb = getView().findViewById(R.id.track_climb);
        trackDescent = getView().findViewById(R.id.track_descent);

        trackWindowBehavior = BottomSheetBehavior.from(trackWindow);
        trackWindowBehavior.setHideable(true);
        trackWindowBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        trackWindowBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    if (!isRecording)
                        unselectTrack();
                    else {
                        if (selectedTrack.getPolyline() != null)
                            selectedTrack.getPolyline().setClickable(false);
                        if (focusedPolyline != null)
                            focusedPolyline.remove();
                    }
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                if (slideOffset >= 0.f)
                    updateRecordingControl(startRecordingButton,
                            trackWindowBehavior.getPeekHeight() + (int)(slideOffset * (bottomSheet.getHeight() - trackWindowBehavior.getPeekHeight())));
                else
                    updateRecordingControl(startRecordingButton,
                            (int)( (1. + slideOffset) * trackWindowBehavior.getPeekHeight()));
            }
        });

        // Get recording bottom sheet controls
        recordingWindow = getView().findViewById(R.id.recording_window);
        recordingDistance = getView().findViewById(R.id.recording_distance);
        recordingDuration = getView().findViewById(R.id.recording_duration);
        recordingSpeed = getView().findViewById(R.id.recording_speed);

        recordingWindowBehavior = BottomSheetBehavior.from(recordingWindow);
        recordingWindowBehavior.setHideable(true);
        recordingWindowBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        recordingWindowBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                Log.d(TAG, "new state: " + newState + ", bottomSheet Height: " + bottomSheet.getHeight());
                if (newState == BottomSheetBehavior.STATE_EXPANDED)
                    updateRecordingControl(stopRecordingButton, bottomSheet.getHeight());
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
            }
        });

        // Set marker start and finish
        int height = 52;
        int width = 52;

        BitmapDrawable bitmapdraw=(BitmapDrawable)getResources().getDrawable(R.drawable.icon_start);
        Bitmap bStart = bitmapdraw.getBitmap();
        startIconSmall = Bitmap.createScaledBitmap(bStart, width, height, false);

        BitmapDrawable bitmapdraw2=(BitmapDrawable)getResources().getDrawable(R.drawable.icon_finish);
        Bitmap bFinish = bitmapdraw2.getBitmap();
        finishIconSmall = Bitmap.createScaledBitmap(bFinish, width, height, false);

        // Handle checkbox to show/hide user's tracks
        CheckBox cb = getView().findViewById(R.id.showmytracks);
        SharedPreferences sharedPref = mContext.getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        cb.setChecked(sharedPref.getBoolean(getString(R.string.show_my_tracks_preference), false));
        cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putBoolean(getString(R.string.show_my_tracks_preference), isChecked);
                editor.commit();
                showMyTracks();
                Log.d(TAG,"The checkbox has changed");
            }
        });

        if (isRecording) {
            displayRecordingPanel();
            progressBar.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (isRecording && !isListening) {
            startListeningTracking();
        }
    }

    @Override
    public void onPause() {
        stopListeningTracking();
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean("isRecording", isRecording);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        Log.v(TAG, googleMap.toString());
        mMap = googleMap;

        // Move camera to default location
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mDefaultLocation, DEFAULT_ZOOM));

        // Listen for camera movements, i.e. when the map moves or is zoomed in/out
        mMap.setOnCameraIdleListener(this::onCameraIdle);
        mMap.setOnMapClickListener(this::onMapClicked);
        mMap.setOnPolylineClickListener(this::onPolylineClicked);

        // Get the current location of the device and set the position of the map.
        // Turn on the My Location layer and the related control on the map.
        updateLocationUI();

        if (MyTools.isLocationEnabled(mContext))
            // Get the current location of the device and set the position of the map.
            getDeviceLocation(8.f);
        else {
            Toast.makeText(mContext, R.string.enable_location_first,
                    Toast.LENGTH_LONG).show();
        }

        showMyTracks();
    }

    private void onPolylineClicked(Polyline clickedPolyline) {
        isTrackSelected = true;
        selectedTrack = (Track)clickedPolyline.getTag();
        progressBar.setVisibility(View.VISIBLE);
        if (selectedTrack.isLocal())
            displayTrackDetails(selectedTrack);
        else
            fetchTrack(selectedTrack.getId());
        // Update recording button text
        startRecordingButton.setText(R.string.follow_this_track);
    }

    /**
     * Defocus the selected track if any
     * @param point the point clicked by the user
     */
    private void onMapClicked(LatLng point) {
        if (!isRecording) {
            if (isTrackSelected) {
                isTrackSelected = false;
                selectedTrack = null;
                unselectTrack();
                hideRecordingPanel();
            }
            startRecordingButton.setText(R.string.button_start_recording);
        }
    }

    /**
     * Called each time the camera is moved, i.e. each time the map changes.
     * We get the radius of visible window, then load and replace the tracks on the map.
     */
    private void onCameraIdle() {
        if (isRecording) {
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        Log.d(TAG, "The camera has stopped moving. Get radius and load tracks of the visible region");
        // Get Radius of visible window
        LatLngBounds bounds = mMap.getProjection().getVisibleRegion().latLngBounds;

        LatLng center = bounds.getCenter();
        LatLng NE = bounds.northeast;
        LatLng SW = bounds.southwest;

        // Compute distance and get radius
        float[] distance = new float[2];
        Location.distanceBetween(SW.latitude, SW.longitude, NE.latitude, NE.longitude, distance);

        // Distance is in distance[0] and it is in meter
        int radius = (int)Math.ceil(distance[0] / 2.0);

        // Load and replace previous tracks
        getTracks(center.latitude, center.longitude, radius);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.search, menu);
        try {
            final Menu m = menu;
            // Associate searchable configuration with the SearchView
            final SearchManager searchManager =
                    (SearchManager) getActivity().getSystemService(Context.SEARCH_SERVICE);
            final SearchView searchView =
                    (SearchView) menu.findItem(R.id.action_search).getActionView();
            searchView.setSearchableInfo(
                    searchManager.getSearchableInfo(getActivity().getComponentName()));
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String s) {
                    // Search for location on map
                    if(search(s)){
                        // Close search
                        (m.findItem(R.id.action_search)).collapseActionView();
                    } else{
                        Toast.makeText(mContext, R.string.location_not_found, Toast.LENGTH_SHORT).show();
                    }

                    return false;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    return false;
                }
            });


        }catch(Exception e){e.printStackTrace();}
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_search) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public boolean search(String query) {
        if (query != null || !query.equals("")) {
            Geocoder geocoder = new Geocoder(getActivity());
            List<Address> addressList;
            try {
                addressList = geocoder.getFromLocationName(query, 1);
                if(!addressList.isEmpty()){
                    Address address = addressList.get(0);
                    LatLng latLng = new LatLng(address.getLatitude(), address.getLongitude());
                    mMap.animateCamera(CameraUpdateFactory.newLatLng(latLng));
                    return true;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        mLocationPermissionGranted = false;
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true;
                }
            }
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION_RECORD: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true;
                    startRecording();
                }
            }
        }
    }

    /*
     * Request location permission, so that we can get the location of the
     * device. The result of the permission request is handled by a callback,
     * onRequestPermissionsResult WHICH IS IMPLEMENTED in MainActivity
     */
    private void getLocationPermission() {
        if (ContextCompat.checkSelfPermission(mContext,
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
            updateLocationUI();
        } else {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    private void updateLocationUI() {
        if (mMap == null) {
            return;
        }
        try {
            if (mLocationPermissionGranted) {
                mMap.setMyLocationEnabled(true);
                //locateButton.setVisibility(View.VISIBLE);
            } else {
                mMap.setMyLocationEnabled(false);
                //locateButton.setVisibility(View.GONE);
                mLastKnownLocation = null;
                getLocationPermission();
            }
        } catch (SecurityException e)  {
            Log.e("Exception: %s", e.getMessage());
        }
    }

    /**
     * Check weather location is enabled or not. If not, show alert.
     */
    public void checkLocation() {
        final LocationManager manager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);

        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            buildAlertMessageNoGps();
        }
    }

    /**
     * Display an alert to enable location
     */
    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setMessage(R.string.gps_seems_disabled)
                .setCancelable(false)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        dialog.cancel();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    /*
     * Get the best and most recent location of the device, which may be null in rare
     * cases when a location is not available.
     */
    private void getDeviceLocation(float zoom) {
        try {
            if (mLocationPermissionGranted) {
                Task locationResult = mFusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(getActivity(), new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful()) {
                            // Set the map's camera position to the current location of the device.
                            mLastKnownLocation = task.getResult();
                            if (mLastKnownLocation != null) {
                                Log.d(TAG, "mLastKnownLocation : " + mLastKnownLocation.toString());
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                    new LatLng(mLastKnownLocation.getLatitude(),
                                            mLastKnownLocation.getLongitude()), zoom));
                            } else {
                                checkLocation();
                            }

                        } else {
                            Log.d(TAG, "Current location is null. Using defaults.");
                            Log.e(TAG, "Exception: %s", task.getException());
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mDefaultLocation, DEFAULT_ZOOM));
                            Toast.makeText(mContext, R.string.location_not_found, Toast.LENGTH_SHORT).show();
                            //mMap.getUiSettings().setMyLocationButtonEnabled(false);
                            //locateButton.hide();
                        }
                    }
                });
            }
        } catch(SecurityException e)  {
            Log.e("Exception: %s", e.getMessage());
        }
    }

    /**
     * Retrieve the tracks from biketracks API and display them on the map. Every previous tracks
     * loaded are cleaned from the map.
     * @param lat Latitude of center point
     * @param lng Longitude of center point
     * @param radius Radius in meter defining the circle to query the tracks.
     */
    private void getTracks(double lat, double lng, int radius) {

        Call<List<Track>> call = apiInterface.doGetTracks(lat, lng, radius);
        call.enqueue(new Callback<List<Track>>() {

            /**
             * Tracks were successfully retrieved
             * @param call
             * @param response
             */
            @Override
            public void onResponse(Call<List<Track>> call, retrofit2.Response<List<Track>> response) {
                List<Track> tracks = response.body();

                if (tracks != null) {
                    cleanPolylines();

                    // Display tracks
                    for (Track track : tracks) {
                        if (isTrackSelected && selectedTrack.getId() == track.getId())
                            continue;
                        
                        PolylineOptions polylineOptions = new PolylineOptions();

                        // Set a unique color for each track
                        if (tracksColor.get(track.getId()) == 0)
                            tracksColor.append(track.getId(), ContrastColor.randomColor()); //0xFF000000 | rnd.nextInt(0xFFFFFF));

                        // Build the track
                        LatLngBounds.Builder builder = new LatLngBounds.Builder();
                        for (Point point : track.getPoints()) {
                            LatLng latLng = new LatLng(point.getLat(), point.getLng());
                            polylineOptions.add(latLng);
                            polylineOptions.width(10);
                            polylineOptions.color(tracksColor.get(track.getId()));
                            builder.include(latLng);
                        }
                        Polyline polyline = mMap.addPolyline(polylineOptions);
                        polyline.setClickable(true);
                        polyline.setTag(track);
                        LatLngBounds bounds = builder.build();
                        track.setLatLngBounds(bounds);
                        track.setPolyline(polyline);
                        polylineSparseArray.put(track.getId(), polyline); // Store the displayed polylines to clean them before each load
                    }

                } else {
                    call.cancel();
                }

                progressBar.setVisibility(View.GONE);
            }

            /**
             * Failing in retrieving the tracks from API
             * @param call
             * @param t
             */
            @Override
            public void onFailure(Call<List<Track>> call, Throwable t) {
                Log.d(TAG, "error in calling tracks: " + t.getMessage());
                Toast.makeText(mContext, String.format("%s : %s", getString(R.string.error), t.getMessage()), Toast.LENGTH_SHORT).show();
                progressBar.setVisibility(View.GONE);
                call.cancel();
            }
        });
    }

    /**
     * Remove all polylines and markers from the map.
     */
    private void cleanMap() {
        cleanPolylines();
        unselectTrack();
    }

    /**
     * Clean all polylines except the selected one if any
     */
    private void cleanPolylines() {
        int polylineSparseArraySize = polylineSparseArray.size();
        for (int i = 0; i < polylineSparseArraySize; i++) {
            int key = polylineSparseArray.keyAt(i);
            if (isTrackSelected && selectedTrack.getId() == key)
                continue;
            polylineSparseArray.get(key).remove();
        }
    }

    private void cleanMyPolylines() {
        int myTrackspolylineSparseArraySize = myTracksPolylineSparseArray.size();
        for (int i = 0; i < myTrackspolylineSparseArraySize; i++) {
            int key = myTracksPolylineSparseArray.keyAt(i);
            if (isTrackSelected && selectedTrack.getId() == key)
                continue;
            myTracksPolylineSparseArray.get(key).remove();
        }
    }

    private void unselectTrack() {
        isTrackSelected = false;
        startRecordingButton.setText(R.string.button_start_recording);

        if (startMarker != null)
            startMarker.remove();
        if (finishMarker != null)
            finishMarker.remove();

        if (focusedPolyline != null)
            focusedPolyline.remove();
    }

    /**
     * Download or use the already downloaded track for displaying its details
     * @param trackId the id of the track
     */
    private void fetchTrack(int trackId) {

        Track track = fullTracksSparseArray.get(trackId);
        if (track != null)
            displayTrackDetails(track);
        else
            downloadAndDisplayTrackDetails(trackId);
    }

    /**
     * Display the selected track details in the bottom sheet appearing from the bottom screen
     * @param track The track to display
     */
    private void displayTrackDetails(Track track) {

        // Fill the bottom sheet with track's content
        trackTitle.setText(track.getName());
        trackType.setText(track.getType());
        trackDistance.setText(String.format(Locale.getDefault(), "%.1f km", track.getDistance() / 1000.f));
        trackClimb.setText(String.format(Locale.getDefault(), "%d m", (int)track.getClimb()));
        trackDescent.setText(String.format(Locale.getDefault(), "%d m", (int)track.getDescent()));

        // Build the elevation graph
        Tuple<LineGraphSeries<DataPoint>, Double> elevationGraph = MyTools.ElevationGraph(track.getPoints());
        GraphView graphView = getView().findViewById(R.id.elevationGraph);

        // set manual X bounds
        graphView.getViewport().setXAxisBoundsManual(true);
        graphView.getViewport().setMinX(0.);
        graphView.getViewport().setMaxX(elevationGraph.second);
        if (graphView.getSeries().size() > 0)
            graphView.removeAllSeries();
        graphView.addSeries(elevationGraph.first);

        // Display the bottom sheet
        trackWindowBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);

        // Redraw track as now it contains all the points
        int color = ContextCompat.getColor(mContext, R.color.red);
        if (!track.isLocal()) {
            polylineSparseArray.get(track.getId()).remove();
            polylineSparseArray.remove(track.getId());
            color = tracksColor.get(track.getId());
        } else {
            myTracksPolylineSparseArray.get(track.getId()).remove();
            myTracksPolylineSparseArray.remove(track.getId());
        }

        PolylineOptions polylineOptions = new PolylineOptions(); // Red polyline to focus the detailed track
        PolylineOptions polylineOptionsDetailed = new PolylineOptions(); // The detailed track
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (Point point : track.getPoints()) {
            LatLng latLng = new LatLng(point.getLat(), point.getLng());

            polylineOptions.add(latLng);
            polylineOptions.width(20);
            polylineOptions.color(Color.rgb(255,0,0));

            polylineOptionsDetailed.add(latLng);
            polylineOptionsDetailed.width(10);
            polylineOptionsDetailed.color(color);

            builder.include(latLng);
        }
        if (focusedPolyline != null)
            focusedPolyline.remove();
        focusedPolyline = mMap.addPolyline(polylineOptions);
        Polyline polylineDetailed = mMap.addPolyline(polylineOptionsDetailed);
        polylineDetailed.setZIndex(2.f);
        focusedPolyline.setZIndex(1.f);
        polylineDetailed.setClickable(true);
        polylineDetailed.setTag(track);
        selectedTrack.setPolyline(polylineDetailed);
        LatLngBounds bounds = builder.build();
        track.setLatLngBounds(bounds);

        // Focus track by making others below
        int polylineSparseArraySize = polylineSparseArray.size();
        for(int i = 0; i < polylineSparseArraySize; i++) {
            int key = polylineSparseArray.keyAt(i);
            Polyline polyline = polylineSparseArray.get(key);
            polyline.setZIndex(0);
        }

        if (track.isLocal())
            myTracksPolylineSparseArray.put(track.getId(), polylineDetailed);
        else
            polylineSparseArray.put(track.getId(), polylineDetailed);

        // Add markers start/end
        if (startMarker != null)
            startMarker.remove();
        if (finishMarker != null)
            finishMarker.remove();

        Point startPoint = track.getPoints().get(0);
        Point finishPoint = track.getPoints().get(track.getPoints().size() - 1);
        LatLng start = new LatLng(startPoint.getLat(), startPoint.getLng());
        LatLng finish = new LatLng(finishPoint.getLat(), finishPoint.getLng());

        MarkerOptions startMarkerOptions = new MarkerOptions()
                .position(start)
                .anchor(0.5f, 0.5f)
                .icon(BitmapDescriptorFactory.fromBitmap(startIconSmall));
        MarkerOptions finishMarkerOptions = new MarkerOptions()
                .position(finish)
                .anchor(0.5f, 0.5f)
                .icon(BitmapDescriptorFactory.fromBitmap(finishIconSmall));

        finishMarker = mMap.addMarker(finishMarkerOptions);
        startMarker = mMap.addMarker(startMarkerOptions);

        // Center map
        mMap.setPadding(0,0,0, trackWindow.getHeight() + startRecordingButton.getHeight());
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(track.getLatLngBounds(), 200));

        progressBar.setVisibility(View.GONE);
    }

    /*
     * Retrieve track from
     * @param trackId
     * @return
     */
    private void downloadAndDisplayTrackDetails(int trackId) {
        Call<Track> call = apiInterface.doGetTrack(trackId);
        call.enqueue(new Callback<Track>() {

            /**
             * Track was successfully retrieved
             * @param call
             * @param response
             */
            @Override
            public void onResponse(Call<Track> call, retrofit2.Response<Track> response) {
                Track track = response.body();

                if (track != null) {
                    fullTracksSparseArray.put(trackId, track);
                    displayTrackDetails(track);
                } else {
                    Toast.makeText(mContext, String.format("%s : %s", getString(R.string.error), getString(R.string.selected_track_empty)), Toast.LENGTH_SHORT).show();
                }
            }

            /**
             * Failing in retrieving the track from API
             * @param call
             * @param t
             */
            @Override
            public void onFailure(Call<Track> call, Throwable t) {
                Toast.makeText(mContext, String.format("%s : %s", getString(R.string.error), t.getMessage()), Toast.LENGTH_SHORT).show();
                progressBar.setVisibility(View.GONE);
                call.cancel();
            }
        });
    }

    /**
     * Used to replace the start record button
     * @param btn the button to update
     * @param height the margin below the button
     */
    private void updateRecordingControl(Button btn, int height) {
        CoordinatorLayout.LayoutParams params = new CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.WRAP_CONTENT,
                CoordinatorLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, height + 32);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER;
        btn.setLayoutParams(params);
        if (mMap != null)
            mMap.setPadding(0, 0, 0, height + startRecordingButton.getHeight());
    }

    /**
     * Ask for permission
     */
    private void startRecordingWrapper() {
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                showMessageOKCancel(getString(R.string.allow_access_to_location_for_activity),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION},
                                        PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION_RECORD);
                            }
                        });
                return;
            }
            requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION_RECORD);
            return;
        }
        startRecording();
    }

    private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener) {
        new android.support.v7.app.AlertDialog.Builder(mContext)
                .setMessage(message)
                .setPositiveButton(R.string.ok, okListener)
                .setNegativeButton(R.string.cancel, null)
                .create()
                .show();
    }

    /**
     * Create recording service and update current activity details when needed
     */
    private void startRecording() {
        Log.d(TAG, "startRecording");
        getActivity().startService(new Intent(getActivity(), TrackerService.class));
        startListeningTracking();

        // Create a notification for the recording
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            String CHANNEL_ID = "my_channel_01";
            NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(mContext, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_record_notification)
                        .setContentTitle(getString(R.string.app_name))
                        .setContentText(getString(R.string.recording_track))
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);//to show content in lock screen
            ;
            // Creates an explicit intent for an Activity in your app
            Intent resultIntent = new Intent(mContext, MainActivity.class);

            // The stack builder object will contain an artificial back stack for the
            // started Activity.
            // This ensures that navigating backward from the Activity leads out of
            // your app to the Home screen.
            TaskStackBuilder stackBuilder = TaskStackBuilder.create(mContext);
            // Adds the back stack for the Intent (but not the Intent itself)
            stackBuilder.addParentStack(MainActivity.class);
            // Adds the Intent that starts the Activity to the top of the stack
            stackBuilder.addNextIntent(resultIntent);
            PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
            mBuilder.setContentIntent(resultPendingIntent);
            NotificationManager mNotificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

            // mNotificationId is a unique integer your app uses to identify the
            // notification. For example, to cancel the notification, you can pass its ID
            // number to NotificationManager.cancel().
            mNotificationManager.notify(0, mBuilder.build());


            Log.d(TAG, "Notification created");
        } else{
            Log.e(TAG, "Notifications not supported");
        }

        // Go to the user's location
        // Turn on the My Location layer and the related control on the map.
        updateLocationUI();

        if (MyTools.isLocationEnabled(mContext))
            // Get the current location of the device and set the position of the map.
            getDeviceLocation(15.f);
        else {
            Toast.makeText(mContext, R.string.enable_location_first,
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Stop recording
     */
    private void stopRecording() {
        Log.d(TAG, "stopRecording");

        stopListeningTracking();
        getActivity().stopService(new Intent(getActivity(), TrackerService.class));

        LayoutInflater inflater = getActivity().getLayoutInflater();
        final View view = inflater.inflate(R.layout.dialog_save_track, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setView(view)
                .setPositiveButton(R.string.yes, (dialog, id) -> {
                    final EditText name = view.findViewById(R.id.track_name);
                    recordedTrack.setName(name.getText().toString());

                    int trackId = saveRecordedTrack();
                    hideRecordingPanel();
                    isRecording = false;

                    Intent intent = new Intent();
                    intent.setClass(getActivity(), TrackActivity.class);
                    intent.putExtra("trackId", trackId);
                    startActivity(intent);
                })
                .setNegativeButton(R.string.no, (dialog, id) -> {
                    hideRecordingPanel();
                    isRecording = false;
                })
                .show();

        // Hide the notification for the recording
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            NotificationManager mNotificationManager =
                    (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            // The id of the channel.
            mNotificationManager.cancel(0);
            ;Log.d(TAG, "Notification removed");
        } else{
            Log.e(TAG, "Notifications not supported");
        }

        // Remove marker
        if (startRecordingMarker != null)
            startRecordingMarker.remove();

        if (selectedTrack != null && selectedTrack.getPolyline() != null)
            selectedTrack.getPolyline().setClickable(true);
    }

    /**
     * Register tracking listeners
     */
    private void startListeningTracking() {
        if (!isListening) {
            LocalBroadcastManager.getInstance(mContext).registerReceiver(trackingUpdatesReceiver,
                    new IntentFilter(TrackerService.ACTION_UPDATE));
            isListening = true;
        }
    }

    /**
     * Unregister tracking listeners
     */
    private void stopListeningTracking() {
        if (isListening) {
            LocalBroadcastManager.getInstance(mContext).unregisterReceiver(trackingUpdatesReceiver);
            isListening = false;
        }
    }

    /**
     * Display the tracking window
     */
    private void displayRecordingPanel() {
        cleanPolylines();

        startRecordingButton.setVisibility(View.INVISIBLE);
        stopRecordingButton.setVisibility(View.VISIBLE);

        trackWindowBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        recordingWindowBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        updateRecordingControl(stopRecordingButton, recordingWindow.getHeight());
    }

    /**
     * Hide the tracking window
     */
    private void hideRecordingPanel() {
        stopRecordingButton.setVisibility(View.INVISIBLE);
        startRecordingButton.setVisibility(View.VISIBLE);

        recordingWindowBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        trackWindowBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        updateRecordingControl(startRecordingButton, 0);
    }

    /**
     * Update the current tracking
     */
    private void updateRecording() {
        recordingDistance.setText(String.format(Locale.getDefault(), "%.1f km", recordedTrack.getDistance() / 1000.f));
        Log.v(TAG, recordedTrack.getDuration() + " => " + MyTools.FormatTimeHHhmmss(recordedTrack.getDuration()));
        recordingDuration.setText(MyTools.FormatTimeHHhmmss(recordedTrack.getDuration()));
        recordingSpeed.setText(String.format(Locale.ENGLISH,"%.1f km/h",recordedTrack.getSpeed() * 3.6));

        cleanPolylines();
        cleanMyPolylines();

        PolylineOptions polylineOptions = new PolylineOptions();
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (Point point : recordedTrack.getPoints()) {
            LatLng latLng = new LatLng(point.getLat(), point.getLng());
            polylineOptions.add(latLng);
            polylineOptions.width(20);
            polylineOptions.color(ContextCompat.getColor(mContext, R.color.red));
            builder.include(latLng);
        }
        Polyline polyline = mMap.addPolyline(polylineOptions);
        polyline.setClickable(false);
        polylineSparseArray.put(0, polyline);

        Point startPoint = recordedTrack.getPoints().get(0);
        LatLng start = new LatLng(startPoint.getLat(), startPoint.getLng());

        MarkerOptions startMarkerOptions = new MarkerOptions()
                .position(start)
                .anchor(0.5f, 0.5f)
                .icon(BitmapDescriptorFactory.fromBitmap(startIconSmall));

        if (startRecordingMarker != null)
            startRecordingMarker.remove();
        startRecordingMarker = mMap.addMarker(startMarkerOptions);
    }

    /**
     * Save the recorded track in database
     */
    private int saveRecordedTrack() {
        return (int)DatabaseHelper.getInstance(mContext).insertTrack(recordedTrack);
    }

    /**
     * Get tracks from DB asynchronously and show them
     */
    private class FetchTracksTask extends AsyncTask<Void, Void, ArrayList<Track>> {
        @Override
        protected ArrayList<Track> doInBackground(Void... voids) {
            return DatabaseHelper.getInstance(mContext).getTracks(true);
        }

        protected void onPostExecute(ArrayList<Track> result) {
            if (result.isEmpty()) {
                Toast.makeText(mContext, R.string.no_registered_tracks, Toast.LENGTH_SHORT).show();
                return;
            }
            myTracks.addAll(result);

            for (Track track : myTracks) {
                track.setLocal(true);
                if (isTrackSelected && selectedTrack.getId() == track.getId())
                    continue;

                PolylineOptions polylineOptions = new PolylineOptions();

                // Build the track
                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                for (Point point : track.getPoints()) {
                    LatLng latLng = new LatLng(point.getLat(), point.getLng());
                    polylineOptions.add(latLng);
                    polylineOptions.width(10);
                    polylineOptions.color(Color.rgb(237, 92, 92));
                    builder.include(latLng);
                }
                Polyline polyline = mMap.addPolyline(polylineOptions);
                polyline.setClickable(true);
                polyline.setTag(track);
                LatLngBounds bounds = builder.build();
                track.setLatLngBounds(bounds);
                track.setPolyline(polyline);
                myTracksPolylineSparseArray.put(track.getId(), polyline); // Store the displayed polylines to clean them before each load
            }
        }
    }

    /**
     * Show my tracks
     */
    private void showMyTracks(){
        cleanMyTracksPolylines();
        myTracks.clear();
        SharedPreferences sharedPref = mContext.getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        if(sharedPref.getBoolean("show_my_tracks", false)){
            new MapFragment.FetchTracksTask().execute();
        }
    }

    private void cleanMyTracksPolylines() {
        int polylineSparseArraySize = myTracksPolylineSparseArray.size();
        for (int i = 0; i < polylineSparseArraySize; i++) {
            int key = myTracksPolylineSparseArray.keyAt(i);
            if (isTrackSelected && selectedTrack.getId() == key)
                continue;
            myTracksPolylineSparseArray.get(key).remove();
        }
    }
}
