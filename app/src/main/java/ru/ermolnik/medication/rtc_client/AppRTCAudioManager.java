package ru.ermolnik.medication.rtc_client;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;

import org.webrtc.ThreadUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import ru.ermolnik.medication.R;
import ru.ermolnik.medication.util.AppRTCUtils;

public class AppRTCAudioManager {
  private static final String TAG = "AppRTCAudioManager";
  private static final String SPEAKERPHONE_AUTO = "auto";
  private static final String SPEAKERPHONE_TRUE = "true";
  private static final String SPEAKERPHONE_FALSE = "false";

  public enum AudioDevice { SPEAKER_PHONE, WIRED_HEADSET, EARPIECE, BLUETOOTH, NONE }

  public enum AudioManagerState {
    UNINITIALIZED,
    PREINITIALIZED,
    RUNNING,
  }

  public interface AudioManagerEvents {
    void onAudioDeviceChanged(
            AudioDevice selectedAudioDevice, Set<AudioDevice> availableAudioDevices);
  }

  private final Context apprtcContext;
  @Nullable
  private AudioManager audioManager;

  @Nullable
  private AudioManagerEvents audioManagerEvents;
  private AudioManagerState amState;
  private int savedAudioMode = AudioManager.MODE_INVALID;
  private boolean savedIsSpeakerPhoneOn;
  private boolean savedIsMicrophoneMute;
  private boolean hasWiredHeadset;

  private AudioDevice defaultAudioDevice;

  private AudioDevice selectedAudioDevice;

  private AudioDevice userSelectedAudioDevice;

  private final String useSpeakerphone;

  @Nullable private AppRTCProximitySensor proximitySensor;

  private final AppRTCBluetoothManager bluetoothManager;

  private Set<AudioDevice> audioDevices = new HashSet<>();

  private BroadcastReceiver wiredHeadsetReceiver;

  @Nullable
  private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;

  private void onProximitySensorChangedState() {
    if (!useSpeakerphone.equals(SPEAKERPHONE_AUTO)) {
      return;
    }

    if (audioDevices.size() == 2 && audioDevices.contains(AudioDevice.EARPIECE)
        && audioDevices.contains(AudioDevice.SPEAKER_PHONE)) {
      if (proximitySensor.sensorReportsNearState()) {
        setAudioDeviceInternal(AudioDevice.EARPIECE);
      } else {
        setAudioDeviceInternal(AudioDevice.SPEAKER_PHONE);
      }
    }
  }

  private class WiredHeadsetReceiver extends BroadcastReceiver {
    private static final int STATE_UNPLUGGED = 0;
    private static final int STATE_PLUGGED = 1;
    private static final int HAS_NO_MIC = 0;
    private static final int HAS_MIC = 1;

    @Override
    public void onReceive(Context context, Intent intent) {
      int state = intent.getIntExtra("state", STATE_UNPLUGGED);
      int microphone = intent.getIntExtra("microphone", HAS_NO_MIC);
      String name = intent.getStringExtra("name");
      Log.d(TAG, "WiredHeadsetReceiver.onReceive" + AppRTCUtils.getThreadInfo() + ": "
              + "a=" + intent.getAction() + ", s="
              + (state == STATE_UNPLUGGED ? "unplugged" : "plugged") + ", m="
              + (microphone == HAS_MIC ? "mic" : "no mic") + ", n=" + name + ", sb="
              + isInitialStickyBroadcast());
      hasWiredHeadset = (state == STATE_PLUGGED);
      updateAudioDeviceState();
    }
  }

  /** Construction. */
  public static AppRTCAudioManager create(Context context) {
    return new AppRTCAudioManager(context);
  }

  private AppRTCAudioManager(Context context) {
    Log.d(TAG, "ctor");
    ThreadUtils.checkIsOnMainThread();
    apprtcContext = context;
    audioManager = ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE));
    bluetoothManager = AppRTCBluetoothManager.create(context, this);
    wiredHeadsetReceiver = new WiredHeadsetReceiver();
    amState = AudioManagerState.UNINITIALIZED;

    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    useSpeakerphone = sharedPreferences.getString(context.getString(R.string.pref_speakerphone_key),
        context.getString(R.string.pref_speakerphone_default));
    Log.d(TAG, "useSpeakerphone: " + useSpeakerphone);
    if (useSpeakerphone.equals(SPEAKERPHONE_FALSE)) {
      defaultAudioDevice = AudioDevice.EARPIECE;
    } else {
      defaultAudioDevice = AudioDevice.SPEAKER_PHONE;
    }

    proximitySensor = AppRTCProximitySensor.create(context,
        this ::onProximitySensorChangedState);

    Log.d(TAG, "defaultAudioDevice: " + defaultAudioDevice);
    AppRTCUtils.logDeviceInfo(TAG);
  }

  public void start(AudioManagerEvents audioManagerEvents) {
    Log.d(TAG, "start");
    ThreadUtils.checkIsOnMainThread();
    if (amState == AudioManagerState.RUNNING) {
      Log.e(TAG, "AudioManager is already active");
      return;
    }

    Log.d(TAG, "AudioManager starts...");
    this.audioManagerEvents = audioManagerEvents;
    amState = AudioManagerState.RUNNING;

    savedAudioMode = audioManager.getMode();
    savedIsSpeakerPhoneOn = audioManager.isSpeakerphoneOn();
    savedIsMicrophoneMute = audioManager.isMicrophoneMute();
    hasWiredHeadset = hasWiredHeadset();

    audioFocusChangeListener = focusChange -> {
      final String typeOfChange;
      switch (focusChange) {
        case AudioManager.AUDIOFOCUS_GAIN:
          typeOfChange = "AUDIOFOCUS_GAIN";
          break;
        case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
          typeOfChange = "AUDIOFOCUS_GAIN_TRANSIENT";
          break;
        case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
          typeOfChange = "AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE";
          break;
        case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
          typeOfChange = "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK";
          break;
        case AudioManager.AUDIOFOCUS_LOSS:
          typeOfChange = "AUDIOFOCUS_LOSS";
          break;
        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
          typeOfChange = "AUDIOFOCUS_LOSS_TRANSIENT";
          break;
        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
          typeOfChange = "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK";
          break;
        default:
          typeOfChange = "AUDIOFOCUS_INVALID";
          break;
      }
      Log.d(TAG, "onAudioFocusChange: " + typeOfChange);
    };

    int result = audioManager.requestAudioFocus(audioFocusChangeListener,
        AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
    if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
      Log.d(TAG, "Audio focus request granted for VOICE_CALL streams");
    } else {
      Log.e(TAG, "Audio focus request failed");
    }

    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

    setMicrophoneMute(false);

    userSelectedAudioDevice = AudioDevice.NONE;
    selectedAudioDevice = AudioDevice.NONE;
    audioDevices.clear();

    bluetoothManager.start();

    updateAudioDeviceState();

    registerReceiver(wiredHeadsetReceiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
    Log.d(TAG, "AudioManager started");
  }

  public void stop() {
    Log.d(TAG, "stop");
    ThreadUtils.checkIsOnMainThread();
    if (amState != AudioManagerState.RUNNING) {
      Log.e(TAG, "Trying to stop AudioManager in incorrect state: " + amState);
      return;
    }
    amState = AudioManagerState.UNINITIALIZED;

    unregisterReceiver(wiredHeadsetReceiver);

    bluetoothManager.stop();

    setSpeakerphoneOn(savedIsSpeakerPhoneOn);
    setMicrophoneMute(savedIsMicrophoneMute);
    audioManager.setMode(savedAudioMode);

    audioManager.abandonAudioFocus(audioFocusChangeListener);
    audioFocusChangeListener = null;
    Log.d(TAG, "Abandoned audio focus for VOICE_CALL streams");

    if (proximitySensor != null) {
      proximitySensor.stop();
      proximitySensor = null;
    }

    audioManagerEvents = null;
    Log.d(TAG, "AudioManager stopped");
  }

  private void setAudioDeviceInternal(AudioDevice device) {
    Log.d(TAG, "setAudioDeviceInternal(device=" + device + ")");
    AppRTCUtils.assertIsTrue(audioDevices.contains(device));

    switch (device) {
      case SPEAKER_PHONE:
        setSpeakerphoneOn(true);
        break;
      case EARPIECE:
        setSpeakerphoneOn(false);
        break;
      case WIRED_HEADSET:
        setSpeakerphoneOn(false);
        break;
      case BLUETOOTH:
        setSpeakerphoneOn(false);
        break;
      default:
        Log.e(TAG, "Invalid audio device selection");
        break;
    }
    selectedAudioDevice = device;
  }

  public void setDefaultAudioDevice(AudioDevice defaultDevice) {
    ThreadUtils.checkIsOnMainThread();
    switch (defaultDevice) {
      case SPEAKER_PHONE:
        defaultAudioDevice = defaultDevice;
        break;
      case EARPIECE:
        if (hasEarpiece()) {
          defaultAudioDevice = defaultDevice;
        } else {
          defaultAudioDevice = AudioDevice.SPEAKER_PHONE;
        }
        break;
      default:
        Log.e(TAG, "Invalid default audio device selection");
        break;
    }
    Log.d(TAG, "setDefaultAudioDevice(device=" + defaultAudioDevice + ")");
    updateAudioDeviceState();
  }

  public void selectAudioDevice(AudioDevice device) {
    ThreadUtils.checkIsOnMainThread();
    if (!audioDevices.contains(device)) {
      Log.e(TAG, "Can not select " + device + " from available " + audioDevices);
    }
    userSelectedAudioDevice = device;
    updateAudioDeviceState();
  }

  public Set<AudioDevice> getAudioDevices() {
    ThreadUtils.checkIsOnMainThread();
    return Collections.unmodifiableSet(new HashSet<>(audioDevices));
  }

  public AudioDevice getSelectedAudioDevice() {
    ThreadUtils.checkIsOnMainThread();
    return selectedAudioDevice;
  }

  private void registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
    apprtcContext.registerReceiver(receiver, filter);
  }

  private void unregisterReceiver(BroadcastReceiver receiver) {
    apprtcContext.unregisterReceiver(receiver);
  }

  private void setSpeakerphoneOn(boolean on) {
    boolean wasOn = audioManager.isSpeakerphoneOn();
    if (wasOn == on) {
      return;
    }
    audioManager.setSpeakerphoneOn(on);
  }

  private void setMicrophoneMute(boolean on) {
    boolean wasMuted = audioManager.isMicrophoneMute();
    if (wasMuted == on) {
      return;
    }
    audioManager.setMicrophoneMute(on);
  }

  private boolean hasEarpiece() {
    return apprtcContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
  }

  @Deprecated
  private boolean hasWiredHeadset() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      return audioManager.isWiredHeadsetOn();
    } else {
      final AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL);
      for (AudioDeviceInfo device : devices) {
        final int type = device.getType();
        if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
          Log.d(TAG, "hasWiredHeadset: found wired headset");
          return true;
        } else if (type == AudioDeviceInfo.TYPE_USB_DEVICE) {
          Log.d(TAG, "hasWiredHeadset: found USB audio device");
          return true;
        }
      }
      return false;
    }
  }

  public void updateAudioDeviceState() {
    ThreadUtils.checkIsOnMainThread();
    Log.d(TAG, "--- updateAudioDeviceState: "
            + "wired headset=" + hasWiredHeadset + ", "
            + "BT state=" + bluetoothManager.getState());
    Log.d(TAG, "Device status: "
            + "available=" + audioDevices + ", "
            + "selected=" + selectedAudioDevice + ", "
            + "user selected=" + userSelectedAudioDevice);

    if (bluetoothManager.getState() == AppRTCBluetoothManager.State.HEADSET_AVAILABLE
        || bluetoothManager.getState() == AppRTCBluetoothManager.State.HEADSET_UNAVAILABLE
        || bluetoothManager.getState() == AppRTCBluetoothManager.State.SCO_DISCONNECTING) {
      bluetoothManager.updateDevice();
    }

    Set<AudioDevice> newAudioDevices = new HashSet<>();

    if (bluetoothManager.getState() == AppRTCBluetoothManager.State.SCO_CONNECTED
        || bluetoothManager.getState() == AppRTCBluetoothManager.State.SCO_CONNECTING
        || bluetoothManager.getState() == AppRTCBluetoothManager.State.HEADSET_AVAILABLE) {
      newAudioDevices.add(AudioDevice.BLUETOOTH);
    }

    if (hasWiredHeadset) {
      newAudioDevices.add(AudioDevice.WIRED_HEADSET);
    } else {
      newAudioDevices.add(AudioDevice.SPEAKER_PHONE);
      if (hasEarpiece()) {
        newAudioDevices.add(AudioDevice.EARPIECE);
      }
    }
    boolean audioDeviceSetUpdated = !audioDevices.equals(newAudioDevices);
    audioDevices = newAudioDevices;
    if (bluetoothManager.getState() == AppRTCBluetoothManager.State.HEADSET_UNAVAILABLE
        && userSelectedAudioDevice == AudioDevice.BLUETOOTH) {
      userSelectedAudioDevice = AudioDevice.NONE;
    }
    if (hasWiredHeadset && userSelectedAudioDevice == AudioDevice.SPEAKER_PHONE) {
      userSelectedAudioDevice = AudioDevice.WIRED_HEADSET;
    }
    if (!hasWiredHeadset && userSelectedAudioDevice == AudioDevice.WIRED_HEADSET) {
      userSelectedAudioDevice = AudioDevice.SPEAKER_PHONE;
    }

    boolean needBluetoothAudioStart =
        bluetoothManager.getState() == AppRTCBluetoothManager.State.HEADSET_AVAILABLE
        && (userSelectedAudioDevice == AudioDevice.NONE
               || userSelectedAudioDevice == AudioDevice.BLUETOOTH);

    boolean needBluetoothAudioStop =
        (bluetoothManager.getState() == AppRTCBluetoothManager.State.SCO_CONNECTED
            || bluetoothManager.getState() == AppRTCBluetoothManager.State.SCO_CONNECTING)
        && (userSelectedAudioDevice != AudioDevice.NONE
               && userSelectedAudioDevice != AudioDevice.BLUETOOTH);

    if (bluetoothManager.getState() == AppRTCBluetoothManager.State.HEADSET_AVAILABLE
        || bluetoothManager.getState() == AppRTCBluetoothManager.State.SCO_CONNECTING
        || bluetoothManager.getState() == AppRTCBluetoothManager.State.SCO_CONNECTED) {
      Log.d(TAG, "Need BT audio: start=" + needBluetoothAudioStart + ", "
              + "stop=" + needBluetoothAudioStop + ", "
              + "BT state=" + bluetoothManager.getState());
    }

    if (needBluetoothAudioStop) {
      bluetoothManager.stopScoAudio();
      bluetoothManager.updateDevice();
    }

    if (needBluetoothAudioStart && !needBluetoothAudioStop) {
      if (!bluetoothManager.startScoAudio()) {
        audioDevices.remove(AudioDevice.BLUETOOTH);
        audioDeviceSetUpdated = true;
      }
    }

    final AudioDevice newAudioDevice;

    if (bluetoothManager.getState() == AppRTCBluetoothManager.State.SCO_CONNECTED) {
      newAudioDevice = AudioDevice.BLUETOOTH;
    } else if (hasWiredHeadset) {
      newAudioDevice = AudioDevice.WIRED_HEADSET;
    } else {
      newAudioDevice = defaultAudioDevice;
    }
    if (newAudioDevice != selectedAudioDevice || audioDeviceSetUpdated) {
      setAudioDeviceInternal(newAudioDevice);
      Log.d(TAG, "New device status: "
              + "available=" + audioDevices + ", "
              + "selected=" + newAudioDevice);
      if (audioManagerEvents != null) {
        audioManagerEvents.onAudioDeviceChanged(selectedAudioDevice, audioDevices);
      }
    }
    Log.d(TAG, "--- updateAudioDeviceState done");
  }
}
