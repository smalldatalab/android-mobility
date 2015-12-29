package org.ohmage.mobility;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.text.ParseException;
import java.util.List;

import io.smalldatalab.omhclient.DSUClient;
import io.smalldatalab.omhclient.DSUDataPoint;
import io.smalldatalab.omhclient.ISO8601;


public class MainActivity extends AppCompatActivity {

    final static String TAG = MainActivity.class.getSimpleName();
    DSUClient mDSUClient;

    private void checkSignIn() {
        // show LoginActivity if the user has not sign in
        if (!mDSUClient.isSignedIn()) {
            Intent mainActivityIntent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(mainActivityIntent);
            this.finish();
        }
    }
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
        checkSignIn();

    }

    @Override
    public void onStart() {
        super.onStart();
        // show LoginActivity if the user has not sign in
        checkSignIn();
        AutoStartUp.repeatingAutoStart(this);

        List<DSUDataPoint> lastRecord = DSUDataPoint.find(DSUDataPoint.class, null, null, null, "creation_date_time desc", "1");

        if (lastRecord.size() > 0) {
            String timeStr = lastRecord.get(0).getCreationDateTime();
            try {

                String elapsedTimeStr = (String) DateUtils.getRelativeTimeSpanString(ISO8601.toCalendar(timeStr).getTimeInMillis(), System.currentTimeMillis(), 0);
                ActionBar actionBar = getSupportActionBar();
                if (actionBar != null) {
                    actionBar.setSubtitle("Last data point: " + elapsedTimeStr);
                }
            } catch (ParseException e) {
                Log.e(TAG, "Cannot parse DateTime" + timeStr, e);
            }

        }

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
            case R.id.show_step_count_demo:
                startActivity(new Intent(this, StepCountDemo.class));
                return true;
        }

        return super.onOptionsItemSelected(item);
    }


}
