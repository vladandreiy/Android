package com.example.stepcounter;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class DataViewActivity extends AppCompatActivity implements SensorEventListener, LocationListener {
    public static SensorManager sensorManager;
    public static Sensor sensorStepAndroid, accelerometer;

    private float[] rawAccelValues = new float[3];
    private static final int SMOOTHING_WINDOW_SIZE = 20;
    private float[][] accelerationValueHistory = new float[3][SMOOTHING_WINDOW_SIZE];
    private float[] runningAccelTotal = new float[3];
    private float[] currentAccelAverage = new float[3];
    private int currentIndex = 0;

    public static int stepCounter = 0;
    public static int stepCounterAndroid = 0;

    // Graphs
    private double graph1LastXValue = 0;
    private double graph2LastXValue = 0;
    private LineGraphSeries<DataPoint> graphAccelerometer;
    private LineGraphSeries<DataPoint> graphFilteredAccelerometer;

    // Peak detection
    private double lastXPoint = 1;
    double stepThreshold = 1;
    double noiseThreshold = 2;
    private final int windowSize = 10;

    private TextView speedTv;
    private double lastLatitude = 0;
    private double lastLongitude = 0;
    private double timestampSpeed = 0;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.debug_main);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensorStepAndroid = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, sensorStepAndroid, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);

        speedTv = findViewById(R.id.speed);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1000);
        else {
            getPermissions();
        }

        GraphView graph = this.findViewById(R.id.graph);
        graphAccelerometer = new LineGraphSeries<>();
        graph.addSeries(graphAccelerometer);
        graph.setTitle("Accelerator Signal");
        graph.getGridLabelRenderer().setVerticalAxisTitle("Signal Value");
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(60);

        GraphView graph2 = this.findViewById(R.id.graph2);
        graphFilteredAccelerometer = new LineGraphSeries<>();
        graph2.setTitle("Smoothed Signal");
        graph2.addSeries(graphFilteredAccelerometer);
        graph2.getGridLabelRenderer().setVerticalAxisTitle("Signal Value");
        graph2.getViewport().setXAxisBoundsManual(true);
        graph2.getViewport().setMinX(0);
        graph2.getViewport().setMaxX(60);
    }

    public void goHomeButton(View v) {
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("steps", stepCounter);
        this.startActivity(i);
    }

    private void getPermissions() {
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null)
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                return;
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        Toast.makeText(this, "Waiting for GPS connection", Toast.LENGTH_SHORT).show();
    }

    private void updateSpeed(Location location) {
        double currentSpeed = 0;
        if (location != null) {
            if (timestampSpeed == 0)
                timestampSpeed = System.currentTimeMillis();
            double distance = calculateDistance(lastLatitude, lastLongitude, location.getLatitude(), location.getLongitude());
            double dt = (location.getTime() - timestampSpeed) / 1000;
            currentSpeed = distance * dt;
            timestampSpeed = location.getTime();
            lastLatitude = location.getLatitude();
            lastLongitude = location.getLongitude();
        }
        DecimalFormat f = new DecimalFormat("##.00");
        if (currentSpeed < 0.1)
            speedTv.setText("Estimated speed: " + 0 + " m/s");
        else
            speedTv.setText("Estimated speed: " + String.format("%.2f", currentSpeed) + " m/s");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1000) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                getPermissions();
            else
                finish();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onSensorChanged(SensorEvent e) {
        switch (e.sensor.getType()) {
            case Sensor.TYPE_STEP_DETECTOR:
                Log.d("DebugActivity", "STEP COUNTER");
                stepCounterAndroid++;
                break;
            case Sensor.TYPE_ACCELEROMETER:
                rawAccelValues[0] = e.values[0];
                rawAccelValues[1] = e.values[1];
                rawAccelValues[2] = e.values[2];

                double lastMag = Math.sqrt(Math.pow(rawAccelValues[0], 2) + Math.pow(rawAccelValues[1], 2) + Math.pow(rawAccelValues[2], 2));

                for (int i = 0; i < 3; i++) {
                    // Erase last value from total
                    runningAccelTotal[i] = runningAccelTotal[i] - accelerationValueHistory[i][currentIndex];
                    // Add current value
                    accelerationValueHistory[i][currentIndex] = rawAccelValues[i];
                    // Add current value to total
                    runningAccelTotal[i] = runningAccelTotal[i] + accelerationValueHistory[i][currentIndex];
                    // Calculate average
                    currentAccelAverage[i] = runningAccelTotal[i] / SMOOTHING_WINDOW_SIZE;
                }
                currentIndex = (currentIndex + 1) % SMOOTHING_WINDOW_SIZE;

                double avgMag = Math.sqrt(Math.pow(currentAccelAverage[0], 2) + Math.pow(currentAccelAverage[1], 2) + Math.pow(currentAccelAverage[2], 2));
                double netMag = lastMag - avgMag;

                //update graph data points
                graph1LastXValue += 1d;
                graphAccelerometer.appendData(new DataPoint(graph1LastXValue, lastMag), true, 60);

                graph2LastXValue += 1d;
                graphFilteredAccelerometer.appendData(new DataPoint(graph2LastXValue, netMag), true, 60);

        }

        TextView calculatedStep = this.findViewById(R.id.tv1);
        TextView androidStep = this.findViewById(R.id.tv2);

        peakDetection();

        calculatedStep.setText("Steps Tracked: " + stepCounter);
        androidStep.setText("Android Steps Tracked: " + stepCounterAndroid);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void peakDetection() {
        double highestValX = graphFilteredAccelerometer.getHighestValueX();
        if (highestValX - lastXPoint < windowSize) {
            return;
        }
        Iterator<DataPoint> valuesInWindow = graphFilteredAccelerometer.getValues(lastXPoint, highestValX);
        lastXPoint = highestValX;
        double forwardSlope;
        double downwardSlope;
        List<DataPoint> dataPointList = new ArrayList<>();
        valuesInWindow.forEachRemaining(dataPointList::add);

        for (int i = 1; i < dataPointList.size() - 1; i++) {
            forwardSlope = dataPointList.get(i + 1).getY() - dataPointList.get(i).getY();
            downwardSlope = dataPointList.get(i).getY() - dataPointList.get(i - 1).getY();

            if (forwardSlope < 0 &&
                    downwardSlope > 0 &&
                    dataPointList.get(i).getY() > stepThreshold &&
                    dataPointList.get(i).getY() < noiseThreshold) {
                stepCounter++;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        this.updateSpeed(location);
    }

    private static double calculateDistance(double lat1, double long1, double lat2, double long2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLong = Math.toRadians(long2 - long1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2)) * Math.sin(dLong / 2)
                * Math.sin(dLong / 2);
        double c = 2 * Math.asin(Math.sqrt(a));
        double distanceInMeters = Math.round(6371000 * c * 100) / 100.0;
        return distanceInMeters;
    }
}