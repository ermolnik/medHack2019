package ru.ermolnik.medication.rtc_client;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.support.annotation.Nullable;
import android.util.Log;

import org.webrtc.ThreadUtils;

import java.util.List;
import java.util.Set;
import ru.ermolnik.medication.util.AppRTCUtils;

/**
 * AppRTCProximitySensor manages functions related to Bluetoth devices in the
 * AppRTC demo.
 */
public class AppRTCBluetoothManager {
  private static final String TAG = "AppRTCBluetoothManager";
  private static final int BLUETOOTH_SCO_TIMEOUT_MS = 4000;
  private static final int MAX_SCO_CONNECTION_ATTEMPTS = 2;

  public enum State {
    UNINITIALIZED,
    ERROR,
    HEADSET_UNAVAILABLE,
    HEADSET_AVAILABLE,
    SCO_DISCONNECTING,
    SCO_CONNECTING,
    SCO_CONNECTED
  }

  private final Context apprtcContext;
  private final AppRTCAudioManager apprtcAudioManager;
  @Nullable
  private final AudioManager audioManager;
  private final Handler handler;

  int scoConnectionAttempts;
  private State bluetoothState;
  private final BluetoothProfile.ServiceListener bluetoothServiceListener;
  @Nullable
  private BluetoothAdapter bluetoothAdapter;
  @Nullable
  private BluetoothHeadset bluetoothHeadset;
  @Nullable
  private BluetoothDevice bluetoothDevice;
  private final BroadcastReceiver bluetoothHeadsetReceiver;

  private final Runnable bluetoothTimeoutRunnable = () -> bluetoothTimeout();

  private class BluetoothServiceListener implements BluetoothProfile.ServiceListener {
    @Override
    public void onServiceConnected(int profile, BluetoothProfile proxy) {
      if (profile != BluetoothProfile.HEADSET || bluetoothState == State.UNINITIALIZED) {
        return;
      }
      Log.d(TAG, "BluetoothServiceListener.onServiceConnected: BT state=" + bluetoothState);
      bluetoothHeadset = (BluetoothHeadset) proxy;
      updateAudioDeviceState();
      Log.d(TAG, "onServiceConnected done: BT state=" + bluetoothState);
    }

    @Override
    public void onServiceDisconnected(int profile) {
      if (profile != BluetoothProfile.HEADSET || bluetoothState == State.UNINITIALIZED) {
        return;
      }
      Log.d(TAG, "BluetoothServiceListener.onServiceDisconnected: BT state=" + bluetoothState);
      stopScoAudio();
      bluetoothHeadset = null;
      bluetoothDevice = null;
      bluetoothState = State.HEADSET_UNAVAILABLE;
      updateAudioDeviceState();
      Log.d(TAG, "onServiceDisconnected done: BT state=" + bluetoothState);
    }
  }

  private class BluetoothHeadsetBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (bluetoothState == State.UNINITIALIZED) {
        return;
      }
      final String action = intent.getAction();
      if (action.equals(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)) {
        final int state =
            intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_DISCONNECTED);
        Log.d(TAG, "BluetoothHeadsetBroadcastReceiver.onReceive: "
                + "a=ACTION_CONNECTION_STATE_CHANGED, "
                + "s=" + stateToString(state) + ", "
                + "sb=" + isInitialStickyBroadcast() + ", "
                + "BT state: " + bluetoothState);
        if (state == BluetoothHeadset.STATE_CONNECTED) {
          scoConnectionAttempts = 0;
          updateAudioDeviceState();
        } else if (state == BluetoothHeadset.STATE_CONNECTING) {
        } else if (state == BluetoothHeadset.STATE_DISCONNECTING) {
        } else if (state == BluetoothHeadset.STATE_DISCONNECTED) {
          stopScoAudio();
          updateAudioDeviceState();
        }
      } else if (action.equals(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)) {
        final int state = intent.getIntExtra(
            BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_AUDIO_DISCONNECTED);
        Log.d(TAG, "BluetoothHeadsetBroadcastReceiver.onReceive: "
                + "a=ACTION_AUDIO_STATE_CHANGED, "
                + "s=" + stateToString(state) + ", "
                + "sb=" + isInitialStickyBroadcast() + ", "
                + "BT state: " + bluetoothState);
        if (state == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
          cancelTimer();
          if (bluetoothState == State.SCO_CONNECTING) {
            Log.d(TAG, "+++ Bluetooth audio SCO is now connected");
            bluetoothState = State.SCO_CONNECTED;
            scoConnectionAttempts = 0;
            updateAudioDeviceState();
          } else {
            Log.w(TAG, "Unexpected state BluetoothHeadset.STATE_AUDIO_CONNECTED");
          }
        } else if (state == BluetoothHeadset.STATE_AUDIO_CONNECTING) {
          Log.d(TAG, "+++ Bluetooth audio SCO is now connecting...");
        } else if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
          Log.d(TAG, "+++ Bluetooth audio SCO is now disconnected");
          if (isInitialStickyBroadcast()) {
            Log.d(TAG, "Ignore STATE_AUDIO_DISCONNECTED initial sticky broadcast.");
            return;
          }
          updateAudioDeviceState();
        }
      }
      Log.d(TAG, "onReceive done: BT state=" + bluetoothState);
    }
  }

  static AppRTCBluetoothManager create(Context context, AppRTCAudioManager audioManager) {
    Log.d(TAG, "create" + AppRTCUtils.getThreadInfo());
    return new AppRTCBluetoothManager(context, audioManager);
  }

  protected AppRTCBluetoothManager(Context context, AppRTCAudioManager audioManager) {
    Log.d(TAG, "ctor");
    ThreadUtils.checkIsOnMainThread();
    apprtcContext = context;
    apprtcAudioManager = audioManager;
    this.audioManager = getAudioManager(context);
    bluetoothState = State.UNINITIALIZED;
    bluetoothServiceListener = new BluetoothServiceListener();
    bluetoothHeadsetReceiver = new BluetoothHeadsetBroadcastReceiver();
    handler = new Handler(Looper.getMainLooper());
  }

  public State getState() {
    ThreadUtils.checkIsOnMainThread();
    return bluetoothState;
  }

  public void start() {
    ThreadUtils.checkIsOnMainThread();
    Log.d(TAG, "start");
    if (!hasPermission(apprtcContext, android.Manifest.permission.BLUETOOTH)) {
      Log.w(TAG, "Process (pid=" + Process.myPid() + ") lacks BLUETOOTH permission");
      return;
    }
    if (bluetoothState != State.UNINITIALIZED) {
      Log.w(TAG, "Invalid BT state");
      return;
    }
    bluetoothHeadset = null;
    bluetoothDevice = null;
    scoConnectionAttempts = 0;
    bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    if (bluetoothAdapter == null) {
      Log.w(TAG, "Device does not support Bluetooth");
      return;
    }
    if (!audioManager.isBluetoothScoAvailableOffCall()) {
      Log.e(TAG, "Bluetooth SCO audio is not available off call");
      return;
    }
    logBluetoothAdapterInfo(bluetoothAdapter);
    if (!getBluetoothProfileProxy(
            apprtcContext, bluetoothServiceListener, BluetoothProfile.HEADSET)) {
      Log.e(TAG, "BluetoothAdapter.getProfileProxy(HEADSET) failed");
      return;
    }
    IntentFilter bluetoothHeadsetFilter = new IntentFilter();
    bluetoothHeadsetFilter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
    bluetoothHeadsetFilter.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
    registerReceiver(bluetoothHeadsetReceiver, bluetoothHeadsetFilter);
    Log.d(TAG, "HEADSET profile state: "
            + stateToString(bluetoothAdapter.getProfileConnectionState(BluetoothProfile.HEADSET)));
    Log.d(TAG, "Bluetooth proxy for headset profile has started");
    bluetoothState = State.HEADSET_UNAVAILABLE;
    Log.d(TAG, "start done: BT state=" + bluetoothState);
  }

  public void stop() {
    ThreadUtils.checkIsOnMainThread();
    Log.d(TAG, "stop: BT state=" + bluetoothState);
    if (bluetoothAdapter == null) {
      return;
    }
    stopScoAudio();
    if (bluetoothState == State.UNINITIALIZED) {
      return;
    }
    unregisterReceiver(bluetoothHeadsetReceiver);
    cancelTimer();
    if (bluetoothHeadset != null) {
      bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset);
      bluetoothHeadset = null;
    }
    bluetoothAdapter = null;
    bluetoothDevice = null;
    bluetoothState = State.UNINITIALIZED;
    Log.d(TAG, "stop done: BT state=" + bluetoothState);
  }

  public boolean startScoAudio() {
    ThreadUtils.checkIsOnMainThread();
    Log.d(TAG, "startSco: BT state=" + bluetoothState + ", "
            + "attempts: " + scoConnectionAttempts + ", "
            + "SCO is on: " + isScoOn());
    if (scoConnectionAttempts >= MAX_SCO_CONNECTION_ATTEMPTS) {
      Log.e(TAG, "BT SCO connection fails - no more attempts");
      return false;
    }
    if (bluetoothState != State.HEADSET_AVAILABLE) {
      Log.e(TAG, "BT SCO connection fails - no headset available");
      return false;
    }
    Log.d(TAG, "Starting Bluetooth SCO and waits for ACTION_AUDIO_STATE_CHANGED...");
    bluetoothState = State.SCO_CONNECTING;
    audioManager.startBluetoothSco();
    audioManager.setBluetoothScoOn(true);
    scoConnectionAttempts++;
    startTimer();
    Log.d(TAG, "startScoAudio done: BT state=" + bluetoothState + ", "
            + "SCO is on: " + isScoOn());
    return true;
  }

  public void stopScoAudio() {
    ThreadUtils.checkIsOnMainThread();
    Log.d(TAG, "stopScoAudio: BT state=" + bluetoothState + ", "
            + "SCO is on: " + isScoOn());
    if (bluetoothState != State.SCO_CONNECTING && bluetoothState != State.SCO_CONNECTED) {
      return;
    }
    cancelTimer();
    audioManager.stopBluetoothSco();
    audioManager.setBluetoothScoOn(false);
    bluetoothState = State.SCO_DISCONNECTING;
    Log.d(TAG, "stopScoAudio done: BT state=" + bluetoothState + ", "
            + "SCO is on: " + isScoOn());
  }

  public void updateDevice() {
    if (bluetoothState == State.UNINITIALIZED || bluetoothHeadset == null) {
      return;
    }
    Log.d(TAG, "updateDevice");
    List<BluetoothDevice> devices = bluetoothHeadset.getConnectedDevices();
    if (devices.isEmpty()) {
      bluetoothDevice = null;
      bluetoothState = State.HEADSET_UNAVAILABLE;
      Log.d(TAG, "No connected bluetooth headset");
    } else {
      bluetoothDevice = devices.get(0);
      bluetoothState = State.HEADSET_AVAILABLE;
      Log.d(TAG, "Connected bluetooth headset: "
              + "name=" + bluetoothDevice.getName() + ", "
              + "state=" + stateToString(bluetoothHeadset.getConnectionState(bluetoothDevice))
              + ", SCO audio=" + bluetoothHeadset.isAudioConnected(bluetoothDevice));
    }
    Log.d(TAG, "updateDevice done: BT state=" + bluetoothState);
  }

  @Nullable
  protected AudioManager getAudioManager(Context context) {
    return (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
  }

  protected void registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
    apprtcContext.registerReceiver(receiver, filter);
  }

  protected void unregisterReceiver(BroadcastReceiver receiver) {
    apprtcContext.unregisterReceiver(receiver);
  }

  protected boolean getBluetoothProfileProxy(
      Context context, BluetoothProfile.ServiceListener listener, int profile) {
    return bluetoothAdapter.getProfileProxy(context, listener, profile);
  }

  protected boolean hasPermission(Context context, String permission) {
    return apprtcContext.checkPermission(permission, Process.myPid(), Process.myUid())
        == PackageManager.PERMISSION_GRANTED;
  }

  @SuppressLint("HardwareIds")
  protected void logBluetoothAdapterInfo(BluetoothAdapter localAdapter) {
    Log.d(TAG, "BluetoothAdapter: "
            + "enabled=" + localAdapter.isEnabled() + ", "
            + "state=" + stateToString(localAdapter.getState()) + ", "
            + "name=" + localAdapter.getName() + ", "
            + "address=" + localAdapter.getAddress());
    // Log the set of BluetoothDevice objects that are bonded (paired) to the local adapter.
    Set<BluetoothDevice> pairedDevices = localAdapter.getBondedDevices();
    if (!pairedDevices.isEmpty()) {
      Log.d(TAG, "paired devices:");
      for (BluetoothDevice device : pairedDevices) {
        Log.d(TAG, " name=" + device.getName() + ", address=" + device.getAddress());
      }
    }
  }

  private void updateAudioDeviceState() {
    ThreadUtils.checkIsOnMainThread();
    Log.d(TAG, "updateAudioDeviceState");
    apprtcAudioManager.updateAudioDeviceState();
  }

  private void startTimer() {
    ThreadUtils.checkIsOnMainThread();
    Log.d(TAG, "startTimer");
    handler.postDelayed(bluetoothTimeoutRunnable, BLUETOOTH_SCO_TIMEOUT_MS);
  }

  private void cancelTimer() {
    ThreadUtils.checkIsOnMainThread();
    Log.d(TAG, "cancelTimer");
    handler.removeCallbacks(bluetoothTimeoutRunnable);
  }

  private void bluetoothTimeout() {
    ThreadUtils.checkIsOnMainThread();
    if (bluetoothState == State.UNINITIALIZED || bluetoothHeadset == null) {
      return;
    }
    Log.d(TAG, "bluetoothTimeout: BT state=" + bluetoothState + ", "
            + "attempts: " + scoConnectionAttempts + ", "
            + "SCO is on: " + isScoOn());
    if (bluetoothState != State.SCO_CONNECTING) {
      return;
    }
    boolean scoConnected = false;
    List<BluetoothDevice> devices = bluetoothHeadset.getConnectedDevices();
    if (devices.size() > 0) {
      bluetoothDevice = devices.get(0);
      if (bluetoothHeadset.isAudioConnected(bluetoothDevice)) {
        Log.d(TAG, "SCO connected with " + bluetoothDevice.getName());
        scoConnected = true;
      } else {
        Log.d(TAG, "SCO is not connected with " + bluetoothDevice.getName());
      }
    }
    if (scoConnected) {
      bluetoothState = State.SCO_CONNECTED;
      scoConnectionAttempts = 0;
    } else {
      Log.w(TAG, "BT failed to connect after timeout");
      stopScoAudio();
    }
    updateAudioDeviceState();
    Log.d(TAG, "bluetoothTimeout done: BT state=" + bluetoothState);
  }

  private boolean isScoOn() {
    return audioManager.isBluetoothScoOn();
  }

  private String stateToString(int state) {
    switch (state) {
      case BluetoothAdapter.STATE_DISCONNECTED:
        return "DISCONNECTED";
      case BluetoothAdapter.STATE_CONNECTED:
        return "CONNECTED";
      case BluetoothAdapter.STATE_CONNECTING:
        return "CONNECTING";
      case BluetoothAdapter.STATE_DISCONNECTING:
        return "DISCONNECTING";
      case BluetoothAdapter.STATE_OFF:
        return "OFF";
      case BluetoothAdapter.STATE_ON:
        return "ON";
      case BluetoothAdapter.STATE_TURNING_OFF:
        return "TURNING_OFF";
      case BluetoothAdapter.STATE_TURNING_ON:
        return  "TURNING_ON";
      default:
        return "INVALID";
    }
  }
}
