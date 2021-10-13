package com.app.tasks;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;

import com.app.interfaceGestion.Callback;
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
@SuppressLint("StaticFieldLeak")
public
class DownloadFileTask extends AsyncTask<ArrayList<File>,Object,ArrayList<File>> {

    private final Context mContext;
    private final DbxClientV2 mDbxClient;
    private final Callback mCallback;
    private Exception mException;

    public DownloadFileTask(Context context, DbxClientV2 dbxClient, Callback callback) {
        mContext = context;
        mDbxClient = dbxClient;
        mCallback = callback;
    }

    @SafeVarargs
    @Override
    protected final ArrayList<File> doInBackground(ArrayList<File>... arrayLists) {
        ArrayList<File> files = new ArrayList<>();

        for (File file : arrayLists[0]) {
            try {

                FileMetadata metadata = (FileMetadata) mDbxClient.files().getMetadata("/" + file.getName());
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

    @Override
    protected void onPostExecute(ArrayList<File> result) {
        super.onPostExecute(result);

        if (mException != null) {
            mCallback.onError(mException);
        } else {
            mCallback.onTaskComplete(result);
        }
    }
}