package ru.ermolnik.medication;

import android.app.Application;

public class App extends Application {

  public static String channel = "med334";
  public static boolean isNeedToConfig = false;

  @Override public void onCreate() {
    super.onCreate();
  }
}
