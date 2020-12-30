package org.medicmobile.webapp.mobile;

import android.content.Context;
import android.util.Log;
import android.webkit.JavascriptInterface;

import com.google.gson.Gson;

public class GpsJSInterface {

    Context mContext;

    /**
     * Instantiate the interface and set the context
     */
    GpsJSInterface(Context c) {
        mContext = c;
    }

    /*
    @org.xwalk.core.JavascriptInterface
    @JavascriptInterface
    public String getLocationData() {

        Log.d("getLocationData", "triggered");
        try {
            String locationJson = ((EmbeddedBrowserActivity) mContext).getLocationData();
            if (locationJson == null) {
                Log.d("getLocationData",  "Location Fetching..");
                return "Location Fetching..";
            } else {
                Log.d("getLocationData",  locationJson);
                return locationJson;
            }
        } catch (Exception e) {
            Log.d("getLocationData",  e.getMessage());
            e.printStackTrace();
            return "Location Error";
        }
    }
    */

    @org.xwalk.core.JavascriptInterface
    @JavascriptInterface
    public void saveFormType(String jsonData) {
        Log.d("saveFormType", "triggered" + jsonData);
        JSInterfaceModel jsInterfaceModel = new Gson().fromJson(jsonData, JSInterfaceModel.class);
        Log.d("saveFormType", "jsonData = " + jsInterfaceModel.getForm() + " , " + jsInterfaceModel.getUserName());
    }
}



