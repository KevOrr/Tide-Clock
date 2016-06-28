package com.kcorr.tideclockremote;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.InputStream;
import java.net.Socket;
import java.util.Date;
import java.util.Scanner;

public class MainActivity extends AppCompatActivity {

    private final String TAG = "MainActivity";
    private final int PERMISSION_COARSE_LOCATION = 1;
    private final String SSID = "Tide Clock Setup";
    private final String PSK = "45c94753";
    private final int CONNECT_TIMEOUT = 5000;

    private LocationManager locationManager;
    private WifiManager wifiManager;
    private boolean connected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        this.locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        this.wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
        this.connected = false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        switch (item.getItemId()) {
            case R.id.action_refresh:
                connect(SSID, PSK);
                update();
                return true;
            case R.id.action_settings:
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] grantResults) {
        switch (request) {
            case PERMISSION_COARSE_LOCATION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    update();
        }
    }

    private void connect(String ssid, String psk) {
        wifiManager.disconnect();
        boolean foundNetwork = false;

        // Enable soft AP if already in network config
        for (WifiConfiguration storedConf : this.wifiManager.getConfiguredNetworks()) {
            if (storedConf != null && storedConf.SSID.equals("\"" + ssid + "\"")) {
                wifiManager.enableNetwork(storedConf.networkId, true);
                foundNetwork = true;
                if (BuildConfig.DEBUG)
                    Log.d(TAG, "Found network in wifi configuration, id=" + storedConf.networkId);
                break;
            }
        }

        // Else, add it and enable
        if (!foundNetwork) {
            WifiConfiguration conf = new WifiConfiguration();
            conf.SSID = "\"" + ssid + "\"";
            conf.preSharedKey = "\"" + psk + "\"";
            /*conf.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            conf.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            conf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            conf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
            conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
            conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);*/

            int networkId = wifiManager.addNetwork(conf);
            wifiManager.enableNetwork(networkId, true);
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Added network to wifi configuration, id=" + networkId);
            }
        }

        wifiManager.reconnect();

    }

    private void update() {

        // If we don't have ACCESS_COARSE_LOCATION permission, ask for it and then return here
        int perm = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
        if (perm != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    PERMISSION_COARSE_LOCATION);
            return;
        }

        Location loc = this.locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        InputStream is = this.getResources().openRawResource(R.raw.stations);
        Scanner s = new Scanner(is).useDelimiter("\\A");
        Station station = null;

        try {
            JSONArray stations = new JSONArray(s.hasNext() ? s.next() : "[]");
            station = StationSelector.getClosestStation(stations, loc);
        } catch (JSONException e) {
            Log.e(TAG, "JSONException thrown in MainActivity.dispClosestStationInfo", e);
        }

        ((TextView) findViewById(R.id.textView_output)).setText(
                String.format("Name: %s\nID: %d\nLocation: %f, %f", station.name,
                station.id, station.latitude, station.longitude)
        );

        new AsyncTask<Void, Void, Boolean>() {
            protected Boolean doInBackground(Void... _) {
                long t = new Date().getTime();
                while (!wifiManager.getConnectionInfo().getSSID().equals(SSID) &&
                        new Date().getTime() - t <= CONNECT_TIMEOUT) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                return wifiManager.getConnectionInfo().getSSID().equals(SSID);
            }

            protected void onPostExecute(Boolean result) {
                connected = result;
                String msg = (connected ? "Connected" : "Failed") + " to Tide Clock for setup";
                Snackbar.make(findViewById(R.id.activity_main), msg, Snackbar.LENGTH_SHORT);
            }
        }.execute();

    }
}
