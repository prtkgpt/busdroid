package com.startupbus.location.service;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;


import com.startupbus.location.R;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import android.content.SharedPreferences;

import com.cleardb.app.ClearDBQueryException;
import com.cleardb.app.ClearDBInTransactionException;
import com.cleardb.app.Client;
import org.json.JSONObject;
import org.json.JSONArray;

// import com.startupbus.location.service.GPSLoggerService;

public class NetUpdateService extends Service {

    public static final String DATABASE_NAME = "GPSLOGGERDB";
    public static final String POINTS_TABLE_NAME = "LOCATION_POINTS";

    public static final String REMOTE_TABLE_NAME = "startupbus";

    private final DecimalFormat sevenSigDigits = new DecimalFormat("0.#######");
    private final DateFormat timestampFormat = new SimpleDateFormat("yyyyMMddHHmmss");

    private LocationManager lm;
    private LocationListener locationListener;
    private SQLiteDatabase db;

    private static long minTimeMillis = 2000;
    private static long minDistanceMeters = 10;
    private static float minAccuracyMeters = 35;

    private int lastStatus = 0;
    private static boolean showingDebugToast = false;

    private static final String tag = "BusDroid:NetUpdateService";

    public static final String PREFS_NAME = "BusdroidPrefs";
    private SharedPreferences settings;
    private SharedPreferences.Editor prefedit;
    private String bus_id;
    private long last_update;

    private final String APP_ID = "3bc0af918733f74f08d0b274e7ede7b0";
    private final String API_KEY = "82fb3d39213cf1b75717eac4e1dd8c30b32234cb";

    private com.cleardb.app.Client cleardbClient;

    private Timer testTimer;

   // private static final String INSERT = "insert into " 
   //    + REMOTE_TABLE_NAME + "(bus_id, timestamp, longitude, latitude) values ('?', '?', '?', '?')";


    public void sendUpdate(String bus_id, Long timestamp, double lat, double lon) {
	try {
	    cleardbClient = new com.cleardb.app.Client(API_KEY, APP_ID);
	    cleardbClient.startTransaction();
	} catch (ClearDBInTransactionException e) {
	    return;
	}
	String query = String.format("INSERT INTO startupbus (bus_id, timestamp, longitude, latitude) VALUES ('%s', '%d', '%f', '%f')",
			      bus_id,
			      timestamp,
			      lon,
			      lat);
	try {
	    cleardbClient.query(query);
	} catch (ClearDBQueryException e) {
	    Log.i(tag, "Query fail, ClearDB");
	} catch (Exception e) {
	    Log.i(tag, "Query fail, other");
	}

	try {
	    JSONObject payload = cleardbClient.sendTransaction();
	} catch (ClearDBQueryException clearE) {
	    System.out.println("ClearDB Exception: " + clearE.getMessage());
	} catch (Exception e) {
	    System.out.println("General Exception: " + e.getMessage());
	}
	Log.i(tag, "Update run");
    }

    ////////// Timer
    class testTask extends TimerTask {
	public void run() {
	    last_update = settings.getLong("last_update", 0);
	    Log.i(tag, String.format("Got last update: %d", last_update));
	    SQLiteDatabase db = openOrCreateDatabase(DATABASE_NAME, SQLiteDatabase.OPEN_READONLY, null);
	    String query = String.format("SELECT * from %s WHERE TIMESTAMP > %d ORDER BY timestamp DESC LIMIT 1;",
					 POINTS_TABLE_NAME,
					 last_update);
	    Cursor cur = db.rawQuery(query, new String [] {});
	    try {
		cur.moveToFirst();
		Double lon = cur.getDouble(cur.getColumnIndex("LONGITUDE"));
		Double lat = cur.getDouble(cur.getColumnIndex("LATITUDE"));
		Long timestamp = cur.getLong(cur.getColumnIndex("TIMESTAMP"));
		Log.i(tag, String.format("%s: %f, %f at %d (latest since  %d)", bus_id, lat, lon, timestamp, last_update));
		sendUpdate(bus_id, timestamp, lon, lat);
		prefedit.putLong("last_update", (long)timestamp);
		prefedit.commit();
	    } catch (Exception e) {
		Log.i(tag, String.format("No new location (since %d)", last_update));
	    }
        }
    }

    private final IBinder mBinder = new LocalBinder();
    @Override
    public void onCreate() {
    	super.onCreate();

	settings = getSharedPreferences(PREFS_NAME, 0);
	prefedit = settings.edit();
	bus_id = settings.getString("bus_id", "Test");

	testTimer = new Timer();
	testTimer.scheduleAtFixedRate(new testTask(), 10L, 30*1000L);
    }

    @Override
    public void onDestroy() {
    	super.onDestroy();
	// sendUpdate();
	testTimer.cancel();
    }

    @Override
    	public IBinder onBind(Intent intent) {
    	return mBinder;
    }

    public void showToast(String msg) {
	Toast.makeText(getBaseContext(), "SGmessage" + msg,
		       Toast.LENGTH_SHORT).show();
    }

    /**
     * Class for clients to access. Because we know this service always runs in
     * the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
    	NetUpdateService getService() {
    	    return NetUpdateService.this;
    	}
    }

}
