package com.kcorr.tideclockremote;

import org.json.JSONException;
import org.json.JSONObject;

/** Data class that abstracts a NOAA COOPS station */
public class Station {

    public final String name;
    public final int id;
    public final float latitude;
    public final float longitude;


    public Station(JSONObject jsonStation) throws JSONException {
        name = jsonStation.getString("name");
        id = jsonStation.getInt("id");
        latitude = Float.parseFloat(jsonStation.getString("lat"));
        longitude = Float.parseFloat(jsonStation.getString("lon"));
    }
}
