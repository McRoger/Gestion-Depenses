package com.example.gestiondepenses;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.widget.ProgressBar;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * Task to download a file from Dropbox and put it in the Downloads folder
 */
class DownloadFileTask extends AsyncTask {

    private final Context mContext;
    private final DbxClientV2 mDbxClient;
    private final Callback mCallback;
    private Exception mException;

    public interface Callback {
        void onDownloadComplete(Object result);
        void onError(Exception e);
    }

    DownloadFileTask(Context context, DbxClientV2 dbxClient, Callback callback) {
        mContext = context;
        mDbxClient = dbxClient;
        mCallback = callback;
    }
    private ProgressDialog progressDialog;


    @Override
    protected void onPostExecute(Object result) {
        super.onPostExecute(result);

        if (mException != null) {
            mCallback.onError(mException);
        } else {
            mCallback.onDownloadComplete(result);
        }
    }

    @Override
    protected  Object doInBackground(Object[] params) {

        ArrayList<File> files=new ArrayList<>();

        for( File file:  (ArrayList<File>) params[0]) {
            try {

                FileMetadata metadata= (FileMetadata)  mDbxClient.files().getMetadata("/"+file.getName());
                File path = Environment.getExternalStoragePublicDirectory(
                        "Comptes");
                file = new File(path, metadata.getName());

//             Make sure the Downloads directory exists.
                if (!path.exists()) {
                    if (!path.mkdirs()) {
                        mException = new RuntimeException("Unable to create directory: " + path);
                    }
                } else if (!path.isDirectory()) {
                    mException = new IllegalStateException("Download path is not a directory: " + path);
                    return null;
                }

                // Download the file.
                try (OutputStream outputStream = new FileOutputStream(file)) {
                    mDbxClient.files().download(metadata.getPathLower(), metadata.getRev())
                            .download(outputStream);
                    mDbxClient.files().delete(metadata.getPathLower());
                }

                // Tell android about the file
                Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                intent.setData(Uri.fromFile(file));
                mContext.sendBroadcast(intent);
                files.add(file);

            } catch (DbxException | IOException e) {
                mException = e;
            }
        }

        return files;
    }

}