
package ru.ermolnik.medication.ui.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import org.webrtc.RendererCommon.ScalingType;
import ru.ermolnik.medication.R;
import ru.ermolnik.medication.ui.CaptureQualityController;
import ru.ermolnik.medication.ui.activity.CallActivity;
import ru.ermolnik.medication.rtc_client.VoteController;

/**
 * Fragment for call control.
 */
public class CallFragment extends Fragment {
  private TextView contactView;
  private ImageButton cameraSwitchButton;
  private ImageButton videoScalingButton;
  private ImageButton toggleMuteButton;
  private ImageButton disconnectButton;
  private TextView captureFormatText;
  private SeekBar captureFormatSlider;
  private OnCallEvents callEvents;
  private ScalingType scalingType;
  private boolean videoCallEnabled = true;

  public interface OnCallEvents {
    void onCallHangUp();
    void onCameraSwitch();
    void onVideoScalingSwitch(ScalingType scalingType);
    void onCaptureFormatChange(int width, int height, int framerate);
    boolean onToggleMic();
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View controlView = inflater.inflate(R.layout.fragment_call, container, false);

    // Create UI controls.
    contactView = controlView.findViewById(R.id.contact_name_call);
    disconnectButton = controlView.findViewById(R.id.button_call_disconnect);
    cameraSwitchButton = controlView.findViewById(R.id.button_call_switch_camera);
    videoScalingButton = controlView.findViewById(R.id.button_call_scaling_mode);
    toggleMuteButton = controlView.findViewById(R.id.button_call_toggle_mic);
    captureFormatText = controlView.findViewById(R.id.capture_format_text_call);
    captureFormatSlider = controlView.findViewById(R.id.capture_format_slider_call);

    disconnectButton.setOnClickListener(v -> {
      callEvents.onCallHangUp();
      startActivity(new Intent(getActivity(), VoteController.class));
    });

    // Add buttons click events.
    //disconnectButton.setOnClickListener(view -> callEvents.onCallHangUp());

    cameraSwitchButton.setOnClickListener(view -> callEvents.onCameraSwitch());

    videoScalingButton.setOnClickListener(view -> {
      if (scalingType == ScalingType.SCALE_ASPECT_FILL) {
        videoScalingButton.setBackgroundResource(R.drawable.ic_action_full_screen);
        scalingType = ScalingType.SCALE_ASPECT_FIT;
      } else {
        videoScalingButton.setBackgroundResource(R.drawable.ic_action_return_from_full_screen);
        scalingType = ScalingType.SCALE_ASPECT_FILL;
      }
      callEvents.onVideoScalingSwitch(scalingType);
    });
    scalingType = ScalingType.SCALE_ASPECT_FILL;

    toggleMuteButton.setOnClickListener(view -> {
      boolean enabled = callEvents.onToggleMic();
      toggleMuteButton.setAlpha(enabled ? 1.0f : 0.3f);
    });

    return controlView;
  }

  @Override
  public void onStart() {
    super.onStart();

    boolean captureSliderEnabled = false;
    Bundle args = getArguments();
    if (args != null) {
      String contactName = args.getString(CallActivity.EXTRA_ROOMID);
      contactView.setText(contactName);
      videoCallEnabled = args.getBoolean(CallActivity.EXTRA_VIDEO_CALL, true);
      captureSliderEnabled = videoCallEnabled
          && args.getBoolean(CallActivity.EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED, false);
    }
    if (!videoCallEnabled) {
      cameraSwitchButton.setVisibility(View.INVISIBLE);
    }
    if (captureSliderEnabled) {
      captureFormatSlider.setOnSeekBarChangeListener(
          new CaptureQualityController(captureFormatText, callEvents));
    } else {
      captureFormatText.setVisibility(View.GONE);
      captureFormatSlider.setVisibility(View.GONE);
    }
  }

  // TODO(sakal): Replace with onAttach(Context) once we only support API level 23+.
  @SuppressWarnings("deprecation")
  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    callEvents = (OnCallEvents) activity;
  }
}
