package wang.tato.cordova.filepath;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;

import org.apache.commons.io.IOUtils;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PermissionHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class FilePath extends CordovaPlugin {

    private static final String TAG = "[FilePath plugin]: ";

    private static final int INVALID_ACTION_ERROR_CODE = -1;

    private static final int GET_PATH_ERROR_CODE = 0;
    private static final String GET_PATH_ERROR_ID = null;

    private static final int GET_CLOUD_PATH_ERROR_CODE = 1;
    private static final String GET_CLOUD_PATH_ERROR_ID = "cloud";

    private static final int RC_READ_EXTERNAL_STORAGE = 5;

    private static CallbackContext callback;
    private static String uriStr;
    private static Uri currentUri;

    public static final int READ_REQ_CODE = 0;

    public static final String READ = Manifest.permission.READ_EXTERNAL_STORAGE;

    protected void getReadPermission(int requestCode) {
        PermissionHelper.requestPermission(this, requestCode, READ);
    }

    public void initialize(CordovaInterface cordova, final CordovaWebView webView) {
        super.initialize(cordova, webView);
    }

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action          The action to execute.
     * @param args            JSONArry of arguments for the plugin.
     * @param callbackContext The callback context through which to return stuff to caller.
     * @return A PluginResult object with a status and message.
     */
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.callback = callbackContext;
        this.uriStr = args.getString(0);
        this.currentUri = Uri.parse(this.uriStr);
        if (action.equals("resolveNativePath")) {
            String filePath = getPath(this.cordova.getContext());
            if (filePath == null) {
                callbackContext.error(GET_PATH_ERROR_CODE);
            } else {
                callbackContext.success(filePath);
            }
            return true;
        } else {
            JSONObject resultObj = new JSONObject();

            resultObj.put("code", INVALID_ACTION_ERROR_CODE);
            resultObj.put("message", "Invalid action.");

            callbackContext.error(resultObj);
        }

        return false;
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    private static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    private static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    private static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    private static boolean isGooglePhotosUri(Uri uri) {
        return ("com.google.android.apps.photos.content".equals(uri.getAuthority()) || "com.google.android.apps.photos.contentprovider".equals(uri.getAuthority()));
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Drive.
     */
    private static boolean isGoogleDriveUri(Uri uri) {
        return "com.google.android.apps.docs.storage".equals(uri.getAuthority()) || "com.google.android.apps.docs.storage.legacy".equals(uri.getAuthority());
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context   The context.
     * @param uri       The Uri to query.
     * @param selection (Optional) Filter used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    private static String getDataColumn(Context context, Uri uri, String selection) {

        Cursor cursor = null;
        final String column = MediaStore.Files.FileColumns.DATA;
        final String[] projection = {MediaStore.Files.FileColumns._ID, column};

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } catch (Exception e) {
            Log.d(TAG, e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    /**
     * Get content:// from segment list
     * In the new Uri Authority of Google Photos, the last segment is not the content:// anymore
     * So let's iterate through all segments and find the content uri!
     *
     * @param segments The list of segment
     */
    private static String getContentFromSegments(List<String> segments) {
        String contentPath = "";

        for (String item : segments) {
            if (item.startsWith("content://")) {
                contentPath = item;
                break;
            }
        }

        return contentPath;
    }

    /**
     * Check if a file exists on device
     *
     * @param filePath The absolute file path
     */
    private static boolean fileExists(String filePath) {
        File file = new File(filePath);

        return file.exists();
    }

    /**
     * Get full file path from external storage
     *
     * @param pathData The storage type and the relative path
     */
    private static String getPathFromExtSD(String[] pathData) {
        final String type = pathData[0];
        final String relativePath = "/" + pathData[1];
        String fullPath = "";

        // on my Sony devices (4.4.4 & 5.1.1), `type` is a dynamic string
        // something like "71F8-2C0A", some kind of unique id per storage
        // don't know any API that can get the root path of that storage based on its id.
        //
        // so no "primary" type, but let the check here for other devices
        if ("primary".equalsIgnoreCase(type)) {
            fullPath = Environment.getExternalStorageDirectory() + relativePath;
            if (fileExists(fullPath)) {
                return fullPath;
            }
        }

        // Environment.isExternalStorageRemovable() is `true` for external and internal storage
        // so we cannot relay on it.
        //
        // instead, for each possible path, check if file exists
        // we'll start with secondary storage as this could be our (physically) removable sd card
        fullPath = System.getenv("SECONDARY_STORAGE") + relativePath;
        if (fileExists(fullPath)) {
            return fullPath;
        }

        fullPath = System.getenv("EXTERNAL_STORAGE") + relativePath;
        if (fileExists(fullPath)) {
            return fullPath;
        }

        return fullPath;
    }

    /**
     * Get a file path from a Uri. This will get the the path for Storage Access
     * Framework Documents, as well as the _data field for the MediaStore and
     * other file-based ContentProviders.<br>
     * <br>
     * Callers should check whether the path is local before assuming it
     * represents a local file.
     *
     * @param context The context.
     */
    private static String getPath(final Context context) {

        Log.d(TAG, "File - " + "Authority: " + currentUri.getAuthority() + ", Fragment: " + currentUri.getFragment() + ", Port: " + currentUri.getPort() + ", Query: " + currentUri.getQuery() + ", Scheme: " + currentUri.getScheme() + ", Host: " + currentUri.getHost() + ", Segments: " + currentUri.getPathSegments().toString());

        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, currentUri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(currentUri)) {
                final String docId = DocumentsContract.getDocumentId(currentUri);
                final String[] split = docId.split(":");
                final String type = split[0];

                String fullPath = getPathFromExtSD(split);
                if (fullPath != "") {
                    return fullPath;
                } else {
                    return null;
                }
            }
            // DownloadsProvider
            else if (isDownloadsDocument(currentUri)) {
                // thanks to https://github.com/hiddentao/cordova-plugin-filepath/issues/34#issuecomment-430129959
                Cursor cursor = null;
                try {
                    cursor = context.getContentResolver().query(currentUri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null);
                    if (cursor != null && cursor.moveToFirst()) {
                        String fileName = cursor.getString(0);
                        String path = Environment.getExternalStorageDirectory().toString() + "/Download/" + fileName;
                        if (!TextUtils.isEmpty(path)) {
                            return path;
                        }
                    }
                } finally {
                    if (cursor != null) cursor.close();
                }
                //
                final String id = DocumentsContract.getDocumentId(currentUri);
                try {
                    final Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                    return getDataColumn(context, contentUri, null);
                } catch (NumberFormatException e) {
                    //In Android 8 and Android P the id is not a number
                    return currentUri.getPath().replaceFirst("^/document/raw:", "").replaceFirst("^raw:", "");
                }
            }
            // MediaProvider
            else if (isMediaDocument(currentUri)) {
                final String docId = DocumentsContract.getDocumentId(currentUri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentUri = MediaStore.getMediaUri(context, currentUri);
                    } else {
                        contentUri = MediaStore.Files.getContentUri("external");
                    }
                }

                final String selection = String.format("%s=%s", MediaStore.Files.FileColumns._ID, split[1]);

                return getDataColumn(context, contentUri, selection);
            } else if (isGoogleDriveUri(currentUri)) {
                return getDriveFilePath(currentUri, context);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(currentUri.getScheme())) {

            // Return the remote address
            if (isGooglePhotosUri(currentUri)) {
                String contentPath = getContentFromSegments(currentUri.getPathSegments());
                if (contentPath != "") {
                    return getPath(context);
                } else {
                    return null;
                }
            }

            if (isGoogleDriveUri(currentUri)) {
                return getDriveFilePath(currentUri, context);
            }

            return getDataColumn(context, currentUri, null);
        }
        // File
        else if ("file".equalsIgnoreCase(currentUri.getScheme())) {
            return currentUri.getPath();
        }

        return null;
    }

    private static String getDriveFilePath(Uri uri, Context context) {
        Uri returnUri = uri;
        Cursor returnCursor = context.getContentResolver().query(returnUri, null, null, null, null);
        /*
         * Get the column indexes of the data in the Cursor,
         *     * move to the first row in the Cursor, get the data,
         *     * and display it.
         * */
        int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
        returnCursor.moveToFirst();
        String name = (returnCursor.getString(nameIndex));
        String size = (Long.toString(returnCursor.getLong(sizeIndex)));
        File file = new File(context.getCacheDir(), name);
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            FileOutputStream outputStream = new FileOutputStream(file);
            int read = 0;
            int maxBufferSize = 1 * 1024 * 1024;
            int bytesAvailable = inputStream.available();

            //int bufferSize = 1024;
            int bufferSize = Math.min(bytesAvailable, maxBufferSize);

            final byte[] buffers = new byte[bufferSize];
            while ((read = inputStream.read(buffers)) != -1) {
                outputStream.write(buffers, 0, read);
            }
            Log.e("File Size", "Size " + file.length());
            inputStream.close();
            outputStream.close();
            Log.e("File Path", "Path " + file.getPath());
            Log.e("File Size", "Size " + file.length());
        } catch (Exception e) {
            Log.e("Exception", e.getMessage());
        }
        return file.getPath();
    }
}