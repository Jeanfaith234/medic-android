package org.medicmobile.webapp.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;
import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.Utils.createUseragentFrom;
import static org.medicmobile.webapp.mobile.Utils.startAppActivityChain;

public class StartupActivity extends Activity {
	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if(DEBUG) trace(this, "Starting...");

		configureAndStartNextActivity();

		if(LockScreen.isCodeSet(this)) {
			LockScreen.showFrom(this);
		}
	}

	private void configureAndStartNextActivity() {
		configureHttpUseragent();

		if(hasEnoughFreeSpace()) {
			if(isGpsAppInstalled("com.lg.gis")) {
				startAppActivityChain(this);
			}else {
				new AlertDialog.Builder(this)
						.setTitle("GIS app is not installed")
						.setMessage("Please install GIS app ")
						.setCancelable(false)
						.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								startAppActivityChain(StartupActivity.this);
								dialog.dismiss();
							}
						})
						.setIcon(R.mipmap.ic_launcher)
						.show();
			}
		}
		else {
			Intent i = new Intent(this, FreeSpaceWarningActivity.class);
			startActivity(i);
			finish();
		}
	}

	private boolean hasEnoughFreeSpace() {
		long freeSpace = getFilesDir().getFreeSpace();

		return freeSpace > FreeSpaceWarningActivity.MINIMUM_SPACE;
	}

	private void configureHttpUseragent() {
		String current = System.getProperty("http.agent");
		System.setProperty("http.agent", createUseragentFrom(current));
	}

	private boolean isGpsAppInstalled(String packageName) {
		try {
			PackageManager pm = this.getPackageManager();
			pm.getPackageInfo(packageName, 0);
			return true;
		} catch (PackageManager.NameNotFoundException e) {
			return false;
		}
	}
}
