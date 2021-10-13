package com.app.tasks;

import android.os.AsyncTask;

import com.app.interfaceGestion.Callback;
import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;

import java.io.File;
import java.util.ArrayList;

public class DeleteFileTask extends AsyncTask<ArrayList<File>, Void, ArrayList<File>> {

    private final DbxClientV2 mDbxClient;
    private final Callback mCallback;
    private Exception mException;

    public DeleteFileTask(DbxClientV2 dbxClient, Callback callback) {
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
                mDbxClient.files().delete(metadata.getPathLower());
                files.add(file);

            } catch (DbxException e) {
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