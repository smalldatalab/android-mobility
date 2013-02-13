package org.ohmage.mobility;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.ohmage.logprobe.Log;

public class SampleReceiver extends BroadcastReceiver
{
	private final static String TAG = "SampleReceiver";
	
	@Override
	public void onReceive(Context context, Intent intent)
	{
		Log.v(TAG, "Start mobility service");
		// Old way
//		context.startService(new Intent(context, ClassifierService.class));
		// wakeful intent service
		WakefulIntentService.sendWakefulWork(context, ClassifierService.class);
	}

}
