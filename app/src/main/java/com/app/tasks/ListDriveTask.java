package com.app.tasks;

import android.os.AsyncTask;

import com.app.gestiondepenses.MainActivity;
import com.app.interfaceGestion.Callback;
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

public class ListDriveTask extends AsyncTask<ArrayList<File>,Void,ArrayList<File>> {

    private final DbxClientV2 mDbxClient;
    private final Callback mCallback;
    private Exception mException;

    private File mPath;

    public ListDriveTask(DbxClientV2 dbxClient, File path, Callback callback) {
        mDbxClient = dbxClient;
        mCallback = callback;
        mPath=path;
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


    @SafeVarargs
    @Override
    protected final ArrayList<File> doInBackground(ArrayList<File>... arrayLists) {

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
            File file = new File(mPath, metadata.getName());

//             Make sure the Downloads directory exists.
            if (!mPath.exists()) {
                if (!mPath.mkdirs()) {
                    mException = new RuntimeException("Unable to create directory: " + mPath);
                }
            } else if (!mPath.isDirectory()) {
                mException = new IllegalStateException("Download path is not a directory: " + mPath);
            }

            // Download the file.
            try (OutputStream outputStream = new FileOutputStream(file)) {
                mDbxClient.files().download(metadata.getPathLower())
                        .download(outputStream);

        } catch (Exception e) {
            e.printStackTrace();
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