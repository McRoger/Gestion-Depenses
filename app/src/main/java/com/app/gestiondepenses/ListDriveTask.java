package com.app.gestiondepenses;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

public class ListDriveTask extends AsyncTask {

    private final DbxClientV2 mDbxClient;
    private final ListDriveTask.Callback mCallback;
    private Exception mException;

    public interface Callback {
        void onDownloadComplete(Object result);

        void onError(Exception e);
    }

    ListDriveTask(Context context, DbxClientV2 dbxClient, ListDriveTask.Callback callback) {
        mDbxClient = dbxClient;
        mCallback = callback;
    }

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
    protected Object doInBackground(Object[] params) {

        ArrayList<File> files = new ArrayList<>();
        try {
            ListFolderResult result = mDbxClient.files().listFolder("");

            for (Metadata o : result.getEntries()) {

                FileMetadata metadata = (FileMetadata) o;

                extracted(files, metadata);
            }
        } catch (DbxException e) {
            e.printStackTrace();
        }
        return files;
    }

    private void extracted(ArrayList<File> files, FileMetadata metadata) {
        try {
            File path = Environment.getExternalStoragePublicDirectory(
                    "Comptes");
            File file = new File(path, metadata.getName());

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
            }

            if (MainActivity.readFile(file) != null) {
                files.add(file);
            } else {
                try (OutputStream outputStream = new FileOutputStream(file)) {
                    mDbxClient.files().delete(metadata.getPathLower());
                }
            }

        } catch (DbxException | IOException e) {
            mException = e;
        }
    }
}