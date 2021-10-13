package com.app.tasks;


import android.os.AsyncTask;
import android.util.Log;

import com.app.interfaceGestion.Callback;
import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.WriteMode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * Async task to upload a file to a directory
 */
public class UploadFileTask extends AsyncTask<ArrayList<File>,Object,ArrayList<File>>{

    private final DbxClientV2 mDbxClient;
    private final Callback mCallback;
    private Exception mException;


    public UploadFileTask(DbxClientV2 dbxClient, Callback callback) {
        mDbxClient = dbxClient;
        mCallback = callback;
    }

    @Override
    protected ArrayList<File> doInBackground(ArrayList<File>... arrayLists) {
        ArrayList<File> files=new ArrayList<>();
        // Upload to Dropbox
        for( File file: arrayLists[0]) {
            try(InputStream inputStream = new FileInputStream(file)) {
                mDbxClient.files().uploadBuilder("/" + file.getName()) //Path in the user's Dropbox to save the file.
                        .withMode(WriteMode.OVERWRITE) //always overwrite existing file
                        .uploadAndFinish(inputStream);
                files.add(file);
                Log.d("Upload Status", "Success");
            } catch (DbxException | IOException e) {
                e.printStackTrace();
            }
        }

        return files;
    }

    @Override
    protected void onPostExecute(ArrayList<File> o) {
        super.onPostExecute(o);
        if (mException != null) {
            mCallback.onError(mException);
        } else if (o == null) {
            mCallback.onError(null);
        } else {
            mCallback.onTaskComplete(o);
        }
    }
}