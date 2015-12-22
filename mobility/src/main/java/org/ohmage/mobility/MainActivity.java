package org.ohmage.mobility;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.util.List;

import io.smalldatalab.omhclient.DSUClient;
import io.smalldatalab.omhclient.DSUDataPoint;


public class MainActivity extends AppCompatActivity {

    final static String TAG = MainActivity.class.getSimpleName();

    DSUClient mDSUClient;


    // Create the Handler object (on the main thread by default)
    Handler handler = new Handler();
    private Runnable runnableCode = new Runnable() {
        @Override
        public void run() {
            List<DSUDataPoint> lastRecord = DSUDataPoint.find(DSUDataPoint.class, null, null, null, "creation_date_time desc", "1");

            if (lastRecord.size() > 0) {
                try {
                    String timeStr = lastRecord.get(0).toJson().getJSONObject("header").getString("creation_date_time");
                    String elapsedTimeStr = (String) DateUtils.getRelativeTimeSpanString(ISO8601.toCalendar(timeStr).getTimeInMillis(), System.currentTimeMillis(), 0);

                    getSupportActionBar().setSubtitle("Last data point: " + elapsedTimeStr);
                } catch (Exception e) {
                    Log.e(TAG, "", e);
                }

            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Init a DSU client
        mDSUClient =
                new DSUClient(
                        DSUHelper.getUrl(this),
                        this.getString(R.string.dsu_client_id),
                        this.getString(R.string.dsu_client_secret),
                        this);

        AutoStartUp.repeatingAutoStart(this);


    }


    @Override
    public void onResume() {
        super.onResume();
        // show LoginActivity if the user has not sign in
        if (!mDSUClient.isSignedIn()) {
            Intent mainActivityIntent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(mainActivityIntent);
        }

        // Define the code block to be executed

        handler.post(runnableCode);
        // force syncing the Mobility data everytime when the user turn on the app
        mDSUClient.forceSync();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_sign_out:
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            mDSUClient.blockingSignOut();
                        } catch (Exception e) {
                            Log.e(TAG, "Logout error", e);
                        }
                        Intent mainActivityIntent = new Intent(MainActivity.this, LoginActivity.class);
                        startActivity(mainActivityIntent);

                    }
                }.start();
                return true;
            case R.id.sync_data:
                mDSUClient.forceSync();
                Toast.makeText(this, "Start uploading data.", Toast.LENGTH_SHORT).show();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }



}
