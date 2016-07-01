package com.kcorr.tideclockremote;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Scanner;

public class MainActivity extends AppCompatActivity {

    private final String TAG = "MainActivity";
    private final int REQUEST_PERMISSION_COARSE_LOCATION = 1;
    private final int CONNECT_TIMEOUT = 5000;
    private final int SOCKET_TIMEOUT = 1000;

    private LocationManager locationManager;
    private WifiManager wifiManager;
    private View mainActivityView;

    private Socket socket = new Socket();
    private Station station;
    private String info = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        this.locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        this.wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);

        this.mainActivityView = findViewById(R.id.activity_main);
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
                updateInfo();
                connect(Util.SSID, Util.PSK, new Runnable() {
                    @Override
                    public void run() {
                        sendInfo();
                    }
                });
                return true;
            case R.id.action_settings:
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int request, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (request) {
            case REQUEST_PERMISSION_COARSE_LOCATION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    updateInfo();
        }
    }

    private void updateInfo() {

        // If we don't have ACCESS_COARSE_LOCATION permission, ask for it and then return here
        int perm = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
        if (perm != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_PERMISSION_COARSE_LOCATION);
            return;
        }

        // Get last known location based on network location (cellular, wifi)
        Location loc = this.locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

        // Find closest station
        InputStream is = this.getResources().openRawResource(R.raw.stations);
        Scanner s = new Scanner(is).useDelimiter("\\A");
        try {
            JSONArray stations = new JSONArray(s.hasNext() ? s.next() : "[]");
            station = StationSelector.getClosestStation(stations, loc);
        } catch (JSONException e) {
            Log.e(TAG, "JSONException thrown in MainActivity.dispClosestStationInfo", e);
        }

        // Display closest station data in TextView
        if (station != null) {
            ((TextView) findViewById(R.id.textView_output)).setText(
                    String.format(getString(R.string.info_station), station.name,
                            station.id, station.latitude, station.longitude)
            );
            info = "ID " + station.id;
        }
    }

    private void connect(String ssid, String psk, final Runnable callback) {
        Snackbar.make(mainActivityView, getString(R.string.msg_connecting_wifi), Snackbar.LENGTH_SHORT);

        wifiManager.disconnect();
        boolean foundNetwork = false;

        // Enable setup AP if already in network config
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

            final int networkId = wifiManager.addNetwork(conf);
            wifiManager.enableNetwork(networkId, true);
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Added network to wifi configuration, id=" + networkId);
            }
        }

        // Connect to setup AP, and display success message, or failure after CONNECT_TIMEOUT ms (5 secs)
        wifiManager.reconnect();

        final AsyncTask<Void, Void, Boolean> connectSocketTask = new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... _) {
                try {
                    socket.connect(new InetSocketAddress(Util.HOST, Util.PORT), CONNECT_TIMEOUT);
                } catch (IOException e) {
                    Snackbar.make(mainActivityView, getString(R.string.msg_failed_socket), Snackbar.LENGTH_SHORT);
                    if (BuildConfig.DEBUG)
                        Log.e(TAG, "Failed to open socket", e);
                }
                return socket.isConnected();
            }

            @Override
            protected void onPostExecute(Boolean result) {
                if (result) {
                    callback.run();
                }
            }
        };

        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... _) {
                final long t = new Date().getTime();
                while (!wifiManager.getConnectionInfo().getSSID().equals(Util.SSID) &&
                        new Date().getTime() - t <= CONNECT_TIMEOUT) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                return wifiManager.getConnectionInfo().getSSID().equals(Util.SSID);
            }

            @Override
            protected void onPostExecute(Boolean result) {
                if (result) {
                    connectSocketTask.execute();
                }
                final String msg = getString(result ? R.string.msg_connected_wifi : R.string.msg_failed_wifi);
                Snackbar.make(mainActivityView, msg, Snackbar.LENGTH_SHORT);
            }
        }.execute();
    }

    private void sendInfo() {
        try {
            OutputStream os = socket.getOutputStream();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                os.write(info.getBytes(StandardCharsets.US_ASCII));
            else
                os.write(info.getBytes(Charset.forName("US-ASCII")));
            os.flush();
        } catch (IOException e) {
            String msg = getString(R.string.msg_send_failed);
            Snackbar.make(mainActivityView, msg, Snackbar.LENGTH_SHORT);
            if (BuildConfig.DEBUG)
                Log.e(TAG, "Failed to write to DataOutputStream, e");
        }

        try {
            byte[] input = new byte[4];
            socket.setSoTimeout(SOCKET_TIMEOUT);
            InputStream is = socket.getInputStream();
            is.read(input, 0, 3);
        } catch (SocketTimeoutException e) {
            String msg = getString(R.string.msg_send_failed);
            Snackbar.make(mainActivityView, msg, Snackbar.LENGTH_SHORT);
            if (BuildConfig.DEBUG)
                Log.e(TAG, "Socket timed out in sendInfo()", e);
        } catch (SocketException e) {
            String msg = getString(R.string.msg_send_failed);
            Snackbar.make(mainActivityView, msg, Snackbar.LENGTH_SHORT);
            if (BuildConfig.DEBUG)
                Log.e(TAG, "SocketException while setting timeout in sendInfo()", e);
        } catch (IOException e) {
            String msg = getString(R.string.msg_send_failed);
            Snackbar.make(mainActivityView, msg, Snackbar.LENGTH_SHORT);
            if (BuildConfig.DEBUG)
                Log.e(TAG, "IOException in sendInfo()", e);
        }
    }
}
