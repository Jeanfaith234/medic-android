package org.medicmobile.webapp.mobile;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import com.google.gson.Gson;

public class GpsJSInterface {

    Context mContext;

    /**
     * Instantiate the interface and set the context
     */
    GpsJSInterface(Context c) {
        mContext = c;
    }

    @org.xwalk.core.JavascriptInterface
    @JavascriptInterface
    public String getLocationData() {

        Log.d("getLocationData", "triggered");
        try {
            String locationJson = "{\"latitude\":12.86,\"longitude\":80.56,\"accuracy\":10,\"deviceId\":4354365ytuyfuyfu}";
            if (locationJson == null) {
                Log.d("getLocationData", "Location Fetching..");
                return "Location Fetching..";
            } else {
                Log.d("getLocationData", locationJson);
                return locationJson;
            }
        } catch (Exception e) {
            Log.d("getLocationData", e.getMessage());
            e.printStackTrace();
            return "Location Error";
        }
    }

    @org.xwalk.core.JavascriptInterface
    @JavascriptInterface
    public void saveFormType(String jsonData) {
        if (jsonData != null) {
            JSInterfaceModel jsInterfaceModel = new Gson().fromJson(jsonData, JSInterfaceModel.class);
            if (jsInterfaceModel != null) {
                Intent intent = new Intent();
                intent.setAction("org.medicmobile.webapp.mobile");
                intent.putExtra("Form", jsInterfaceModel.getForm());
                intent.putExtra("Username", jsInterfaceModel.getUserName());
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                intent.setComponent(new ComponentName("com.lg.gis", "com.lg.gis.receiver.MedicDataReceiver"));
                mContext.sendBroadcast(intent);
            }
        }
    }
}



