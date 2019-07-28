package ru.ermolnik.medication.util;

import android.os.Build;
import android.util.Log;

public final class AppRTCUtils {
  private AppRTCUtils() {}

  public static void assertIsTrue(boolean condition) {
    if (!condition) {
      throw new AssertionError("Expected condition to be true");
    }
  }

  public static String getThreadInfo() {
    return "@[name=" + Thread.currentThread().getName() + ", id=" + Thread.currentThread().getId()
        + "]";
  }

  public static void logDeviceInfo(String tag) {
    Log.d(tag, "Android SDK: " + Build.VERSION.SDK_INT + ", "
            + "Release: " + Build.VERSION.RELEASE + ", "
            + "Brand: " + Build.BRAND + ", "
            + "Device: " + Build.DEVICE + ", "
            + "Id: " + Build.ID + ", "
            + "Hardware: " + Build.HARDWARE + ", "
            + "Manufacturer: " + Build.MANUFACTURER + ", "
            + "Model: " + Build.MODEL + ", "
            + "Product: " + Build.PRODUCT);
  }
}
