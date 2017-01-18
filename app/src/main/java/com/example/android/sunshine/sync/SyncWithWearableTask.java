package com.example.android.sunshine.sync;

import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.android.sunshine.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

/**
 * Created by zeitgeist on 13.1.2017.
 */

public class SyncWithWearableTask implements GoogleApiClient.ConnectionCallbacks,GoogleApiClient.OnConnectionFailedListener {

    private final static String TAG = SyncWithWearableTask.class.getSimpleName();
    private final static String MAX_TEMP = "max_temp";
    private final static String MIN_TEMP = "min_temp";
    private final static String WEATHER_ID ="weather_id";
    private GoogleApiClient mGoogleApiClient;
    private ContentValues[] mContentValues;
    public SyncWithWearableTask(ContentValues[] contentValues, Context context){
        mContentValues = contentValues;
        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApiIfAvailable(Wearable.API)
                .build();
        mGoogleApiClient.connect();
    }



    public void synchWithWearable(){
        double maxTemp = mContentValues[0].getAsDouble(WeatherContract.WeatherEntry.COLUMN_MAX_TEMP);
        double minTemp = mContentValues[0].getAsDouble(WeatherContract.WeatherEntry.COLUMN_MIN_TEMP);
        int weatherId = mContentValues[0].getAsInteger(WeatherContract.WeatherEntry.COLUMN_WEATHER_ID);
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/weather");
        putDataMapRequest.getDataMap().putDouble(MAX_TEMP,maxTemp);
        putDataMapRequest.getDataMap().putDouble(MIN_TEMP,minTemp);
        putDataMapRequest.getDataMap().putInt(WEATHER_ID,weatherId);
        putDataMapRequest.getDataMap().putLong("time", System.currentTimeMillis());
        PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest().setUrgent();
        Wearable.DataApi.putDataItem(mGoogleApiClient,putDataRequest)
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                        if(dataItemResult.getStatus().isSuccess()){
                            Log.d(TAG,"Succesfully send the data ");
                        }
                        else {
                            Log.d(TAG,"Failed to send the data");
                        }
                    }
                });
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG,"App side connected");
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }


}
