package com.ardic.android.iotignitedemoapp;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.ardic.android.iotignite.callbacks.ConnectionCallback;
import com.ardic.android.iotignite.enumerations.NodeType;
import com.ardic.android.iotignite.enumerations.ThingCategory;
import com.ardic.android.iotignite.enumerations.ThingDataType;
import com.ardic.android.iotignite.exceptions.AuthenticationException;
import com.ardic.android.iotignite.exceptions.UnsupportedVersionException;
import com.ardic.android.iotignite.exceptions.UnsupportedVersionExceptionType;
import com.ardic.android.iotignite.listeners.NodeListener;
import com.ardic.android.iotignite.listeners.ThingListener;
import com.ardic.android.iotignite.nodes.IotIgniteManager;
import com.ardic.android.iotignite.nodes.Node;
import com.ardic.android.iotignite.things.Thing;
import com.ardic.android.iotignite.things.ThingActionData;
import com.ardic.android.iotignite.things.ThingConfiguration;
import com.ardic.android.iotignite.things.ThingData;
import com.ardic.android.iotignite.things.ThingType;
import com.ardic.android.iotignitedemoapp.constants.Constants;

import java.util.Hashtable;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class VirtualDemoNodeHandler implements ConnectionCallback {

    private static final String TAG = "IoTIgniteDemoApp";

    private static final int NUMBER_OF_THREADS_IN_EXECUTOR = 2;
    private static final long EXECUTOR_START_DELAY = 100L;
    private static volatile ScheduledExecutorService mExecutor;
    private Hashtable<String, ScheduledFuture<?>> tasks = new Hashtable<String, ScheduledFuture<?>>();

    private static IotIgniteManager mIotIgniteManager;
    private Node myNode;
    private Thing mTemperatureThing, mHumidityThing, mLampThing;
    private ThingType mTempThingType, mHumThingType, mLampThingType;
    private ThingDataHandler mThingDataHandler;
    private String appPackageName = "com.ardic.android.iotignitedemoapp";
    private boolean versionError =false;

    private boolean igniteConnected = false;

    // Temperature Thing Listener
    // Receives configuration and action
    private ThingListener tempThingListener = new ThingListener() {
        @Override
        public void onConfigurationReceived(Thing thing) {
            Log.i(TAG, "Config arrived for " + thing.getThingID());
            applyConfiguration(thing);
        }

        @Override
        public void onActionReceived(String nodeId, String sensorId, ThingActionData thingActionData) {

        }

        @Override
        public void onThingUnregistered(String nodeId, String sensorId) {
            Log.i(TAG, "Temperature thing unregistered!");
            stopReadDataTask(nodeId, sensorId);
        }
    };

    // Humidity Thing Listener
    // Receives configuration and action
    private ThingListener humThingListener = new ThingListener() {
        @Override
        public void onConfigurationReceived(Thing thing) {
            Log.i(TAG, "Config arrived for " + thing.getThingID());
            applyConfiguration(thing);
        }

        @Override
        public void onActionReceived(String nodeId, String sensorId, ThingActionData thingActionData) {

        }

        @Override
        public void onThingUnregistered(String nodeId, String sensorId) {
            Log.i(TAG, "Humidity thing unregistered!");
            stopReadDataTask(nodeId, sensorId);
        }
    };

    // Lamp Thing Listener
    // Receives configuration and action
    private ThingListener lampThingListener = new ThingListener() {
        @Override
        public void onConfigurationReceived(Thing thing) {
            Log.i(TAG, "Config arrived for " + thing.getThingID());
            applyConfiguration(thing);
        }

        @Override
        public void onActionReceived(String nodeId, String sensorId, final ThingActionData thingActionData) {
            Log.i(TAG, "Action arrived for " + nodeId + " " + sensorId);
            // Toggle lamp in sensor activity
            if(sensorsActivity != null) {
                sensorsActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ToggleButton toggleLamp = (ToggleButton) sensorsActivity.findViewById(R.id.toggleLamp);
                        Log.i(TAG, thingActionData.getMessage());
                        int message = 0;
                        try {
                            String s = thingActionData.getMessage();
                            if (s != null) {
                                message = Integer.parseInt(s.replace("\"", ""));
                            }
                        } catch (NumberFormatException e) {
                            Log.i(TAG, "Message Invalid");
                        }
                        toggleLamp.setChecked(message == 1);
                    }
                });
            }
        }

        @Override
        public void onThingUnregistered(String nodeId, String sensorId) {
            Log.i(TAG, "Lamp thing unregistered!");
            stopReadDataTask(nodeId, sensorId);
        }
    };

    private static final long IGNITE_TIMER_PERIOD = 5000L;

    private Timer igniteTimer = new Timer();

    private Context applicatonContext;

    private Activity sensorsActivity;

    private IgniteWatchDog igniteWatchDog = new IgniteWatchDog();

    // Handle ignite connection with timer task
    private class IgniteWatchDog extends TimerTask {
        @Override
        public void run() {
            if(!igniteConnected && !versionError) {
                Log.i(TAG, "Rebuild Ignite...");
                start();
            }
        }
    }

    public VirtualDemoNodeHandler(Activity activity, Context appContext) {
        this.applicatonContext = appContext;
        this.sensorsActivity = activity;
    }

    public void start() {
        // Build Ignite Manager
        try {
            mIotIgniteManager = new IotIgniteManager.Builder()
                    .setContext(applicatonContext)
                    .setConnectionListener(this)
                    .build();
        } catch (UnsupportedVersionException e) {
            Log.e(TAG, e.toString());
            versionError = true;
            if (UnsupportedVersionExceptionType.UNSUPPORTED_IOTIGNITE_AGENT_VERSION.toString().equals(e.getMessage())) {
                Log.e(TAG, "UNSUPPORTED_IOTIGNITE_AGENT_VERSION");
                showAgentInstallationDialog();
            } else {
                Log.e(TAG, "UNSUPPORTED_IOTIGNITE_SDK_VERSION");
                showAppInstallationDialog();
            }
        }
        cancelAndScheduleIgniteTimer();
    }

    private void showAgentInstallationDialog() {
        sensorsActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new MaterialDialog.Builder(sensorsActivity)
                        .title("Confirm")
                        .content("Your IoT Ignite Agent version is out of date! Install the latest version?")
                        .positiveText("Agree")
                        .negativeText("Disagree")
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                openUrl("http://iotapp.link/");
                            }
                        })
                        .show();
            }
        });
    }

    private void showAppInstallationDialog() {
        sensorsActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new MaterialDialog.Builder(sensorsActivity)
                        .title("Confirm")
                        .content("Your Demo App is out of date! Install the latest version?")
                        .positiveText("Agree")
                        .negativeText("Disagree")
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                openUrl("https://download.iot-ignite.com/DemoApp/");
                            }
                        })
                        .show();
            }
        });
    }

    private void openUrl(String url) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));
        try {
            sensorsActivity.startActivity(i);
        }catch (ActivityNotFoundException e) {
            showDilaog("Browser could not opened!");
        }
    }

    private void showDilaog(String message) {
        new MaterialDialog.Builder(sensorsActivity)
                .content(message)
                .neutralText("Ok")
                .show();
    }

    public void stop() {
        if (igniteConnected) {
            mHumidityThing.setConnected(false, "Application Destroyed");
            mLampThing.setConnected(false, "Application Destroyed");
            mTemperatureThing.setConnected(false, "Application Destroyed");
            myNode.setConnected(false, "ApplicationDestroyed");
        }
        if(mExecutor != null) {
            mExecutor.shutdown();
        }
    }

    @Override
    public void onConnected() {
        Log.i(TAG, "Ignite Connected!");
        igniteConnected = true;
        updateConnectionStatus(true);

        // IoT Ignite connected.
        // Register node, things and send initial data
        initIgniteVariables();
        sendInitialData();
        cancelAndScheduleIgniteTimer();
    }

    @Override
    public void onDisconnected() {
        Log.i(TAG, "Ignite Disconnected!");
        igniteConnected = false;
        updateConnectionStatus(false);
        cancelAndScheduleIgniteTimer();
    }

    // Change connection status views in sensor activity
    private void updateConnectionStatus(final boolean connected) {
        if(sensorsActivity != null) {
            sensorsActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ImageView imageViewConnection = (ImageView) sensorsActivity.findViewById(R.id.imageViewConnection);
                    TextView textViewConnection = (TextView) sensorsActivity.findViewById(R.id.textConnection);
                    if (connected) {
                        imageViewConnection.setImageDrawable(sensorsActivity.getResources().getDrawable(R.drawable.connected));
                        textViewConnection.setText("Connected");
                    } else {
                        imageViewConnection.setImageDrawable(sensorsActivity.getResources().getDrawable(R.drawable.disconnected));
                        textViewConnection.setText("Disconnected");
                    }
                }
            });
        }
    }

    // Create node and things and register them
    private void initIgniteVariables() {
        mTempThingType = new ThingType("Temperature", "IoT Ignite Devzone", ThingDataType.FLOAT);
        mHumThingType = new ThingType("Humidity", "IoT Ignite Devzone", ThingDataType.FLOAT);
        mLampThingType = new ThingType("Lamp", "IoT Ignite Devzone", ThingDataType.INTEGER);

        myNode = IotIgniteManager.NodeFactory.createNode(Constants.NODE, Constants.NODE, NodeType.GENERIC, null, new NodeListener() {
            @Override
            public void onNodeUnregistered(String s) {
                Log.i(TAG, Constants.NODE + " unregistered!");
            }
        });

        // Register node if not registered and set connection.
        if (!myNode.isRegistered() && myNode.register()) {
            myNode.setConnected(true, Constants.NODE + " is online");
            Log.i(TAG, myNode.getNodeID() + " is successfully registered!");
        } else {
            myNode.setConnected(true, Constants.NODE + " is online");
            Log.i(TAG, myNode.getNodeID() + " is already registered!");
        }
        if (myNode.isRegistered()) {
            mTemperatureThing = myNode.createThing(Constants.TEMP_THING, mTempThingType, ThingCategory.EXTERNAL, false, tempThingListener, null);
            mHumidityThing = myNode.createThing(Constants.HUM_THING, mHumThingType, ThingCategory.EXTERNAL, false, humThingListener, null);
            mLampThing = myNode.createThing(Constants.LAMP_THING, mLampThingType, ThingCategory.EXTERNAL, true, lampThingListener, null);
            registerThingIfNotRegistered(mTemperatureThing);
            registerThingIfNotRegistered(mHumidityThing);
            registerThingIfNotRegistered(mLampThing);
        }
    }

    private void registerThingIfNotRegistered(Thing t) {
        if (!t.isRegistered() && t.register()) {
            t.setConnected(true, t.getThingID() + " connected");
            Log.i(TAG, t.getThingID() + " is successfully registered!");
        } else {
            t.setConnected(true, t.getThingID() + " connected");
            Log.i(TAG, t.getThingID() + " is already registered!");
        }
        applyConfiguration(t);
    }

    // Get thing values from sensor activity
    // Then send these values to IotIgnite
    private class ThingDataHandler implements Runnable {

        Thing mThing;

        ThingDataHandler(Thing thing) {
            mThing = thing;
        }
        @Override
        public void run() {
            ThingData mThingData = new ThingData();
            if(mThing.equals(mTemperatureThing)) {
                SeekBar seekBarTemperature = (SeekBar) sensorsActivity.findViewById(R.id.seekBarTemperature);
                mThingData.addData(seekBarTemperature.getProgress());
            } else if(mThing.equals(mHumidityThing)) {
                SeekBar seekBarHumidity = (SeekBar) sensorsActivity.findViewById(R.id.seekBarHumidity);
                mThingData.addData(seekBarHumidity.getProgress());
            } else if(mThing.equals(mLampThing)) {
                ToggleButton toggleLamp = (ToggleButton) sensorsActivity.findViewById(R.id.toggleLamp);
                mThingData.addData(toggleLamp.isChecked() ? 1 : 0);
            }

            if(mThing.sendData(mThingData)){
                Log.i(TAG, "DATA SENT SUCCESSFULLY : " + mThingData);
            }else{
                Log.i(TAG, "DATA SENT FAILURE");
            }
        }
    }

    // Schedule data readers for things
    private void applyConfiguration(Thing thing) {
        if(thing != null) {
            stopReadDataTask(thing.getNodeID(), thing.getThingID());
            if (thing.getThingConfiguration().getDataReadingFrequency() > 0) {
                mThingDataHandler = new ThingDataHandler(thing);

                mExecutor = Executors.newScheduledThreadPool(NUMBER_OF_THREADS_IN_EXECUTOR);

                ScheduledFuture<?> sf = mExecutor.scheduleAtFixedRate(mThingDataHandler, EXECUTOR_START_DELAY, thing.getThingConfiguration().getDataReadingFrequency(), TimeUnit.MILLISECONDS);
                String key = thing.getNodeID() + "|" + thing.getThingID();
                tasks.put(key, sf);
            }
        }
    }

    // Stop task which reads data from thing
    public void stopReadDataTask(String nodeId, String sensorId) {
        String key = nodeId + "|" + sensorId;
        if (tasks.containsKey(key)) {
            try {
                tasks.get(key).cancel(true);
                tasks.remove(key);
            } catch (Exception e) {
                Log.d(TAG, "Could not stop schedule send data task" + e);
            }
        }
    }

    private void cancelAndScheduleIgniteTimer() {
        igniteTimer.cancel();
        igniteWatchDog.cancel();
        igniteWatchDog = new IgniteWatchDog();
        igniteTimer = new Timer();
        igniteTimer.schedule(igniteWatchDog, IGNITE_TIMER_PERIOD);
    }

    private void sendInitialData() {
        sendData(Constants.TEMP_THING, Constants.INIT_TEMP);
        sendData(Constants.HUM_THING, Constants.INIT_HUM);
        sendData(Constants.LAMP_THING, Constants.INIT_LAMP);
    }

    private boolean isConfigReadWhenArrive(Thing mThing) {
        if (mThing.getThingConfiguration().getDataReadingFrequency() == ThingConfiguration.READING_WHEN_ARRIVE) {
            return true;
        }
        return false;
    }

    // Sends data to IotIgnite immediately
    // If reading frequency is READING_WHEN_ARRIVE in thing configuration
    public void sendData(String thingId, int value) {
        if(igniteConnected) {
            try {
                Node mNode = mIotIgniteManager.getNodeByID(Constants.NODE);
                if(mNode != null) {
                    Thing mThing = mNode.getThingByID(thingId);
                    if (mThing != null) {
                        ThingData mthingData = new ThingData();
                        mthingData.addData(value);
                        if (isConfigReadWhenArrive(mThing) && mThing.sendData(mthingData)) {
                            Log.i(TAG, "DATA SENT SUCCESSFULLY : " + mthingData);
                        } else {
                            Log.i(TAG, "DATA SENT FAILURE");
                        }
                    } else {
                        Log.i(TAG, thingId + " is not registered");
                    }
                } else {
                    Log.i(TAG, Constants.NODE + " is not registered");
                }
            } catch (AuthenticationException e) {
                Log.i(TAG, "AuthenticationException!");
            }
        } else {
            Log.i(TAG, "Ignite Disconnected!");
        }
    }
}