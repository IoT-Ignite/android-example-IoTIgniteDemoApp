package com.ardic.android.iotignitedemoapp;

import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.ardic.android.iotignitedemoapp.constants.Constants;

public class SensorsActivity extends AppCompatActivity {

    private static String TAG = "IoTIgniteDemoApp";

    private TextView temperatureValue, humidityValue;
    private SeekBar seekBarTemperature, seekBarHumidity;
    private ImageView imageLamp;
    private ToggleButton toggleLamp;
    private VirtualDemoNodeHandler mDemoNodeHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensors);
        mDemoNodeHandler = new VirtualDemoNodeHandler(this, getApplicationContext());
        mDemoNodeHandler.start();
        initUIComponents();
    }

    private void initUIComponents() {
        seekBarTemperature = (SeekBar)findViewById(R.id.seekBarTemperature);
        temperatureValue = (TextView)findViewById(R.id.textTemperatureValue);
        seekBarTemperature.setProgress(Constants.INIT_TEMP);
        temperatureValue.setText(String.valueOf(Constants.INIT_TEMP) +  " \u00b0 C");
        seekBarTemperature.setMax(100);

        seekBarTemperature.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                temperatureValue.setText(String.valueOf(i) +  " \u00b0 C");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mDemoNodeHandler.sendData(Constants.TEMP_THING, seekBar.getProgress());
            }
        });

        seekBarHumidity = (SeekBar)findViewById(R.id.seekBarHumidity);
        humidityValue = (TextView)findViewById(R.id.textHumidityValue);
        seekBarHumidity.setProgress(Constants.INIT_HUM);
        humidityValue.setText(String.valueOf(Constants.INIT_HUM) + " %");
        seekBarHumidity.setMax(100);

        seekBarHumidity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                humidityValue.setText(String.valueOf(i) + " %");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mDemoNodeHandler.sendData(Constants.HUM_THING, seekBar.getProgress());
            }
        });

        imageLamp = (ImageView)findViewById(R.id.imageLamp);
        toggleLamp = (ToggleButton)findViewById(R.id.toggleLamp);
        toggleLamp.setChecked(true);
        imageLamp.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.lamp_on));

        toggleLamp.setOnCheckedChangeListener(new ToggleButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if(b) {
                    imageLamp.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.lamp_on));
                    mDemoNodeHandler.sendData(Constants.LAMP_THING, 1);
                } else {
                    imageLamp.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.lamp_off));
                    mDemoNodeHandler.sendData(Constants.LAMP_THING, 0);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mDemoNodeHandler.stop();

    }
}
