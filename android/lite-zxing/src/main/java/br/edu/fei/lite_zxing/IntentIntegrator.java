/*
 * Copyright 2009 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package br.edu.fei.lite_zxing;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;

/**
 * <p>A utility class which helps ease integration with Barcode Scanner via {@link Intent}s. This is a simple
 * way to invoke barcode scanning and receive the result, without any need to integrate, modify, or learn the
 * project's source code.</p>
 * <p>
 * <h2>Initiating a barcode scan</h2>
 * <p>
 * <p>To integrate, create an instance of {@code IntentIntegrator} and call {@link #initiateScan()} and wait
 * for the result in your app.</p>
 * <p>
 * <p>It does require that the Barcode Scanner (or work-alike) application is installed. The
 * {@link #initiateScan()} method will prompt the user to download the application, if needed.</p>
 * <p>
 * <p>There are a few steps to using this integration. First, your {@link Activity} must implement
 * the method {@link Activity#onActivityResult(int, int, Intent)} and include a line of code like this:</p>
 * <p>
 * <pre>{@code
 * public void onActivityResult(int requestCode, int resultCode, Intent intent) {
 *   IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
 *   if (scanResult != null) {
 *     // handle scan result
 *   }
 *   // else continue with any other code you need in the method
 *   ...
 * }
 * }</pre>
 * <p>
 * <p>This is where you will handle a scan result.</p>
 * <p>
 * <p>Second, just call this in response to a user action somewhere to begin the scan process:</p>
 * <p>
 * <pre>{@code
 * IntentIntegrator integrator = new IntentIntegrator(yourActivity);
 * integrator.initiateScan();
 * }</pre>
 * <p>
 * <p>Note that {@link #initiateScan()} returns an {@link AlertDialog} which is non-null if the
 * user was prompted to download the application. This lets the calling app potentially manage the dialog.
 * In particular, ideally, the app dismisses the dialog if it's still active in its {@link Activity#onPause()}
 * method.</p>
 * <p>
 * <p>You can use {@link #setTitle(String)} to customize the title of this download prompt dialog (or, use
 * {@link #setTitleByID(int)} to set the title by string resource ID.) Likewise, the prompt message, and
 * yes/no button labels can be changed.</p>
 * <p>
 * <p>Finally, you can use {@link #addExtra(String, Object)} to add more parameters to the Intent used
 * to invoke the scanner. This can be used to set additional options not directly exposed by this
 * simplified API.</p>
 * <p>
 * <p>By default, this will only allow applications that are known to respond to this intent correctly
 * do so. The apps that are allowed to response can be set with {@link #setTargetApplications(List)}.
 * For example, set to {@link #TARGET_BARCODE_SCANNER_ONLY} to only target the Barcode Scanner app itself.</p>
 * <p>
 * <h2>Sharing text via barcode</h2>
 * <p>
 * <p>To share text, encoded as a QR Code on-screen, similarly, see {@link #shareText(CharSequence)}.</p>
 * <p>
 * <p>Some code, particularly download integration, was contributed from the Anobiit application.</p>
 * <p>
 * <h2>Enabling experimental barcode formats</h2>
 * <p>
 * <p>Some formats are not enabled by default even when scanning with {@link #ALL_CODE_TYPES}, such as
 * PDF417. Use {@link #initiateScan(Collection)} with
 * a collection containing the names of formats to scan for explicitly, like "PDF_417", to use such
 * formats.</p>
 *
 * @author Sean Owen
 * @author Fred Lin
 * @author Isaac Potoczny-Jones
 * @author Brad Drehmer
 * @author gcstang
 */
public class IntentIntegrator {

    public static final int REQUEST_CODE = 0x0000c0de; // Only use bottom 16 bits
    private static final String TAG = IntentIntegrator.class.getSimpleName();

    // Should be FLAG_ACTIVITY_NEW_DOCUMENT in API 21+.
    // Defined once here because the current value is deprecated, so generates just one warning
    private static final int FLAG_NEW_DOC = Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET;

    private final Activity activity;
    private final Fragment fragment;

    private List<String> targetApplications;
    private final Map<String, Object> moreExtras = new HashMap<String, Object>(3);

    /**
     * @param activity {@link Activity} invoking the integration
     */
    public IntentIntegrator(Activity activity) {
        this.activity = activity;
        this.fragment = null;
        initializeConfiguration();
    }

    /**
     * @param fragment {@link Fragment} invoking the integration.
     *                 {@link #startActivityForResult(Intent, int)} will be called on the {@link Fragment} instead
     *                 of an {@link Activity}
     */
    public IntentIntegrator(Fragment fragment) {
        this.activity = fragment.getActivity();
        this.fragment = fragment;
        initializeConfiguration();
    }

    private void initializeConfiguration() {
        //targetApplications = TARGET_ALL_KNOWN;
    }

    public Collection<String> getTargetApplications() {
        return targetApplications;
    }

    public final void setTargetApplications(List<String> targetApplications) {
        if (targetApplications.isEmpty()) {
            throw new IllegalArgumentException("No target applications");
        }
        this.targetApplications = targetApplications;
    }

    public void setSingleTargetApplication(String targetApplication) {
        this.targetApplications = Collections.singletonList(targetApplication);
    }

    public Map<String, ?> getMoreExtras() {
        return moreExtras;
    }

    public final void addExtra(String key, Object value) {
        moreExtras.put(key, value);
    }

    /**
     * Initiates a scan for all known barcode types with the default camera.
     *
     * @return the {@link AlertDialog} that was shown to the user prompting them to download the app
     * if a prompt was needed, or null otherwise.
     */
    public final AlertDialog initiateScan(Context context) {
        return initiateScan(-1, context);
    }

    /**
     * Initiates a scan for all known barcode types with the specified camera.
     *
     * @param cameraId camera ID of the camera to use. A negative value means "no preference".
     * @return the {@link AlertDialog} that was shown to the user prompting them to download the app
     * if a prompt was needed, or null otherwise.
     */
    //public final AlertDialog initiateScan(int cameraId, Context context) {
    //    return initiateScan(ALL_CODE_TYPES, cameraId, context);
    //}

    /**
     * Initiates a scan, using the default camera, only for a certain set of barcode types, given as strings corresponding
     * to their names in ZXing's {@code BarcodeFormat} class like "UPC_A". You can supply constants
     * like {@link #PRODUCT_CODE_TYPES} for example.
     *
     * @param desiredBarcodeFormats names of {@code BarcodeFormat}s to scan for
     * @return the {@link AlertDialog} that was shown to the user prompting them to download the app
     * if a prompt was needed, or null otherwise.
     */
    //public final AlertDialog initiateScan(Context context) {
        //return initiateScan(-1, context);
    //}

    /**
     * Initiates a scan, using the specified camera, only for a certain set of barcode types, given as strings corresponding
     * to their names in ZXing's {@code BarcodeFormat} class like "UPC_A". You can supply constants
     * like {@link #PRODUCT_CODE_TYPES} for example.
     *
     * @param desiredBarcodeFormats names of {@code BarcodeFormat}s to scan for
     * @param cameraId              camera ID of the camera to use. A negative value means "no preference".
     * @return the {@link AlertDialog} that was shown to the user prompting them to download the app
     * if a prompt was needed, or null otherwise
     */
    public final AlertDialog initiateScan(int cameraId, Context context) {

        Intent intentScan = new Intent(context, CaptureActivity.class);

        intentScan.addCategory(Intent.CATEGORY_DEFAULT);
        intentScan.putExtra("SCAN_FORMATS", "QR_CODE");


        // check requested camera ID
        if (cameraId >= 0) {
            intentScan.putExtra("SCAN_CAMERA_ID", cameraId);
        }

        intentScan.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intentScan.addFlags(FLAG_NEW_DOC);
        attachMoreExtras(intentScan);
        startActivityForResult(intentScan, REQUEST_CODE);
        return null;
    }

    /**
     * Start an activity. This method is defined to allow different methods of activity starting for
     * newer versions of Android and for compatibility library.
     *
     * @param intent Intent to start.
     * @param code   Request code for the activity
     * @see Activity#startActivityForResult(Intent, int)
     * @see Fragment#startActivityForResult(Intent, int)
     */
    protected void startActivityForResult(Intent intent, int code) {
        if (fragment == null) {
            activity.startActivityForResult(intent, code);
        } else {
            fragment.startActivityForResult(intent, code);
        }
    }

    private String findTargetAppPackage(Intent intent) {
        PackageManager pm = activity.getPackageManager();
        List<ResolveInfo> availableApps = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (availableApps != null) {
            for (String targetApp : targetApplications) {
                if (contains(availableApps, targetApp)) {
                    return targetApp;
                }
            }
        }
        return null;
    }

    private static boolean contains(Iterable<ResolveInfo> availableApps, String targetApp) {
        for (ResolveInfo availableApp : availableApps) {
            String packageName = availableApp.activityInfo.packageName;
            if (targetApp.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * <p>Call this from your {@link Activity}'s
     * {@link Activity#onActivityResult(int, int, Intent)} method.</p>
     *
     * @param requestCode request code from {@code onActivityResult()}
     * @param resultCode  result code from {@code onActivityResult()}
     * @param intent      {@link Intent} from {@code onActivityResult()}
     * @return null if the event handled here was not related to this class, or
     * else an {@link com.google.zxing.integration.android.IntentResult} containing the result of the scan. If the user cancelled scanning,
     * the fields will be null.
     */
    public static IntentResult parseActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                String contents = intent.getStringExtra("SCAN_RESULT");
                String formatName = intent.getStringExtra("SCAN_RESULT_FORMAT");
                byte[] rawBytes = intent.getByteArrayExtra("SCAN_RESULT_BYTES");
                int intentOrientation = intent.getIntExtra("SCAN_RESULT_ORIENTATION", Integer.MIN_VALUE);
                Integer orientation = intentOrientation == Integer.MIN_VALUE ? null : intentOrientation;
                String errorCorrectionLevel = intent.getStringExtra("SCAN_RESULT_ERROR_CORRECTION_LEVEL");
                return new IntentResult(contents,
                        formatName,
                        rawBytes,
                        orientation,
                        errorCorrectionLevel);
            }
            return new IntentResult();
        }
        return null;
    }


    /**
     * Defaults to type "TEXT_TYPE".
     *
     * @param text the text string to encode as a barcode
     * @return the {@link AlertDialog} that was shown to the user prompting them to download the app
     * if a prompt was needed, or null otherwise
     * @see #shareText(CharSequence, CharSequence)
     */
    public final AlertDialog shareText(CharSequence text) {
        return shareText(text, "TEXT_TYPE");
    }

    /**
     * Shares the given text by encoding it as a barcode, such that another user can
     * scan the text off the screen of the device.
     *
     * @param text the text string to encode as a barcode
     * @param type type of data to encode. See {@code com.google.zxing.client.android.Contents.Type} constants.
     * @return the {@link AlertDialog} that was shown to the user prompting them to download the app
     * if a prompt was needed, or null otherwise
     */
    public final AlertDialog shareText(CharSequence text, CharSequence type) {
        Intent intent = new Intent();
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        //intent.setAction(BS_PACKAGE + ".ENCODE");
        intent.putExtra("ENCODE_TYPE", type);
        intent.putExtra("ENCODE_DATA", text);
        String targetAppPackage = findTargetAppPackage(intent);
        if (targetAppPackage == null) {
            //return showDownloadDialog();
        }
        intent.setPackage(targetAppPackage);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(FLAG_NEW_DOC);
        attachMoreExtras(intent);
        if (fragment == null) {
            activity.startActivity(intent);
        } else {
            fragment.startActivity(intent);
        }
        return null;
    }

    private static List<String> list(String... values) {
        return Collections.unmodifiableList(Arrays.asList(values));
    }

    private void attachMoreExtras(Intent intent) {
        for (Map.Entry<String, Object> entry : moreExtras.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            // Kind of hacky
            if (value instanceof Integer) {
                intent.putExtra(key, (Integer) value);
            } else if (value instanceof Long) {
                intent.putExtra(key, (Long) value);
            } else if (value instanceof Boolean) {
                intent.putExtra(key, (Boolean) value);
            } else if (value instanceof Double) {
                intent.putExtra(key, (Double) value);
            } else if (value instanceof Float) {
                intent.putExtra(key, (Float) value);
            } else if (value instanceof Bundle) {
                intent.putExtra(key, (Bundle) value);
            } else {
                intent.putExtra(key, value.toString());
            }
        }
    }

}