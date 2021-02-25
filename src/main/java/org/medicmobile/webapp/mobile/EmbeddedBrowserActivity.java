package org.medicmobile.webapp.mobile;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import org.xwalk.core.XWalkPreferences;
import org.xwalk.core.XWalkResourceClient;

import java.util.ArrayList;
import java.util.List;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static java.lang.Boolean.parseBoolean;
import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;
import static org.medicmobile.webapp.mobile.BuildConfig.DISABLE_APP_URL_VALIDATION;
import static org.medicmobile.webapp.mobile.MedicLog.error;
import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.MedicLog.warn;
import static org.medicmobile.webapp.mobile.SimpleJsonClient2.redactUrl;
import static org.medicmobile.webapp.mobile.Utils.createUseragentFrom;

@SuppressWarnings({ "PMD.GodClass", "PMD.TooManyMethods" })
public class EmbeddedBrowserActivity extends LockableActivity {
	/** Any activity result with all 3 low bits set is _not_ a simprints result. */
	private static final int NON_SIMPRINTS_FLAGS = 0x7;
	static final int GRAB_PHOTO = (0 << 3) | NON_SIMPRINTS_FLAGS;
	static final int GRAB_MRDT_PHOTO = (1 << 3) | NON_SIMPRINTS_FLAGS;

	private final static int ACCESS_FINE_LOCATION_PERMISSION_REQUEST = (int)Math.random();

	private static final ValueCallback<String> IGNORE_RESULT = new ValueCallback<String>() {
		public void onReceiveValue(String result) { /* ignore */ }
	};

	private final ValueCallback<String> backButtonHandler = new ValueCallback<String>() {
		public void onReceiveValue(String result) {
			if(!"true".equals(result)) {
				EmbeddedBrowserActivity.this.moveTaskToBack(false);
			}
		}
	};

	private WebView container;
	private SettingsStore settings;
	private String appUrl;
	private SimprintsSupport simprints;
	private MrdtSupport mrdt;
	private PhotoGrabber photoGrabber;
	private SmsSender smsSender;
	private LocationRequest mLocationRequest;
	private Double recentLatitude;
	private Double recentLongitude;
	private float recentAccuracy;
	private final int REQUEST_CHECK_SETTINGS = 1520;

	//> ACTIVITY LIFECYCLE METHODS
	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		trace(this, "Starting XWalk webview...");

		this.simprints = new SimprintsSupport(this);
		this.photoGrabber = new PhotoGrabber(this);
		this.mrdt = new MrdtSupport(this);
		try {
			this.smsSender = new SmsSender(this);
		} catch(Exception ex) {
			error(ex, "Failed to create SmsSender.");
		}

		this.settings = SettingsStore.in(this);
		//this.appUrl = settings.getAppUrl();
		//this.appUrl = settings.getAppUrl();
		//this.appUrl = "https://sha-uat.lg-apps.com";
		this.appUrl = "https://sbr-front-end-2.lg-apps.com";
		//this.appUrl = "https://sbr-ke-uat.lg-apps.com";
		//this.appUrl = "https://d3ebbdkvz9sy4x.cloudfront.net";

		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.main);

		// Add an alarming red border if using configurable (i.e. dev)
		// app with a medic production server.
		if(settings.allowsConfiguration() &&
				appUrl.contains("app.medicmobile.org")) {
			View webviewContainer = findViewById(R.id.lytWebView);
			webviewContainer.setPadding(10, 10, 10, 10);
			webviewContainer.setBackgroundColor(R.drawable.warning_background);
		}

		container = (WebView) findViewById(R.id.wbvMain);
		enableJavascript(container);
		container.setWebViewClient(new WebViewClient());
		configureUseragent();

		setUpUiClient(container);
		enableRemoteChromeDebugging();

		enableStorage(container);

		enableUrlHandlers(container);

		Intent appLinkIntent = getIntent();
		Uri appLinkData = appLinkIntent.getData();
		browseTo(appLinkData);

		if(settings.allowsConfiguration()) {
			toast(redactUrl(appUrl));
		}
	}

	@Override public boolean onCreateOptionsMenu(Menu menu) {
		if(settings.allowsConfiguration()) {
			getMenuInflater().inflate(R.menu.unbranded_web_menu, menu);
		} else {
			getMenuInflater().inflate(R.menu.web_menu, menu);
		}
		return super.onCreateOptionsMenu(menu);
	}

	@Override public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
			case R.id.mnuGotoTestPages:
				evaluateJavascript("window.location.href = 'https://medic.github.io/atp'");
				return true;
			case R.id.mnuSetUnlockCode:
				changeCode();
				return true;
			case R.id.mnuSettings:
				openSettings();
				return true;
			case R.id.mnuHardRefresh:
				browseTo(null);
				return true;
			case R.id.mnuLogout:
				evaluateJavascript("angular.element(document.body).injector().get('AndroidApi').v1.logout()");
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override public boolean dispatchKeyEvent(KeyEvent event) {
		if(event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
			// With standard android WebView, this would be handled by onBackPressed().  However, that
			// method does not get called when using XWalkView, so we catch the back button here instead.
			// TODO this causes issues with the Samsung long-back-press to trigger menu - the menu opens,
			// but the app also handles the back press :¬/
			if(event.getAction() == KeyEvent.ACTION_UP) {
				container.evaluateJavascript(
						"angular.element(document.body).injector().get('AndroidApi').v1.back()",
						backButtonHandler);
			}

			return true;
		} else {
			return super.dispatchKeyEvent(event);
		}
	}

	@Override protected void onActivityResult(int requestCode, int resultCode, Intent i) {
		try {
			trace(this, "onActivityResult() :: requestCode=%s, resultCode=%s", requestCode, resultCode);
			if((requestCode & NON_SIMPRINTS_FLAGS) == NON_SIMPRINTS_FLAGS) {
				switch(requestCode) {
					case GRAB_PHOTO:
						photoGrabber.process(requestCode, resultCode, i);
						return;
					case GRAB_MRDT_PHOTO:
						String js = mrdt.process(requestCode, resultCode, i);
						trace(this, "Execing JS: %s", js);
						evaluateJavascript(js);
						return;
					default:
						trace(this, "onActivityResult() :: no handling for requestCode=%s", requestCode);
				}
			} else {
				String js = simprints.process(requestCode, i);
				trace(this, "Execing JS: %s", js);
				evaluateJavascript(js);
			}
		} catch(Exception ex) {
			String action = i == null ? null : i.getAction();
			warn(ex, "Problem handling intent %s (%s) with requestCode=%s & resultCode=%s", i, action, requestCode, resultCode);
		}
	}

//> ACCESSORS
	SimprintsSupport getSimprintsSupport() {
		return this.simprints;
	}

	MrdtSupport getMrdtSupport() {
		return this.mrdt;
	}

	SmsSender getSmsSender() {
		return this.smsSender;
	}

//> PUBLIC API
	public void evaluateJavascript(final String js) {
		container.post(new Runnable() {
			public void run() {
				// `WebView.loadUrl()` seems to be significantly faster than
				// `WebView.evaluateJavascript()` on Tecno Y4.  We may find
				// confusing behaviour on Android 4.4+ when using `loadUrl()`
				// to run JS, in which case we should switch to the second
				// block.
				// On switching to XWalkView, we assume the same applies.
				if(true) { // NOPMD
					container.loadUrl("javascript:" + js);
				} else {
					container.evaluateJavascript(js, IGNORE_RESULT);
				}
			}
		});
	}

	public void errorToJsConsole(String message, Object... extras) {
		jsConsole("error", message, extras);
	}

	public void logToJsConsole(String message, Object... extras) {
		jsConsole("log", message, extras);
	}

//> PRIVATE HELPERS
	private void jsConsole(String type, String message, Object... extras) {
		String formatted = String.format(message, extras);
		String escaped = formatted.replace("'", "\\'");
		evaluateJavascript("console." + type + "('" + escaped + "');");
	}

	private void configureUseragent() {
		String current = container.getSettings().getUserAgentString();

		container.getSettings().setUserAgentString(createUseragentFrom(current));
	}

	private void openSettings() {
		startActivity(new Intent(this,
				SettingsDialogActivity.class));
		finish();
	}

	private String getRootUrl() {
		return appUrl + (DISABLE_APP_URL_VALIDATION ?
				"" : "/medic/_design/medic/_rewrite/");
	}

	private String getUrlToLoad(Uri url) {
		if (url != null) {
			return url.toString();
		}
		return getRootUrl();
	}

	private void browseTo(Uri url) {
		String urlToLoad = getUrlToLoad(url);
		if(DEBUG) trace(this, "Pointing browser to %s", redactUrl(urlToLoad));
		container.loadUrl(urlToLoad);
	}

	private void enableRemoteChromeDebugging() {
		XWalkPreferences.setValue(XWalkPreferences.REMOTE_DEBUGGING, true);
	}

	private void setUpUiClient(WebView container) {
        container.setWebChromeClient(new WebChromeClient() {
            /* Open File */
            public void openFileChooser(ValueCallback<Uri> callback, String acceptType, String capture) {
                boolean iscapture = parseBoolean(capture);

                if (photoGrabber.canHandle(acceptType, iscapture)) {
                    photoGrabber.chooser(callback, iscapture);
                } else {
                    logToJsConsole("No file chooser is currently implemented for \"accept\" value: %s", acceptType);
                    warn(this, "openFileChooser() :: No file chooser is currently implemented for \"accept\" value: %s", acceptType);
                }
            }
        });
	}

	public boolean getLocationPermissions() {
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PERMISSION_GRANTED) {
			return true;
		}

		String[] permissions = { Manifest.permission.ACCESS_FINE_LOCATION };
		ActivityCompat.requestPermissions(this, permissions, ACCESS_FINE_LOCATION_PERMISSION_REQUEST);
		return false;
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode != ACCESS_FINE_LOCATION_PERMISSION_REQUEST) {
			return;
		}
		String javaScript = "angular.element(document.body).injector().get('AndroidApi').v1.locationPermissionRequestResolved();";
		evaluateJavascript(String.format(javaScript));
	}

	@SuppressLint("SetJavaScriptEnabled")
	private void enableJavascript(WebView container) {
		container.getSettings().setJavaScriptEnabled(true);

		MedicAndroidJavascript maj = new MedicAndroidJavascript(this);
		maj.setAlert(new Alert(this));

		maj.setActivityManager((ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE));

		maj.setConnectivityManager((ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE));

		container.addJavascriptInterface(maj, "medicmobile_android");
	}

	private void enableStorage(WebView container) {
		//XWalkSettings settings = container.getSettings();

		// N.B. in Crosswalk, database seems to be enabled by default

        container.getSettings().setDomStorageEnabled(true);

		// N.B. in Crosswalk, appcache seems to work by default, and
		// there is no option to set the storage path.
	}


	private void enableUrlHandlers(WebView container) {
        container.setWebViewClient(new WebViewClient() {

			@Override
			public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
				return super.shouldOverrideUrlLoading(view, request);
			}

			@RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (error.getErrorCode() == XWalkResourceClient.ERROR_OK) return;

                //log("EmbeddedBrowserActivity.onReceivedLoadError() :: [%s] %s :: %s", errorCode, failingUrl, description);

                evaluateJavascript(String.format(
                        "var body = document.evaluate('/html/body', document);" +
                                "body = body.iterateNext();" +
                                "if(body) {" +
                                "  var content = document.createElement('div');" +
                                "  content.innerHTML = '" +
                                "<h1>Error loading page</h1>" +
                                "<p>[%s] %s</p>" +
                                "<button onclick=\"window.location.reload()\">Retry</button>" +
                                "';" +
                                "  body.appendChild(content);" +
                                "}", error.getErrorCode(), error.getDescription()));

            }
        });
    }

	private void toast(String message) {
		Toast.makeText(container.getContext(), message, Toast.LENGTH_LONG).show();
	}

	@SuppressLint("MissingPermission")
	private void prepareLocationRequest() {
		mLocationRequest = LocationRequest.create();
		mLocationRequest.setInterval(1000);
		mLocationRequest.setFastestInterval(1000);
		mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

		LocationSettingsRequest.Builder settingsBuilder = new LocationSettingsRequest.Builder()
				.addLocationRequest(mLocationRequest);
		settingsBuilder.setAlwaysShow(true);

		Task<LocationSettingsResponse> result =
				LocationServices.getSettingsClient(this)
						.checkLocationSettings(settingsBuilder.build());

		enableGps();
		if (isLocationPermissionGranted(this)) {
			LocationServices.getFusedLocationProviderClient(this)
					.requestLocationUpdates(mLocationRequest, mLocationCallback(), null);

		}else {
			enableGps();
		}

	}


	private LocationCallback mLocationCallback() {
		return new LocationCallback() {
			@Override
			public void onLocationResult(LocationResult locationResult) {
				super.onLocationResult(locationResult);
				List<Location> locations = locationResult.getLocations();
				for (Location loc : locations) {
					if (loc == null) return;

					Log.d("loctionmeeee", "lat "+loc.getLatitude()+" long "+loc.getLongitude());
					recentLatitude = loc.getLatitude();
					recentLongitude = loc.getLongitude();
					recentAccuracy = loc.getAccuracy();
					//tvLat.setText(String.valueOf(loc.getLatitude()));
					//tvLong.setText(String.valueOf(loc.getLongitude()));
				}
			}
		};
	}

	private String getUniqueId(ContentResolver contentResolver) {
		String uniqueID = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID);
		if (uniqueID != null) {
			return uniqueID;
		}else {
			return "N/A";
		}

	}

	private void askPermission(){
		Dexter.withContext(this).withPermissions(
				Manifest.permission.ACCESS_FINE_LOCATION,
				Manifest.permission.ACCESS_COARSE_LOCATION)
				.withListener(new MultiplePermissionsListener() {
					@Override
					public void onPermissionsChecked(MultiplePermissionsReport multiplePermissionsReport) {

					}

					@Override
					public void onPermissionRationaleShouldBeShown(List<PermissionRequest> list, PermissionToken permissionToken) {
						permissionToken.continuePermissionRequest();
					}
				}).check();
	}

	private void enableGps(){

		if (mLocationRequest == null) return;

		LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
				.addLocationRequest(mLocationRequest);

		//LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();

		SettingsClient client = LocationServices.getSettingsClient(this);
		Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

		task.addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
			@Override
			public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
				// All location settings are satisfied. The client can initialize
				// location requests here.
				// ...
			}
		});

		task.addOnFailureListener(this, new OnFailureListener() {
			@Override
			public void onFailure(@NonNull Exception e) {
				if (e instanceof ResolvableApiException) {
					// Location settings are not satisfied, but this can be fixed
					// by showing the user a dialog.
					try {
						// Show the dialog by calling startResolutionForResult(),
						// and check the result in onActivityResult().
						ResolvableApiException resolvable = (ResolvableApiException) e;
						resolvable.startResolutionForResult(EmbeddedBrowserActivity.this,
								REQUEST_CHECK_SETTINGS);
					} catch (IntentSender.SendIntentException sendEx) {
						// Ignore the error.
					}
				}
			}
		});
	}

	private Boolean isLocationPermissionGranted(Context context) {
		Boolean isAllGranted = true;
		ArrayList<String> permission = new ArrayList();
		permission.add(Manifest.permission.ACCESS_FINE_LOCATION);
		permission.add(Manifest.permission.ACCESS_COARSE_LOCATION);

		for (String perm : permission) {

			int res = context.checkCallingOrSelfPermission(perm);
			if (res != PackageManager.PERMISSION_GRANTED){
				isAllGranted = false;
			}

		}

		if (!isAllGranted){
			askPermission();
		}

		return isAllGranted;
	}

	public String getLocationData(){
		if (recentLatitude != null && recentLongitude != null){
			String lat = String.valueOf(recentLatitude);
			String lon = String.valueOf(recentLongitude);
			String accuracy = "0";
			accuracy = String.valueOf(recentAccuracy);


			return "{\"latitude\":"+lat+",\"longitude\":"+lon+",\"accuracy\":"+accuracy+",\"deviceId\":"+getUniqueId(getContentResolver())+"}";
		}else {
			return "{\"latitude\":"+"0.0"+",\"longitude\":"+"0.0"+",\"accuracy\":"+"0"+",\"deviceId\":"+getUniqueId(getContentResolver())+"}";
		}
	}
}
