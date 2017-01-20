/*
The MIT License (MIT)

Copyright (c) 2016 Mikihiro Hayashi

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package org.apache.cordova.buildinfo;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashMap;

import java.lang.reflect.Field;

import com.baidu.speech.EventManager;
import com.baidu.speech.EventManagerFactory;
import com.baidu.speech.EventListener;

/**
 * BuildInfo Cordova Plugin
 *
 * @author Mikihiro Hayashi
 * @since 1.0.0
 */
public class BuildInfo extends CordovaPlugin {
	private static final String TAG = "WakeUp";

    private EventManager mWpEventManager = null;
    private CallbackContext m_cb = null;

	/**
	 * Cache of result JSON
	 */
	private static JSONObject mBuildInfoCache;

	/**
	 * Constructor
	 */
	public BuildInfo() {
	}

	/**
	 * execute
	 * @param action          The action to execute.
	 * @param args            The exec() arguments.
	 * @param callbackContext The callback context used when calling back into JavaScript.
	 * @return
	 * @throws JSONException
     */
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

		if ("init".equals(action)) {
			String buildConfigClassName = null;
			if (1 < args.length()) {
				buildConfigClassName = args.getString(0);
			}

			init(buildConfigClassName, callbackContext);
			return true;
		} else if ("version".equals(action)) {
            android.util.Log.e("a22301", "PRETTY COOL, Android");
            String msg = args.getString(0);
            this.waitWakupCmd(msg, callbackContext);
            return true; /* FIXME - how to indicate this value? */
        } else if ("stopWakeup".equals(action)) {
            if(mWpEventManager == null) {
                android.util.Log.d(TAG, "null obj, do nothing");
            } else {
                android.util.Log.e(TAG, "try stopping wakeup...");
                mWpEventManager.send("wp.stop",
                        null, null, 0, 0);
                callbackContext.success("Stopped");
            }
            return true;
        }

		return false;
	}

	/**
	 * init
	 * @param buildConfigClassName null or specified BuildConfig class name
	 * @param callbackContext
	 */
	private void init(String buildConfigClassName, CallbackContext callbackContext) {
		// Cached check
		if (null != mBuildInfoCache) {
			callbackContext.success(mBuildInfoCache);
			return;
		}

		// Load PackageInfo
		Activity activity = cordova.getActivity();
		String packageName = activity.getPackageName();
		String basePackageName = packageName;
		CharSequence displayName = "";

		PackageManager pm = activity.getPackageManager();

		try {
			PackageInfo pi = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);

			if (null != pi.applicationInfo) {
				displayName = pi.applicationInfo.loadLabel(pm);
			}
		} catch (PackageManager.NameNotFoundException e) {
			e.printStackTrace();
		}

		// Load BuildConfig class
		Class c = null;

		if (null == buildConfigClassName) {
			buildConfigClassName = packageName + ".BuildConfig";
		}

		try {
			c = Class.forName(buildConfigClassName);
		} catch (ClassNotFoundException e) {
		}

		if (null == c) {
			basePackageName = activity.getClass().getPackage().getName();
			buildConfigClassName = basePackageName + ".BuildConfig";

			try {
				c = Class.forName(buildConfigClassName);
			} catch (ClassNotFoundException e) {
				callbackContext.error("BuildConfig ClassNotFoundException: " + e.getMessage());
				return;
			}
		}

		// Create result
		mBuildInfoCache = new JSONObject();
		try {
			boolean debug = getClassFieldBoolean(c, "DEBUG", false);

			mBuildInfoCache.put("packageName"    , packageName);
			mBuildInfoCache.put("basePackageName", basePackageName);
			mBuildInfoCache.put("displayName"    , displayName);
			mBuildInfoCache.put("name"           , displayName); // same as displayName
			mBuildInfoCache.put("version"        , getClassFieldString(c, "VERSION_NAME", ""));
			mBuildInfoCache.put("versionCode"    , getClassFieldInt(c, "VERSION_CODE", 0));
			mBuildInfoCache.put("debug"          , debug);
			mBuildInfoCache.put("buildType"      , getClassFieldString(c, "BUILD_TYPE", ""));
			mBuildInfoCache.put("flavor"         , getClassFieldString(c, "FLAVOR", ""));

			if (debug) {
				Log.d(TAG, "packageName    : \"" + mBuildInfoCache.getString("packageName") + "\"");
				Log.d(TAG, "basePackageName: \"" + mBuildInfoCache.getString("basePackageName") + "\"");
				Log.d(TAG, "displayName    : \"" + mBuildInfoCache.getString("displayName") + "\"");
				Log.d(TAG, "name           : \"" + mBuildInfoCache.getString("name") + "\"");
				Log.d(TAG, "version        : \"" + mBuildInfoCache.getString("version") + "\"");
				Log.d(TAG, "versionCode    : " + mBuildInfoCache.getInt("versionCode"));
				Log.d(TAG, "debug          : " + (mBuildInfoCache.getBoolean("debug") ? "true" : "false"));
				Log.d(TAG, "buildType      : \"" + mBuildInfoCache.getString("buildType") + "\"");
				Log.d(TAG, "flavor         : \"" + mBuildInfoCache.getString("flavor") + "\"");
			}
		} catch (JSONException e) {
			e.printStackTrace();
			callbackContext.error("JSONException: " + e.getMessage());
			return;
		}

		callbackContext.success(mBuildInfoCache);
	}

	/**
	 * Get boolean of field from Class
	 * @param c
	 * @param fieldName
	 * @param defaultReturn
     * @return
     */
	private static boolean getClassFieldBoolean(Class c, String fieldName, boolean defaultReturn) {
		boolean ret = defaultReturn;
		Field field = getClassField(c, fieldName);

		if (null != field) {
			try {
				ret = field.getBoolean(c);
			} catch (IllegalAccessException iae) {
				iae.printStackTrace();
			}
		}

		return ret;
	}

	/**
	 * Get string of field from Class
	 * @param c
	 * @param fieldName
	 * @param defaultReturn
     * @return
     */
	private static String getClassFieldString(Class c, String fieldName, String defaultReturn) {
		String ret = defaultReturn;
		Field field = getClassField(c, fieldName);

		if (null != field) {
			try {
				ret = (String)field.get(c);
			} catch (IllegalAccessException iae) {
				iae.printStackTrace();
			}
		}

		return ret;
	}

	/**
	 * Get int of field from Class
	 * @param c
	 * @param fieldName
	 * @param defaultReturn
     * @return
     */
	private static int getClassFieldInt(Class c, String fieldName, int defaultReturn) {
		int ret = defaultReturn;
		Field field = getClassField(c, fieldName);

		if (null != field) {
			try {
				ret = field.getInt(c);
			} catch (IllegalAccessException iae) {
				iae.printStackTrace();
			}
		}

		return ret;
	}

	/**
	 * Get field from Class
	 * @param c
	 * @param fieldName
     * @return
     */
	private static Field getClassField(Class c, String fieldName) {
		Field field = null;

		try {
			field = c.getField(fieldName);
		} catch (NoSuchFieldException nsfe) {
			nsfe.printStackTrace();
		}

		return field;
	}

    // My newly-added function...
    private void waitWakupCmd(String message, CallbackContext callbackContext) {
        m_cb = callbackContext;

        android.util.Log.e(TAG, "try use wakup event...");
        mWpEventManager = EventManagerFactory.create(cordova.getActivity(), "wp");
        mWpEventManager.registerListener(new EventListener() {
                @Override
                public void onEvent(String name, String params, byte[] data, int offset, int length){
                    String val;
                    android.util.Log.e(TAG, String.format("event: name=%s, params=%s", name, params));
                    try{
                        JSONObject json = new JSONObject(params);
                        if("wp.data".equals(name)) {
                            android.util.Log.e(TAG, "BINGO~~, you say it!");
                            val = "WAKUP!";
                            notifyResult(val, true); // GOOD, tell upper layer...
                        } else if ("wp.exit".equals(name)) {
                            android.util.Log.e(TAG, "STOPPED, you need restart again");
                            val = "STOPPED, need restart";
                            notifyResult(val, false);
                        }
                    } catch(JSONException e) {
                        android.util.Log.e(TAG, "got JSON Exception...");
                        val = "Exception found";
                        notifyResult(val, false);
                    }
                }
             });

        HashMap params = new HashMap();
        params.put("kws-file", "assets:///WakeUp.bin");
        mWpEventManager.send("wp.start", new JSONObject(params).toString(), null, 0, 0);
        android.util.Log.e(TAG, "-->tracking started...");
    }

    private void notifyResult(String data, boolean goodNews){
        if (m_cb == null) {
            return;
        }

        if(goodNews)
            m_cb.success(data);
        else
            m_cb.error(data);
    }

}
