package ru.ermolnik.medication.rtc_client;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import ru.ermolnik.medication.R;

public class VoteController extends Activity {

  private ImageView vote5, vote3;
  private ImageView feedBack;
  private EditText review;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_vote);

    vote3 = findViewById(R.id.vote_3);
    vote5 = findViewById(R.id.vote_5);
    feedBack = findViewById(R.id.feedback);
    review = findViewById(R.id.review);

    vote5.setOnClickListener(v -> {
          vote3.setVisibility(View.VISIBLE);
          vote5.setVisibility(View.GONE);
        }
    );

    vote3.setOnClickListener(v -> {
          vote5.setVisibility(View.VISIBLE);
          vote3.setVisibility(View.GONE);
        }
    );

    feedBack.setOnClickListener(v -> {
      Toast.makeText(this, "Отзыв учтен, вы получите обратную связь в ближайгее время", Toast.LENGTH_LONG).show();
      review.setText("");
      startActivity(new Intent(this, ListController.class));
    });

    findViewById(R.id.submit).setOnClickListener(v ->
        {
          if (vote3.getVisibility() == View.VISIBLE) {
            findViewById(R.id.submit).setVisibility(View.INVISIBLE);

            feedBack.setVisibility(View.VISIBLE);
            review.setVisibility(View.VISIBLE);
          }
          else {
            Toast.makeText(this, "Спасибо за оценку!", Toast.LENGTH_SHORT).show();
          }
        }
    );
  }
}
