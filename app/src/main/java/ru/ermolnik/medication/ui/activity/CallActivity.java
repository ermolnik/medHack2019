package ru.ermolnik.medication.ui.activity;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.ImageView;
import android.widget.Toast;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.EglBase;
import org.webrtc.FileVideoCapturer;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon.ScalingType;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFileRenderer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
import ru.ermolnik.medication.R;
import ru.ermolnik.medication.exceptions.UnhandledExceptionHandler;
import ru.ermolnik.medication.rtc_client.AppRTCAudioManager;
import ru.ermolnik.medication.rtc_client.AppRTCClient;
import ru.ermolnik.medication.rtc_client.DirectRTCClient;
import ru.ermolnik.medication.rtc_client.PeerConnectionClient;
import ru.ermolnik.medication.rtc_client.VoteController;
import ru.ermolnik.medication.rtc_client.WebSocketRTCClient;
import ru.ermolnik.medication.ui.fragment.CallFragment;
import ru.ermolnik.medication.ui.fragment.HudFragment;
import ru.ermolnik.medication.util.CpuMonitor;

public class CallActivity extends Activity implements AppRTCClient.SignalingEvents,
                                                      PeerConnectionClient.PeerConnectionEvents,
    CallFragment.OnCallEvents {
  private static final String TAG = "CallRTCClient";

  public static final String EXTRA_ROOMID = "ru.ermolnik.medication.ROOMID";
  public static final String EXTRA_URLPARAMETERS = "ru.ermolnik.medication.URLPARAMETERS";
  public static final String EXTRA_LOOPBACK = "ru.ermolnik.medication.LOOPBACK";
  public static final String EXTRA_VIDEO_CALL = "ru.ermolnik.medication.VIDEO_CALL";
  public static final String EXTRA_SCREENCAPTURE = "ru.ermolnik.medication.SCREENCAPTURE";
  public static final String EXTRA_CAMERA2 = "ru.ermolnik.medication.CAMERA2";
  public static final String EXTRA_VIDEO_WIDTH = "ru.ermolnik.medication.VIDEO_WIDTH";
  public static final String EXTRA_VIDEO_HEIGHT = "ru.ermolnik.medication.VIDEO_HEIGHT";
  public static final String EXTRA_VIDEO_FPS = "ru.ermolnik.medication.VIDEO_FPS";
  public static final String EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED =
      "ru.ermolnik.medication.VIDEO_CAPTUREQUALITYSLIDER";
  public static final String EXTRA_VIDEO_BITRATE = "ru.ermolnik.medication.VIDEO_BITRATE";
  public static final String EXTRA_VIDEOCODEC = "ru.ermolnik.medication.VIDEOCODEC";
  public static final String EXTRA_HWCODEC_ENABLED = "ru.ermolnik.medication.HWCODEC";
  public static final String EXTRA_CAPTURETOTEXTURE_ENABLED = "ru.ermolnik.medication.CAPTURETOTEXTURE";
  public static final String EXTRA_FLEXFEC_ENABLED = "ru.ermolnik.medication.FLEXFEC";
  public static final String EXTRA_AUDIO_BITRATE = "ru.ermolnik.medication.AUDIO_BITRATE";
  public static final String EXTRA_AUDIOCODEC = "ru.ermolnik.medication.AUDIOCODEC";
  public static final String EXTRA_NOAUDIOPROCESSING_ENABLED =
      "ru.ermolnik.medication.NOAUDIOPROCESSING";
  public static final String EXTRA_AECDUMP_ENABLED = "ru.ermolnik.medication.AECDUMP";
  public static final String EXTRA_SAVE_INPUT_AUDIO_TO_FILE_ENABLED =
      "ru.ermolnik.medication.SAVE_INPUT_AUDIO_TO_FILE";
  public static final String EXTRA_OPENSLES_ENABLED = "ru.ermolnik.medication.OPENSLES";
  public static final String EXTRA_DISABLE_BUILT_IN_AEC = "ru.ermolnik.medication.DISABLE_BUILT_IN_AEC";
  public static final String EXTRA_DISABLE_BUILT_IN_AGC = "ru.ermolnik.medication.DISABLE_BUILT_IN_AGC";
  public static final String EXTRA_DISABLE_BUILT_IN_NS = "ru.ermolnik.medication.DISABLE_BUILT_IN_NS";
  public static final String EXTRA_DISABLE_WEBRTC_AGC_AND_HPF =
      "ru.ermolnik.medication.DISABLE_WEBRTC_GAIN_CONTROL";
  public static final String EXTRA_DISPLAY_HUD = "ru.ermolnik.medication.DISPLAY_HUD";
  public static final String EXTRA_TRACING = "ru.ermolnik.medication.TRACING";
  public static final String EXTRA_CMDLINE = "ru.ermolnik.medication.CMDLINE";
  public static final String EXTRA_RUNTIME = "ru.ermolnik.medication.RUNTIME";
  public static final String EXTRA_VIDEO_FILE_AS_CAMERA = "ru.ermolnik.medication.VIDEO_FILE_AS_CAMERA";
  public static final String EXTRA_SAVE_REMOTE_VIDEO_TO_FILE =
      "ru.ermolnik.medication.SAVE_REMOTE_VIDEO_TO_FILE";
  public static final String EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH =
      "ru.ermolnik.medication.SAVE_REMOTE_VIDEO_TO_FILE_WIDTH";
  public static final String EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT =
      "ru.ermolnik.medication.SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT";
  public static final String EXTRA_USE_VALUES_FROM_INTENT =
      "ru.ermolnik.medication.USE_VALUES_FROM_INTENT";
  public static final String EXTRA_DATA_CHANNEL_ENABLED = "ru.ermolnik.medication.DATA_CHANNEL_ENABLED";
  public static final String EXTRA_ORDERED = "ru.ermolnik.medication.ORDERED";
  public static final String EXTRA_MAX_RETRANSMITS_MS = "ru.ermolnik.medication.MAX_RETRANSMITS_MS";
  public static final String EXTRA_MAX_RETRANSMITS = "ru.ermolnik.medication.MAX_RETRANSMITS";
  public static final String EXTRA_PROTOCOL = "ru.ermolnik.medication.PROTOCOL";
  public static final String EXTRA_NEGOTIATED = "ru.ermolnik.medication.NEGOTIATED";
  public static final String EXTRA_ID = "ru.ermolnik.medication.ID";
  public static final String EXTRA_ENABLE_RTCEVENTLOG = "ru.ermolnik.medication.ENABLE_RTCEVENTLOG";

  private static final int CAPTURE_PERMISSION_REQUEST_CODE = 1;

  private ImageView hangOutCall;

  private static final String[] MANDATORY_PERMISSIONS = {"android.permission.MODIFY_AUDIO_SETTINGS",
      "android.permission.RECORD_AUDIO", "android.permission.INTERNET"};

  private static final int STAT_CALLBACK_PERIOD = 1000;

  private static class ProxyVideoSink implements VideoSink {
    private VideoSink target;

    @Override
    synchronized public void onFrame(VideoFrame frame) {
      if (target == null) {
        Logging.d(TAG, "Dropping frame in proxy because target is null.");
        return;
      }

      target.onFrame(frame);
    }

    synchronized public void setTarget(VideoSink target) {
      this.target = target;
    }
  }

  private final ProxyVideoSink remoteProxyRenderer = new ProxyVideoSink();
  private final ProxyVideoSink localProxyVideoSink = new ProxyVideoSink();
  @Nullable private PeerConnectionClient peerConnectionClient;
  @Nullable
  private AppRTCClient appRtcClient;
  @Nullable
  private AppRTCClient.SignalingParameters signalingParameters;
  @Nullable private AppRTCAudioManager audioManager;
  @Nullable
  private SurfaceViewRenderer pipRenderer;
  @Nullable
  private SurfaceViewRenderer fullscreenRenderer;
  @Nullable
  private VideoFileRenderer videoFileRenderer;
  private final List<VideoSink> remoteSinks = new ArrayList<>();
  private Toast logToast;
  private boolean commandLineRun;
  private boolean activityRunning;
  private AppRTCClient.RoomConnectionParameters roomConnectionParameters;
  @Nullable
  private PeerConnectionClient.PeerConnectionParameters peerConnectionParameters;
  private boolean connected;
  private boolean isError;
  private boolean callControlFragmentVisible = true;
  private long callStartedTimeMs;
  private boolean micEnabled = true;
  private boolean screencaptureEnabled;
  private static Intent mediaProjectionPermissionResultData;
  private static int mediaProjectionPermissionResultCode;
  private boolean isSwappedFeeds;

  private CallFragment callFragment;
  private HudFragment hudFragment;
  private CpuMonitor cpuMonitor;

  @Override
  @SuppressWarnings("deprecation")
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Thread.setDefaultUncaughtExceptionHandler(new UnhandledExceptionHandler(this));

    requestWindowFeature(Window.FEATURE_NO_TITLE);
    getWindow().addFlags(LayoutParams.FLAG_FULLSCREEN | LayoutParams.FLAG_KEEP_SCREEN_ON
        | LayoutParams.FLAG_SHOW_WHEN_LOCKED | LayoutParams.FLAG_TURN_SCREEN_ON);
    getWindow().getDecorView().setSystemUiVisibility(getSystemUiVisibility());
    setContentView(R.layout.activity_call);

    hangOutCall = findViewById(R.id.hangOutCall);

    hangOutCall.setOnClickListener(v ->{
      onCallHangUp();
      startActivity(new Intent(this, VoteController.class));
    });

    connected = false;
    signalingParameters = null;

    pipRenderer = findViewById(R.id.pip_video_view);
    fullscreenRenderer = findViewById(R.id.fullscreen_video_view);
    callFragment = new CallFragment();
    hudFragment = new HudFragment();

    View.OnClickListener listener = view -> toggleCallControlFragmentVisibility();

    pipRenderer.setOnClickListener(view -> setSwappedFeeds(!isSwappedFeeds));

    fullscreenRenderer.setOnClickListener(listener);
    remoteSinks.add(remoteProxyRenderer);

    final Intent intent = getIntent();
    final EglBase eglBase = EglBase.create();

    pipRenderer.init(eglBase.getEglBaseContext(), null);
    pipRenderer.setScalingType(ScalingType.SCALE_ASPECT_FIT);
    String saveRemoteVideoToFile = intent.getStringExtra(EXTRA_SAVE_REMOTE_VIDEO_TO_FILE);

    if (saveRemoteVideoToFile != null) {
      int videoOutWidth = intent.getIntExtra(EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH, 0);
      int videoOutHeight = intent.getIntExtra(EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT, 0);
      try {
        videoFileRenderer = new VideoFileRenderer(
            saveRemoteVideoToFile, videoOutWidth, videoOutHeight, eglBase.getEglBaseContext());
        remoteSinks.add(videoFileRenderer);
      } catch (IOException e) {
        throw new RuntimeException(
            "Failed to open video file for output: " + saveRemoteVideoToFile, e);
      }
    }
    fullscreenRenderer.init(eglBase.getEglBaseContext(), null);
    fullscreenRenderer.setScalingType(ScalingType.SCALE_ASPECT_FILL);

    pipRenderer.setZOrderMediaOverlay(true);
    pipRenderer.setEnableHardwareScaler(true /* enabled */);
    fullscreenRenderer.setEnableHardwareScaler(false /* enabled */);
    setSwappedFeeds(true /* isSwappedFeeds */);

    for (String permission : MANDATORY_PERMISSIONS) {
      if (checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
        logAndToast("Permission " + permission + " is not granted");
        setResult(RESULT_CANCELED);
        finish();
        return;
      }
    }

    Uri roomUri = intent.getData();
    if (roomUri == null) {
      logAndToast(getString(R.string.missing_url));
      Log.e(TAG, "Didn't get any URL in intent!");
      setResult(RESULT_CANCELED);
      finish();
      return;
    }

    String roomId = intent.getStringExtra(EXTRA_ROOMID);
    Log.d(TAG, "Room ID: " + roomId);
    if (roomId == null || roomId.length() == 0) {
      logAndToast(getString(R.string.missing_url));
      Log.e(TAG, "Incorrect room ID in intent!");
      setResult(RESULT_CANCELED);
      finish();
      return;
    }


    boolean loopback = intent.getBooleanExtra(EXTRA_LOOPBACK, false);
    boolean tracing = intent.getBooleanExtra(EXTRA_TRACING, false);

    int videoWidth = intent.getIntExtra(EXTRA_VIDEO_WIDTH, 0);
    int videoHeight = intent.getIntExtra(EXTRA_VIDEO_HEIGHT, 0);

    screencaptureEnabled = intent.getBooleanExtra(EXTRA_SCREENCAPTURE, false);
    // If capturing format is not specified for screencapture, use screen resolution.
    if (screencaptureEnabled && videoWidth == 0 && videoHeight == 0) {
      DisplayMetrics displayMetrics = getDisplayMetrics();
      videoWidth = displayMetrics.widthPixels;
      videoHeight = displayMetrics.heightPixels;
    }
    PeerConnectionClient.DataChannelParameters dataChannelParameters = null;
    if (intent.getBooleanExtra(EXTRA_DATA_CHANNEL_ENABLED, false)) {
      dataChannelParameters = new PeerConnectionClient.DataChannelParameters(intent.getBooleanExtra(EXTRA_ORDERED, true),
          intent.getIntExtra(EXTRA_MAX_RETRANSMITS_MS, -1),
          intent.getIntExtra(EXTRA_MAX_RETRANSMITS, -1), intent.getStringExtra(EXTRA_PROTOCOL),
          intent.getBooleanExtra(EXTRA_NEGOTIATED, false), intent.getIntExtra(EXTRA_ID, -1));
    }
    peerConnectionParameters =
        new PeerConnectionClient.PeerConnectionParameters(intent.getBooleanExtra(EXTRA_VIDEO_CALL, true), loopback,
            tracing, videoWidth, videoHeight, intent.getIntExtra(EXTRA_VIDEO_FPS, 0),
            intent.getIntExtra(EXTRA_VIDEO_BITRATE, 0), intent.getStringExtra(EXTRA_VIDEOCODEC),
            intent.getBooleanExtra(EXTRA_HWCODEC_ENABLED, true),
            intent.getBooleanExtra(EXTRA_FLEXFEC_ENABLED, false),
            intent.getIntExtra(EXTRA_AUDIO_BITRATE, 0), intent.getStringExtra(EXTRA_AUDIOCODEC),
            intent.getBooleanExtra(EXTRA_NOAUDIOPROCESSING_ENABLED, false),
            intent.getBooleanExtra(EXTRA_AECDUMP_ENABLED, false),
            intent.getBooleanExtra(EXTRA_SAVE_INPUT_AUDIO_TO_FILE_ENABLED, false),
            intent.getBooleanExtra(EXTRA_OPENSLES_ENABLED, false),
            intent.getBooleanExtra(EXTRA_DISABLE_BUILT_IN_AEC, false),
            intent.getBooleanExtra(EXTRA_DISABLE_BUILT_IN_AGC, false),
            intent.getBooleanExtra(EXTRA_DISABLE_BUILT_IN_NS, false),
            intent.getBooleanExtra(EXTRA_DISABLE_WEBRTC_AGC_AND_HPF, false),
            intent.getBooleanExtra(EXTRA_ENABLE_RTCEVENTLOG, false), dataChannelParameters);
    commandLineRun = intent.getBooleanExtra(EXTRA_CMDLINE, false);
    int runTimeMs = intent.getIntExtra(EXTRA_RUNTIME, 0);

    Log.d(TAG, "VIDEO_FILE: '" + intent.getStringExtra(EXTRA_VIDEO_FILE_AS_CAMERA) + "'");

    if (loopback || !DirectRTCClient.IP_PATTERN.matcher(roomId).matches()) {
      appRtcClient = new WebSocketRTCClient(this);
    } else {
      Log.i(TAG, "Using DirectRTCClient because room name looks like an IP.");
      appRtcClient = new DirectRTCClient(this);
    }
    String urlParameters = intent.getStringExtra(EXTRA_URLPARAMETERS);
    roomConnectionParameters =
        new AppRTCClient.RoomConnectionParameters(roomUri.toString(), roomId, loopback, urlParameters);

    if (CpuMonitor.isSupported()) {
      cpuMonitor = new CpuMonitor(this);
      hudFragment.setCpuMonitor(cpuMonitor);
    }

    callFragment.setArguments(intent.getExtras());
    hudFragment.setArguments(intent.getExtras());
    FragmentTransaction ft = getFragmentManager().beginTransaction();
    ft.add(R.id.call_fragment_container, callFragment);
    ft.add(R.id.hud_fragment_container, hudFragment);
    ft.commit();

    if (commandLineRun && runTimeMs > 0) {
      (new Handler()).postDelayed(() -> disconnect(), runTimeMs);
    }

    peerConnectionClient = new PeerConnectionClient(
        getApplicationContext(), eglBase, peerConnectionParameters, CallActivity.this);
    PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
    if (loopback) {
      options.networkIgnoreMask = 0;
    }
    peerConnectionClient.createPeerConnectionFactory(options);

    if (screencaptureEnabled) {
      startScreenCapture();
    } else {
      startCall();
    }
  }

  @TargetApi(17)
  private DisplayMetrics getDisplayMetrics() {
    DisplayMetrics displayMetrics = new DisplayMetrics();
    WindowManager windowManager =
        (WindowManager) getApplication().getSystemService(Context.WINDOW_SERVICE);
    windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
    return displayMetrics;
  }

  @TargetApi(19)
  private static int getSystemUiVisibility() {
    int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
    }
    return flags;
  }

  @TargetApi(21)
  private void startScreenCapture() {
    MediaProjectionManager mediaProjectionManager =
        (MediaProjectionManager) getApplication().getSystemService(
            Context.MEDIA_PROJECTION_SERVICE);
    startActivityForResult(
        mediaProjectionManager.createScreenCaptureIntent(), CAPTURE_PERMISSION_REQUEST_CODE);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode != CAPTURE_PERMISSION_REQUEST_CODE)
      return;
    mediaProjectionPermissionResultCode = resultCode;
    mediaProjectionPermissionResultData = data;
    startCall();
  }

  private boolean useCamera2() {
    return Camera2Enumerator.isSupported(this) && getIntent().getBooleanExtra(EXTRA_CAMERA2, true);
  }

  private boolean captureToTexture() {
    return getIntent().getBooleanExtra(EXTRA_CAPTURETOTEXTURE_ENABLED, false);
  }

  private @Nullable VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
    final String[] deviceNames = enumerator.getDeviceNames();

    Logging.d(TAG, "Looking for front facing cameras.");
    for (String deviceName : deviceNames) {
      if (enumerator.isFrontFacing(deviceName)) {
        Logging.d(TAG, "Creating front facing camera capturer.");
        VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

        if (videoCapturer != null) {
          return videoCapturer;
        }
      }
    }

    Logging.d(TAG, "Looking for other cameras.");
    for (String deviceName : deviceNames) {
      if (!enumerator.isFrontFacing(deviceName)) {
        Logging.d(TAG, "Creating other camera capturer.");
        VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

        if (videoCapturer != null) {
          return videoCapturer;
        }
      }
    }

    return null;
  }

  @TargetApi(21)
  private @Nullable VideoCapturer createScreenCapturer() {
    if (mediaProjectionPermissionResultCode != Activity.RESULT_OK) {
      reportError("User didn't give permission to capture the screen.");
      return null;
    }
    return new ScreenCapturerAndroid(
        mediaProjectionPermissionResultData, new MediaProjection.Callback() {
      @Override
      public void onStop() {
        reportError("User revoked permission to capture the screen.");
      }
    });
  }

  @Override
  public void onStop() {
    super.onStop();
    activityRunning = false;
    if (peerConnectionClient != null && !screencaptureEnabled) {
      peerConnectionClient.stopVideoSource();
    }
    if (cpuMonitor != null) {
      cpuMonitor.pause();
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    activityRunning = true;
    // Video is not paused for screencapture. See onPause.
    if (peerConnectionClient != null && !screencaptureEnabled) {
      peerConnectionClient.startVideoSource();
    }
    if (cpuMonitor != null) {
      cpuMonitor.resume();
    }
  }

  @Override
  protected void onDestroy() {
    Thread.setDefaultUncaughtExceptionHandler(null);
    disconnect();
    if (logToast != null) {
      logToast.cancel();
    }
    activityRunning = false;
    super.onDestroy();
  }

  @Override
  public void onCallHangUp() {
    disconnect();
  }

  @Override
  public void onCameraSwitch() {
    if (peerConnectionClient != null) {
      peerConnectionClient.switchCamera();
    }
  }

  @Override
  public void onVideoScalingSwitch(ScalingType scalingType) {
    fullscreenRenderer.setScalingType(scalingType);
  }

  @Override
  public void onCaptureFormatChange(int width, int height, int framerate) {
    if (peerConnectionClient != null) {
      peerConnectionClient.changeCaptureFormat(width, height, framerate);
    }
  }

  @Override
  public boolean onToggleMic() {
    if (peerConnectionClient != null) {
      micEnabled = !micEnabled;
      peerConnectionClient.setAudioEnabled(micEnabled);
    }
    return micEnabled;
  }

  private void toggleCallControlFragmentVisibility() {
    if (!connected || !callFragment.isAdded()) {
      return;
    }
    callControlFragmentVisible = !callControlFragmentVisible;
    FragmentTransaction ft = getFragmentManager().beginTransaction();
    if (callControlFragmentVisible) {
      ft.show(callFragment);
      ft.show(hudFragment);
    } else {
      ft.hide(callFragment);
      ft.hide(hudFragment);
    }
    ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
    ft.commit();
  }

  private void startCall() {
    if (appRtcClient == null) {
      Log.e(TAG, "AppRTC client is not allocated for a call.");
      return;
    }
    callStartedTimeMs = System.currentTimeMillis();

    logAndToast(getString(R.string.connecting_to, roomConnectionParameters.roomUrl));
    appRtcClient.connectToRoom(roomConnectionParameters);

    audioManager = AppRTCAudioManager.create(getApplicationContext());
    Log.d(TAG, "Starting the audio manager...");
    audioManager.start((audioDevice, availableAudioDevices) -> onAudioManagerDevicesChanged(audioDevice, availableAudioDevices));
  }

  private void callConnected() {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    Log.i(TAG, "Call connected: delay=" + delta + "ms");
    if (peerConnectionClient == null || isError) {
      Log.w(TAG, "Call is connected in closed or error state");
      return;
    }
    peerConnectionClient.enableStatsEvents(true, STAT_CALLBACK_PERIOD);
    setSwappedFeeds(false /* isSwappedFeeds */);
  }

  private void onAudioManagerDevicesChanged(
      final AppRTCAudioManager.AudioDevice device, final Set<AppRTCAudioManager.AudioDevice> availableDevices) {
    Log.d(TAG, "onAudioManagerDevicesChanged: " + availableDevices + ", "
            + "selected: " + device);
  }

  private void disconnect() {
    activityRunning = false;
    remoteProxyRenderer.setTarget(null);
    localProxyVideoSink.setTarget(null);
    if (appRtcClient != null) {
      appRtcClient.disconnectFromRoom();
      appRtcClient = null;
    }
    if (pipRenderer != null) {
      pipRenderer.release();
      pipRenderer = null;
    }
    if (videoFileRenderer != null) {
      videoFileRenderer.release();
      videoFileRenderer = null;
    }
    if (fullscreenRenderer != null) {
      fullscreenRenderer.release();
      fullscreenRenderer = null;
    }
    if (peerConnectionClient != null) {
      peerConnectionClient.close();
      peerConnectionClient = null;
    }
    if (audioManager != null) {
      audioManager.stop();
      audioManager = null;
    }
    if (connected && !isError) {
      setResult(RESULT_OK);
    } else {
      setResult(RESULT_CANCELED);
    }
    finish();
  }

  private void disconnectWithErrorMessage(final String errorMessage) {
    if (commandLineRun || !activityRunning) {
      Log.e(TAG, "Critical error: " + errorMessage);
      disconnect();
    } else {
      new AlertDialog.Builder(this)
          .setTitle(getText(R.string.channel_error_title))
          .setMessage(errorMessage)
          .setCancelable(false)
          .setNeutralButton(R.string.ok,
              (dialog, id) -> {
                dialog.cancel();
                disconnect();
              })
          .create()
          .show();
    }
  }

  private void logAndToast(String msg) {
    Log.d(TAG, msg);
    if (logToast != null) {
      logToast.cancel();
    }
    logToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
    //logToast.show();
  }

  private void reportError(final String description) {
    runOnUiThread(() -> {
      if (!isError) {
        isError = true;
        disconnectWithErrorMessage(description);
      }
    });
  }

  private @Nullable VideoCapturer createVideoCapturer() {
    final VideoCapturer videoCapturer;
    String videoFileAsCamera = getIntent().getStringExtra(EXTRA_VIDEO_FILE_AS_CAMERA);
    if (videoFileAsCamera != null) {
      try {
        videoCapturer = new FileVideoCapturer(videoFileAsCamera);
      } catch (IOException e) {
        reportError("Failed to open video file for emulated camera");
        return null;
      }
    } else if (screencaptureEnabled) {
      return createScreenCapturer();
    } else if (useCamera2()) {
      if (!captureToTexture()) {
        reportError(getString(R.string.camera2_texture_only_error));
        return null;
      }

      Logging.d(TAG, "Creating capturer using camera2 API.");
      videoCapturer = createCameraCapturer(new Camera2Enumerator(this));
    } else {
      Logging.d(TAG, "Creating capturer using camera1 API.");
      videoCapturer = createCameraCapturer(new Camera1Enumerator(captureToTexture()));
    }
    if (videoCapturer == null) {
      reportError("Failed to open camera");
      return null;
    }
    return videoCapturer;
  }

  private void setSwappedFeeds(boolean isSwappedFeeds) {
    Logging.d(TAG, "setSwappedFeeds: " + isSwappedFeeds);
    this.isSwappedFeeds = isSwappedFeeds;
    localProxyVideoSink.setTarget(isSwappedFeeds ? fullscreenRenderer : pipRenderer);
    remoteProxyRenderer.setTarget(isSwappedFeeds ? pipRenderer : fullscreenRenderer);
    fullscreenRenderer.setMirror(isSwappedFeeds);
    pipRenderer.setMirror(!isSwappedFeeds);
  }

  private void onConnectedToRoomInternal(final AppRTCClient.SignalingParameters params) {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;

    signalingParameters = params;
    logAndToast("Creating peer connection, delay=" + delta + "ms");
    VideoCapturer videoCapturer = null;
    if (peerConnectionParameters.videoCallEnabled) {
      videoCapturer = createVideoCapturer();
    }
    peerConnectionClient.createPeerConnection(
        localProxyVideoSink, remoteSinks, videoCapturer, signalingParameters);

    if (signalingParameters.initiator) {
      logAndToast("Creating OFFER...");
      peerConnectionClient.createOffer();
    } else {
      if (params.offerSdp != null) {
        peerConnectionClient.setRemoteDescription(params.offerSdp);
        logAndToast("Creating ANSWER...");
        peerConnectionClient.createAnswer();
      }
      if (params.iceCandidates != null) {
        for (IceCandidate iceCandidate : params.iceCandidates) {
          peerConnectionClient.addRemoteIceCandidate(iceCandidate);
        }
      }
    }
  }

  @Override
  public void onConnectedToRoom(final AppRTCClient.SignalingParameters params) {
    runOnUiThread(() -> onConnectedToRoomInternal(params));
  }

  @Override
  public void onRemoteDescription(final SessionDescription sdp) {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    runOnUiThread(() -> {
      if (peerConnectionClient == null) {
        Log.e(TAG, "Received remote SDP for non-initilized peer connection.");
        return;
      }
      logAndToast("Received remote " + sdp.type + ", delay=" + delta + "ms");
      peerConnectionClient.setRemoteDescription(sdp);
      if (!signalingParameters.initiator) {
        logAndToast("Creating ANSWER...");
        peerConnectionClient.createAnswer();
      }
    });
  }

  @Override
  public void onRemoteIceCandidate(final IceCandidate candidate) {
    runOnUiThread(() -> {
      if (peerConnectionClient == null) {
        Log.e(TAG, "Received ICE candidate for a non-initialized peer connection.");
        return;
      }
      peerConnectionClient.addRemoteIceCandidate(candidate);
    });
  }

  @Override
  public void onRemoteIceCandidatesRemoved(final IceCandidate[] candidates) {
    runOnUiThread(() -> {
      if (peerConnectionClient == null) {
        Log.e(TAG, "Received ICE candidate removals for a non-initialized peer connection.");
        return;
      }
      peerConnectionClient.removeRemoteIceCandidates(candidates);
    });
  }

  @Override
  public void onChannelClose() {
    runOnUiThread(() -> {
      logAndToast("Remote end hung up; dropping PeerConnection");
      disconnect();
    });
  }

  @Override
  public void onChannelError(final String description) {
    reportError(description);
  }

  @Override
  public void onLocalDescription(final SessionDescription sdp) {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    runOnUiThread(() -> {
      if (appRtcClient != null) {
        logAndToast("Sending " + sdp.type + ", delay=" + delta + "ms");
        if (signalingParameters.initiator) {
          appRtcClient.sendOfferSdp(sdp);
        } else {
          appRtcClient.sendAnswerSdp(sdp);
        }
      }
      if (peerConnectionParameters.videoMaxBitrate > 0) {
        Log.d(TAG, "Set video maximum bitrate: " + peerConnectionParameters.videoMaxBitrate);
        peerConnectionClient.setVideoMaxBitrate(peerConnectionParameters.videoMaxBitrate);
      }
    });
  }

  @Override
  public void onIceCandidate(final IceCandidate candidate) {
    runOnUiThread(() -> {
      if (appRtcClient != null) {
        appRtcClient.sendLocalIceCandidate(candidate);
      }
    });
  }

  @Override
  public void onIceCandidatesRemoved(final IceCandidate[] candidates) {
    runOnUiThread(() -> {
      if (appRtcClient != null) {
        appRtcClient.sendLocalIceCandidateRemovals(candidates);
      }
    });
  }

  @Override
  public void onIceConnected() {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    runOnUiThread(() -> logAndToast("ICE connected, delay=" + delta + "ms"));
  }

  @Override
  public void onIceDisconnected() {
    runOnUiThread(() -> logAndToast("ICE disconnected"));
  }

  @Override
  public void onConnected() {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    runOnUiThread(() -> {
      logAndToast("DTLS connected, delay=" + delta + "ms");
      connected = true;
      callConnected();
    });
  }

  @Override
  public void onDisconnected() {
    runOnUiThread(() -> {
      logAndToast("DTLS disconnected");
      connected = false;
      disconnect();
    });
  }

  @Override
  public void onPeerConnectionClosed() {}

  @Override
  public void onPeerConnectionStatsReady(final StatsReport[] reports) {
    runOnUiThread(() -> {
      if (!isError && connected) {
        hudFragment.updateEncoderStatistics(reports);
      }
    });
  }

  @Override
  public void onPeerConnectionError(final String description) {
    reportError(description);
  }
}
