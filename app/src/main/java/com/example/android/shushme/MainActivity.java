package com.example.android.shushme;


import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import com.example.android.shushme.provider.PlaceContract.PlaceEntry;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceBuffer;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.location.places.ui.PlacePicker;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LoaderManager.LoaderCallbacks<Cursor>
{
    // Constants
    public static final String TAG = MainActivity.class.getSimpleName();
    private static final int PERMISSIONS_REQUEST_FINE_LOCATION = 111;
    private static final int PLACE_PICKER_REQUEST = 1;
    //BindViews (with id)
    @BindView(R.id.location_permissions_check_box)
    CheckBox locationPermissions;
    @BindView(R.id.add_location_button)
    Button addLocationButton;
    @BindView(R.id.resetButton)
    Button resetButton;
    @BindView(R.id.enable_switch)
    Switch onOffSwitch;
    @BindView(R.id.ringer_permissions_checkbox)
    CheckBox ringerCheckbox;
    // Member variables
    private PlaceListAdapter mAdapter;
    private RecyclerView mRecyclerView;
    // TODO (4.8) Create a new instance of Geofencing using "this" as the context and mClient as the client
    private Geofencing mGeofencing;
    // TODO (3.1) Implement a method called refreshPlacesData that:
    // in MainActivity's onCreate (you will have to declare it as a private member)
    private GoogleApiClient mClient;
    private boolean mIsEnabled;



    /**
     * Called when the activity is starting
     *
     * @param savedInstanceState The Bundle that contains the data supplied in onSaveInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        // Set up the recycler view
        mRecyclerView = (RecyclerView) findViewById(R.id.places_list_recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new PlaceListAdapter(this, null);
        mRecyclerView.setAdapter(mAdapter);

        // TODO (4.9) Create a boolean SharedPreference to store the state of the "Enable Geofences" switch
        // and initialize the switch based on the value of that SharedPreference
        mIsEnabled= getPreferences(MODE_PRIVATE).getBoolean(getString(R.string.setting_enabled),false);
        onOffSwitch.setChecked(mIsEnabled);
        // TODO (4.10) Handle the switch's change event and Register/Unregister geofences based on the value of isChecked
        // as well as set a private boolean mIsEnabled to the current switch's state
        onOffSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences.Editor editor= getPreferences(MODE_PRIVATE).edit();
                editor.putBoolean(getString(R.string.setting_enabled),isChecked);
                mIsEnabled=isChecked;
                editor.commit();
                if (isChecked) mGeofencing.registerAllGeofences();
                else mGeofencing.unRegisterAllGeofences();
            }
        });

        // TODO (1.4) Create a GoogleApiClient with the LocationServices API and GEO_DATA_API
        // TODO (3.1) Implement a method called refreshPlacesData that:
        // in MainActivity's onCreate (you will have to declare it as a private member)
        mClient= new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .addApi(Places.GEO_DATA_API)
                .enableAutoManage(this, this)
                .build();

        // TODO (4.8) Create a new instance of Geofencing using "this" as the context and mClient as the client
        mGeofencing= new Geofencing(this, mClient);

        // TODO (3.8) Implement onLocationPermissionClicked to handle the CheckBox click event
        locationPermissions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onLocationPermissionClicked();
            }
        });

        // TODO (3.9) Implement the Add Place Button click event to show  a toast message with the permission status
        addLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAddPlaceButtonClicked();
            }
        });

        // TODO (5.2) Implement onRingerPermissionsClicked to launch ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
        ringerCheckbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent= new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                startActivity(intent);
            }
        });

        //TODO (5.3) Initialize ringer permissions checkbox
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        //check if the API supports such permissions change and check if permission is granted
        if (Build.VERSION.SDK_INT>=24 && !nm.isNotificationPolicyAccessGranted()){
            ringerCheckbox.setChecked(false);
        }else {
            ringerCheckbox.setChecked(true);
            ringerCheckbox.setEnabled(false);
        }

        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Uri uri= PlaceEntry.CONTENT_URI;
                int rowsDeleted = getContentResolver().delete(uri, null, null);
                Log.v("CatalogActivity", rowsDeleted + " rows deleted from product database");
                mAdapter.swapPlaces( null);
                mRecyclerView.setAdapter(mAdapter);
                mGeofencing.unRegisterAllGeofences();

                NotificationManager nm = (NotificationManager) getApplication()
                        .getSystemService(Context.NOTIFICATION_SERVICE);
                if (Build.VERSION.SDK_INT < 24 ||
                        (Build.VERSION.SDK_INT >= 24 && !nm.isNotificationPolicyAccessGranted())) {
                    AudioManager audioManager = (AudioManager) getApplication().getSystemService(Context.AUDIO_SERVICE);
                    audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                }
            }
        });
    }

    // TODO (3.1) Implement a method called refreshPlacesData that:
    public void refreshPlacesData(){

        // - Queries all the locally stored Places IDs
        Uri uri= PlaceEntry.CONTENT_URI;
        Cursor data= getContentResolver().query(
                uri,
                null,
                null,
                null,
                null);
        if(data==null|| data.getCount()==0) return;
        List<String> guids= new ArrayList<String>();
        while (data.moveToNext()){
            guids.add(data.getString(data.getColumnIndex(PlaceEntry.COLUMN_PLACE_ID)));
        }

        // - Calls Places.GeoDataApi.getPlaceById with that list of IDs
        // Note: When calling Places.GeoDataApi.getPlaceById use the same GoogleApiClient created
        PendingResult<PlaceBuffer> placeResult= Places.GeoDataApi.getPlaceById(mClient,
                guids.toArray(new  String[guids.size()]));
        placeResult.setResultCallback(
                new ResultCallback<PlaceBuffer>() {

            //TODO (3.8) Set the getPlaceById callBack so that onResult calls the Adapter's swapPlaces with the result
            @Override
            public void onResult(@NonNull PlaceBuffer places) {
                mAdapter.swapPlaces(places);


                // TODO (4.11) Call updateGeofenceList and registerAllGeofences if mIsEnabled is true
                if(mIsEnabled){
                    mGeofencing.updateGeofencesList(places);
                    mGeofencing.registerAllGeofences();
                }
            }
        });
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {

    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {

    }
    // TODO (1.5) Override onConnected, onConnectionSuspended and onConnectionFailed for GoogleApiClient
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.i(TAG, "API Client Connection Successful!");

        //TODO (3.2) call refreshPlacesData in GoogleApiClient's onConnected and in the Add New Place button click event
        refreshPlacesData();
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "API Client Connection Successful!");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.i(TAG, "API Client Connection Successful!");
    }

    // TODO (1.7) Override onResume and inside it initialize the location permissions checkbox
    @Override
    protected void onResume() {
        super.onResume();
        ButterKnife.bind(this);

        //request permission at run time
        if (ActivityCompat.checkSelfPermission(MainActivity.this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            //permission unallowed
            locationPermissions.setChecked(false);
        } else {
            //permission allowed
            locationPermissions.setChecked(true);
            locationPermissions.setEnabled(false);
        }
    }


    // TODO (1.8) Implement onLocationPermissionClicked to handle the CheckBox click event
    public void onLocationPermissionClicked(){
    ActivityCompat.requestPermissions(MainActivity.this,
            new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
            PERMISSIONS_REQUEST_FINE_LOCATION);
    }
    // TODO (1.9) Implement the Add Place Button click event to show  a toast message with the permission status
    public void onAddPlaceButtonClicked(){
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(getApplicationContext(), getString(R.string.need_location_permission_message), Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(getApplicationContext(), getString(R.string.location_permissions_granted_message), Toast.LENGTH_LONG).show();

        // TODO (2.1) Create a PlacePicker.IntentBuilder and call startActivityForResult
        // TODO (2.2) Handle GooglePlayServices exceptions
        try {
            PlacePicker.IntentBuilder builder = new PlacePicker.IntentBuilder();
            Intent i = null;
            i = builder.build(this);
            startActivityForResult(i, PLACE_PICKER_REQUEST);
        } catch (GooglePlayServicesRepairableException e) {
            Log.e(TAG, String.format("GooglePlayServices Not Available [%s]", e.getMessage()));
        } catch (GooglePlayServicesNotAvailableException e) {
            Log.e(TAG, String.format("GooglePlayService Not Available [%s]", e.getMessage()));
        }catch (Exception e){
            Log.e(TAG, String.format("PlacePicker Exception : %s", e.getMessage()));
        }
    }

    // TODO (2.3) Implement onActivityResult and check that the requestCode is PLACE_PICKER_REQUEST
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode == PLACE_PICKER_REQUEST) && (resultCode == RESULT_OK)) {
                Place place = PlacePicker.getPlace(this, data);
                if (place==null){
                    Log.i(TAG, "No place selected");
            }

            // TODO (2.4) In onActivityResult, use PlacePicker.getPlace to extract the Place ID and insert it into the DB

            //Extract the place information from the API
            String placeName= place.getName().toString();
            String placeAddress=place.getAddress().toString();
            String placeId= place.getId();

            //Insert a new place into DB
            ContentValues contentValues=new ContentValues();
            contentValues.put(PlaceEntry.COLUMN_PLACE_ID, placeId);
            getContentResolver().insert(PlaceEntry.CONTENT_URI,contentValues);

            //TODO (3.2) call refreshPlacesData in GoogleApiClient's onConnected and in the Add New Place button click event
            //get live data information
            refreshPlacesData();
        }
    }
}
