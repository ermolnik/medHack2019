package ru.ermolnik.medication.rtc_client;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.support.annotation.Nullable;
import android.util.Log;
import org.webrtc.ThreadUtils;
import ru.ermolnik.medication.util.AppRTCUtils;

public class AppRTCProximitySensor implements SensorEventListener {
  private static final String TAG = "AppRTCProximitySensor";
  private final ThreadUtils.ThreadChecker threadChecker = new ThreadUtils.ThreadChecker();

  private final Runnable onSensorStateListener;
  private final SensorManager sensorManager;
  @Nullable private Sensor proximitySensor;
  private boolean lastStateReportIsNear;

  static AppRTCProximitySensor create(Context context, Runnable sensorStateListener) {
    return new AppRTCProximitySensor(context, sensorStateListener);
  }

  private AppRTCProximitySensor(Context context, Runnable sensorStateListener) {
    Log.d(TAG, "AppRTCProximitySensor" + AppRTCUtils.getThreadInfo());
    onSensorStateListener = sensorStateListener;
    sensorManager = ((SensorManager) context.getSystemService(Context.SENSOR_SERVICE));
  }

  public boolean start() {
    threadChecker.checkIsOnValidThread();
    Log.d(TAG, "start" + AppRTCUtils.getThreadInfo());
    if (!initDefaultSensor()) {
      return false;
    }
    sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
    return true;
  }

  public void stop() {
    threadChecker.checkIsOnValidThread();
    Log.d(TAG, "stop" + AppRTCUtils.getThreadInfo());
    if (proximitySensor == null) {
      return;
    }
    sensorManager.unregisterListener(this, proximitySensor);
  }

  public boolean sensorReportsNearState() {
    threadChecker.checkIsOnValidThread();
    return lastStateReportIsNear;
  }

  @Override
  public final void onAccuracyChanged(Sensor sensor, int accuracy) {
    threadChecker.checkIsOnValidThread();
    AppRTCUtils.assertIsTrue(sensor.getType() == Sensor.TYPE_PROXIMITY);
    if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
      Log.e(TAG, "The values returned by this sensor cannot be trusted");
    }
  }

  @Override
  public final void onSensorChanged(SensorEvent event) {
    threadChecker.checkIsOnValidThread();
    AppRTCUtils.assertIsTrue(event.sensor.getType() == Sensor.TYPE_PROXIMITY);
    float distanceInCentimeters = event.values[0];
    if (distanceInCentimeters < proximitySensor.getMaximumRange()) {
      Log.d(TAG, "Proximity sensor => NEAR state");
      lastStateReportIsNear = true;
    } else {
      Log.d(TAG, "Proximity sensor => FAR state");
      lastStateReportIsNear = false;
    }

    if (onSensorStateListener != null) {
      onSensorStateListener.run();
    }

    Log.d(TAG, "onSensorChanged" + AppRTCUtils.getThreadInfo() + ": "
            + "accuracy=" + event.accuracy + ", timestamp=" + event.timestamp + ", distance="
            + event.values[0]);
  }

  private boolean initDefaultSensor() {
    if (proximitySensor != null) {
      return true;
    }
    proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
    if (proximitySensor == null) {
      return false;
    }
    logProximitySensorInfo();
    return true;
  }

  private void logProximitySensorInfo() {
    if (proximitySensor == null) {
      return;
    }
    StringBuilder info = new StringBuilder("Proximity sensor: ");
    info.append("name=").append(proximitySensor.getName());
    info.append(", vendor: ").append(proximitySensor.getVendor());
    info.append(", power: ").append(proximitySensor.getPower());
    info.append(", resolution: ").append(proximitySensor.getResolution());
    info.append(", max range: ").append(proximitySensor.getMaximumRange());
    info.append(", min delay: ").append(proximitySensor.getMinDelay());
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
      // Added in API level 20.
      info.append(", type: ").append(proximitySensor.getStringType());
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      // Added in API level 21.
      info.append(", max delay: ").append(proximitySensor.getMaxDelay());
      info.append(", reporting mode: ").append(proximitySensor.getReportingMode());
      info.append(", isWakeUpSensor: ").append(proximitySensor.isWakeUpSensor());
    }
    Log.d(TAG, info.toString());
  }
}
