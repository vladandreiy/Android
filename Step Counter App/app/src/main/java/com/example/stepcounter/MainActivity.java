package com.example.stepcounter;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class MainActivity extends AppCompatActivity {
    private int stepCounter = 0;
    private int lastStep = 0;
    private boolean showedGoalReach = false;
    private int stepGoal = 500;
    private Handler threadHandler;
    private boolean buttonPressed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        SharedPreferences sharedPreferences = getSharedPreferences("UserData", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        Button button = findViewById(R.id.buttonGoal);
        EditText setGoalET = findViewById(R.id.editText);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (buttonPressed == false) {
                    setGoalET.setVisibility(View.VISIBLE);
                    buttonPressed = true;
                } else {
                    editor.putInt("StepGoal", Integer.parseInt(setGoalET.getText().toString()));
                    editor.apply();
                    InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    setGoalET.setVisibility(View.GONE);
                    setGoalET.getText().clear();
                    updateView();
                    buttonPressed = false;
                }

            }
        });

        threadHandler = new Handler();
        updateUserInterface();
    }

    private void updateView() {
        SharedPreferences sharedPreferences = getSharedPreferences("UserData", MODE_PRIVATE);
        stepGoal = sharedPreferences.getInt("StepGoal", 500);
        TextView stepCountStr = this.findViewById(R.id.maintv1);
        stepCountStr.setText("Step Count: " + stepCounter);
        TextView progressText = (TextView) this.findViewById(R.id.maintv2);
        progressText.setText("Step Goal: " + stepGoal + ".\nProgress: " + stepCounter + " / " + stepGoal);
        int lastProgress = (int) ((lastStep / (1.0 * stepGoal)) * 100);
        int currProgress = (int) ((stepCounter / (1.0 * stepGoal)) * 100);
        if (currProgress > 100)
            currProgress = 100;
        ProgressBar progressBar = this.findViewById(R.id.progressBar);
        ObjectAnimator animation = ObjectAnimator.ofInt(progressBar, "progress", lastProgress, currProgress);
        animation.setDuration(2000);
        animation.setInterpolator(new DecelerateInterpolator());
        animation.start();
        if (DataViewActivity.stepCounter > stepCounter) {
            stepCounter = DataViewActivity.stepCounter;
            if (stepCounter >= stepGoal && !showedGoalReach) {
                showedGoalReach = true;
                Context context = getApplicationContext();
                CharSequence text = "Good Job! You've reached your goal!";
                int duration = Toast.LENGTH_LONG;
                Toast toast = Toast.makeText(context, text, duration);
                toast.show();
            }
            lastStep = stepCounter;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        final Context context = this;
        if (id == R.id.action_data) {
            Intent intent = new Intent(context, DataViewActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRepeatingTask();
    }

    Runnable mStatusChecker = new Runnable() {
        @Override
        public void run() {
            try {
                updateView();
            } finally {
                threadHandler.postDelayed(mStatusChecker, 500);
            }
        }
    };

    void updateUserInterface() {
        mStatusChecker.run();
    }

    void stopRepeatingTask() {
        threadHandler.removeCallbacks(mStatusChecker);
    }

    public void updateView(View view) {
        updateView();
    }
}
