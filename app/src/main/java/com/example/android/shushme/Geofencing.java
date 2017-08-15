package com.example.android.shushme;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.nfc.Tag;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceBuffer;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Dat T Do on 8/13/2017.
 */
// TODO (4.1) Create a Geofencing class with a
// Context and GoogleApiClient constructor that
// initializes a private member ArrayList of Geofences called mGeofenceList
// TODO (4.6) Inside Geofencing, implement a public method called registerAllGeofences that
//implements ResultCallback to enable setCallback method in the RegisterAllGeoFences
public class Geofencing implements ResultCallback {

    //private variables
    private List<Geofence> mGeofenceList;
    private PendingIntent mGeofencePendingIntent;
    private GoogleApiClient mGoogleApiClient;
    private Context mContext;

    //constants
    private static final long GEOFENCE_TIMEOUT = 24*60*60*1000;//24 hours
    private static final float GEOFENCE_RADIUS = 50; //50 meters
    private static final String TAG= Geofencing.class.getSimpleName();

    public Geofencing(Context context, GoogleApiClient client){
        mContext= context;
        mGoogleApiClient=client;
        mGeofencePendingIntent=null;
        mGeofenceList= new ArrayList<>();
    }

    // TODO (4.6) Inside Geofencing, implement a public method called registerAllGeofences that
    // registers the GeofencingRequest by calling LocationServices.GeofencingApi.addGeofences
    // using the helper functions getGeofencingRequest() and getGeofencePendingIntent()
    public void registerAllGeofences(){
        //check that API client is connected and that the lsit has Geofences in it
        if (mGoogleApiClient==null|| !mGoogleApiClient.isConnected()||
                mGeofenceList==null || mGeofenceList.size()==0){
            return;
        }
        try {
            LocationServices.GeofencingApi.addGeofences(
                    mGoogleApiClient,
                    getGeofencingRequest(),
                    getGeofencePendingIntent()
            ).setResultCallback(this);
        }catch(SecurityException securityException){
            //Catch exception generated if the app does not use ACCESS_FINE_LOCATION_permission
            Log.e(TAG, securityException.getMessage());
        }
    }

    // TODO (4.7) Inside Geofencing, implement a public method called unRegisterAllGeofences that
    // unregisters all geofences by calling LocationServices.GeofencingApi.removeGeofences
    // using the helper function getGeofencePendingIntent()
    /***
     * Unregisters all the Geofences created by this app from Google Place Services
     * Uses {@code #mGoogleApiClient} to connect to Google Place Services
     * Uses {@link #getGeofencePendingIntent} to get the pending intent passed when
     * registering the Geofences in the first place
     * Triggers {@link #onResult} when the geofences have been unregistered successfully
     */
    public void unRegisterAllGeofences(){
        if (mGoogleApiClient==null|| !mGoogleApiClient.isConnected()) {
            return;
        }
        try {
            LocationServices.GeofencingApi.removeGeofences(
                    mGoogleApiClient,
                    getGeofencePendingIntent());
        } catch (SecurityException securityException){
            //catch exception generated if the app does not use ACCESS_FINE_LOCATION permission
            Log.e(TAG, securityException.getMessage());
        }
    }

    // TODO (4.2) Inside Geofencing, implement a public method called updateGeofencesList that
    // given a PlaceBuffer will create a Geofence object for each Place using Geofence.Builder
    // and add that Geofence to mGeofenceList
    /***
     * Updates the local ArrayList of Geofences using data from the passed in list
     * Uses the Place ID defined by the API as the Geofence object Id
     *
     * @param places the PlaceBuffer result of the getPlaceById call
     */
    public void updateGeofencesList(PlaceBuffer places){
        mGeofenceList= new ArrayList<>();
        if (places==null|| places.getCount()==0) return;
        for (Place place: places){
            //Read the place information from the DB Cursor
            String placeUID= place.getId();
            double placeLat= place.getLatLng().latitude;
            double placeLng= place.getLatLng().longitude;
            //Build a Geofence object
            Geofence geofence= new Geofence.Builder()
                    .setRequestId(placeUID)
                    .setExpirationDuration(GEOFENCE_TIMEOUT)
                    .setCircularRegion(placeLat, placeLng, GEOFENCE_RADIUS)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER|Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build();
            //Add it to the list
            mGeofenceList.add(geofence);
        }
    }

    // TODO (4.3) Inside Geofencing, implement a private helper method called getGeofencingRequest that
    // uses GeofencingRequest.Builder to return a GeofencingRequest object from the Geofence list
    /***
     * Creates a GeofencingRequest object using the mGeofenceList ArrayList of Geofences
     * Used by {@code #registerGeofences}
     *
     * @return the GeofencingRequest object
     */
    private GeofencingRequest getGeofencingRequest(){
        GeofencingRequest.Builder builder= new GeofencingRequest.Builder();
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
        builder.addGeofences(mGeofenceList);
        return builder.build();
    }

    // TODO (4.5) Inside Geofencing, implement a private helper method called getGeofencePendingIntent that
    // returns a PendingIntent for the GeofenceBroadcastReceiver class
    /***
     * Creates a PendingIntent object using the GeofenceTransitionsIntentService class
     * Used by {@code #registerGeofences}
     *
     * @return the PendingIntent object
     */
    private PendingIntent getGeofencePendingIntent(){
        //Reuse the PendingIntent if we already have it
        if(mGeofencePendingIntent!= null){
            return mGeofencePendingIntent;
        }
        Intent intent= new Intent(mContext, GeofenceBroadcastReceiver.class);
        mGeofencePendingIntent= PendingIntent.getBroadcast(mContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        return mGeofencePendingIntent;
    }

    // TODO (4.6) Inside Geofencing, implement a public method called registerAllGeofences that
    //on Result method for the Callback method
    @Override
    public void onResult(@NonNull Result result){
    Log.e(TAG, String.format("Error adding/removing geofence : %s",
            result.getStatus().toString()));
    }
}