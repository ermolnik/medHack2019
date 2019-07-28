package ru.ermolnik.medication.util;

import android.media.AudioFormat;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.util.Log;

import org.webrtc.audio.JavaAudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule.SamplesReadyCallback;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;


public class RecordedAudioToFileController implements SamplesReadyCallback {
  private static final String TAG = "RecordedAudioToFile";
  private static final long MAX_FILE_SIZE_IN_BYTES = 58348800L;

  private final Object lock = new Object();
  private final ExecutorService executor;
  @Nullable private OutputStream rawAudioFileOutputStream;
  private boolean isRunning;
  private long fileSizeInBytes;

  public RecordedAudioToFileController(ExecutorService executor) {
    Log.d(TAG, "ctor");
    this.executor = executor;
  }

  public boolean start() {
    Log.d(TAG, "start");
    if (!isExternalStorageWritable()) {
      Log.e(TAG, "Writing to external media is not possible");
      return false;
    }
    synchronized (lock) {
      isRunning = true;
    }
    return true;
  }

  public void stop() {
    Log.d(TAG, "stop");
    synchronized (lock) {
      isRunning = false;
      if (rawAudioFileOutputStream != null) {
        try {
          rawAudioFileOutputStream.close();
        } catch (IOException e) {
          Log.e(TAG, "Failed to close file with saved input audio: " + e);
        }
        rawAudioFileOutputStream = null;
      }
      fileSizeInBytes = 0;
    }
  }

  private boolean isExternalStorageWritable() {
    String state = Environment.getExternalStorageState();
    if (Environment.MEDIA_MOUNTED.equals(state)) {
      return true;
    }
    return false;
  }

  private void openRawAudioOutputFile(int sampleRate, int channelCount) {
    final String fileName = Environment.getExternalStorageDirectory().getPath() + File.separator
        + "recorded_audio_16bits_" + String.valueOf(sampleRate) + "Hz"
        + ((channelCount == 1) ? "_mono" : "_stereo") + ".pcm";
    final File outputFile = new File(fileName);
    try {
      rawAudioFileOutputStream = new FileOutputStream(outputFile);
    } catch (FileNotFoundException e) {
      Log.e(TAG, "Failed to open audio output file: " + e.getMessage());
    }
    Log.d(TAG, "Opened file for recording: " + fileName);
  }

  @Override
  public void onWebRtcAudioRecordSamplesReady(JavaAudioDeviceModule.AudioSamples samples) {
    if (samples.getAudioFormat() != AudioFormat.ENCODING_PCM_16BIT) {
      Log.e(TAG, "Invalid audio format");
      return;
    }
    synchronized (lock) {
      if (!isRunning) {
        return;
      }
      if (rawAudioFileOutputStream == null) {
        openRawAudioOutputFile(samples.getSampleRate(), samples.getChannelCount());
        fileSizeInBytes = 0;
      }
    }
    executor.execute(() -> {
      if (rawAudioFileOutputStream != null) {
        try {
          if (fileSizeInBytes < MAX_FILE_SIZE_IN_BYTES) {
            rawAudioFileOutputStream.write(samples.getData());
            fileSizeInBytes += samples.getData().length;
          }
        } catch (IOException e) {
          Log.e(TAG, "Failed to write audio to file: " + e.getMessage());
        }
      }
    });
  }
}
