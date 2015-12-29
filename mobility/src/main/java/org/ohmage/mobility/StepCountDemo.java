package org.ohmage.mobility;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import java.text.DecimalFormat;

public class StepCountDemo extends AppCompatActivity implements TextToSpeech.OnInitListener {
    private final static int TTS_CHECK = 100;
    long resetFromCount = 0;
    long curTotal = 0;
    private TextToSpeech mTts;
    private StepCounter counter;

    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        if (requestCode == TTS_CHECK) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                // success, create the TTS instance
                mTts = new TextToSpeech(this, this);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_step_count_demo);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        HandlerThread mHandlerThread = new HandlerThread("sensorThread");
        mHandlerThread.start();
        Handler handler = new Handler(mHandlerThread.getLooper());

        final TextView countView = (TextView) findViewById(R.id.countView);
        counter = new StepCounter(this, handler) {
            @Override
            void onDetectStep(final long total) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        curTotal = total;
                        countView.setText(new DecimalFormat("#,###").format(total - resetFromCount));
                        if (mTts != null) {
                            String text = String.valueOf(total - resetFromCount);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                mTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, text);
                            }
                        }
                    }
                });
            }
        };

        final Button resetCountButton = (Button) findViewById(R.id.resetCountButton);
        resetCountButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetFromCount = curTotal;
                countView.setText(new DecimalFormat("#,###").format(0));
            }
        });

        Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkIntent, TTS_CHECK);

    }

    @Override
    public void onResume() {
        super.onResume();
        counter.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        counter.pause();
    }

    @Override
    public void onInit(int status) {

    }
}
