package ru.ermolnik.medication.exceptions;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.util.Log;
import android.util.TypedValue;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.PrintWriter;
import java.io.StringWriter;

public class UnhandledExceptionHandler implements Thread.UncaughtExceptionHandler {
  private static final String TAG = "AppRTCMobileActivity";
  private final Activity activity;

  public UnhandledExceptionHandler(final Activity activity) {
    this.activity = activity;
  }

  @Override
  public void uncaughtException(Thread unusedThread, final Throwable e) {
    activity.runOnUiThread(() -> {
      String title = "Fatal error: " + getTopLevelCauseMessage(e);
      String msg = getRecursiveStackTrace(e);
      TextView errorView = new TextView(activity);
      errorView.setText(msg);
      errorView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8);
      ScrollView scrollingContainer = new ScrollView(activity);
      scrollingContainer.addView(errorView);
      Log.e(TAG, title + "\n\n" + msg);
      DialogInterface.OnClickListener listener = (dialog, which) -> {
        dialog.dismiss();
        System.exit(1);
      };
      AlertDialog.Builder builder = new AlertDialog.Builder(activity);
      builder.setTitle(title)
          .setView(scrollingContainer)
          .setPositiveButton("Exit", listener)
          .show();
    });
  }

  private static String getTopLevelCauseMessage(Throwable t) {
    Throwable topLevelCause = t;
    while (topLevelCause.getCause() != null) {
      topLevelCause = topLevelCause.getCause();
    }
    return topLevelCause.getMessage();
  }

  private static String getRecursiveStackTrace(Throwable t) {
    StringWriter writer = new StringWriter();
    t.printStackTrace(new PrintWriter(writer));
    return writer.toString();
  }
}
