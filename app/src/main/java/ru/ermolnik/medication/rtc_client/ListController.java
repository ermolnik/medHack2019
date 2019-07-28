package ru.ermolnik.medication.rtc_client;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;
import ru.ermolnik.medication.App;
import ru.ermolnik.medication.R;
import ru.ermolnik.medication.ui.activity.ConnectActivity;

public class ListController extends Activity {

  ImageView goVideo, doctorNotAvailable;


  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_list);

    goVideo = findViewById(R.id.goVideo);
    doctorNotAvailable = findViewById(R.id.doctorNotAvailable);

    goVideo.setOnClickListener(v -> {
      startActivity(new Intent(ListController.this, ConnectActivity.class));
      Toast.makeText(this, "Подключаемся...", Toast.LENGTH_LONG).show();
    });

    goVideo.setOnLongClickListener(v -> {
      startActivity(new Intent(ListController.this, ConnectActivity.class));
      App.isNeedToConfig = true;
      return true;
    });

    doctorNotAvailable.setOnClickListener(v -> {
      Toast.makeText(this, "Доктор не дотсупен, вернитесь к назначенному времени", Toast.LENGTH_LONG).show();
    });
  }
}
