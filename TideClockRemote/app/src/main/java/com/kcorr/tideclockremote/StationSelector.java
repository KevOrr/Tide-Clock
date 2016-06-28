package com.kcorr.tideclockremote;

import android.location.Location;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class StationSelector {

    private static final String TAG = "StationSelector";

    // All angles in degrees
    public static Station getClosestStation(JSONArray stations, Location loc) {

        float minDist = Float.MAX_VALUE;
        Station closestStation = null;

        try {
            JSONObject jsonClosestStation = stations.getJSONObject(0);

            for (int i = 0; i < stations.length(); i++) {
                // Get next station
                JSONObject jsonStation = stations.getJSONObject(i);
                float stationLat = Float.parseFloat(jsonStation.getString("lat"));
                float stationLon = Float.parseFloat(jsonStation.getString("lon"));

                // Calculate distance to this station
                float[] dist = new float[1];
                Location.distanceBetween(stationLat, stationLon, loc.getLatitude(), loc.getLongitude(), dist);

                // Store if closer than previously closest
                if (dist[0] <= minDist) {
                    minDist = dist[0];
                    jsonClosestStation = jsonStation;
                }
            }

            closestStation = new Station(jsonClosestStation);

            if (BuildConfig.DEBUG)
                Log.d(TAG, "Found closest station, id=" + closestStation.id);

        } catch (JSONException e) {
            Log.e(TAG, "JSONException raised", e);
        }

        return closestStation;
    }
}
