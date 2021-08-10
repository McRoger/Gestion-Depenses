package com.example.gestiondepenses;


import android.os.AsyncTask;
import android.util.Log;

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
class UploadFileTask extends AsyncTask{

    private ArrayList<File> mFiles;
    private final DbxClientV2 mDbxClient;
    private final Callback mCallback;
    private Exception mException;

    public interface Callback {
        void onUploadComplete(Object o);
        void onError(Exception e);
    }

    UploadFileTask(DbxClientV2 dbxClient, Callback callback) {
        mDbxClient = dbxClient;
        mCallback = callback;
    }

    @Override
    protected void onPostExecute(Object o) {
        super.onPostExecute(o);
        if (mException != null) {
            mCallback.onError(mException);
        } else if (o == null) {
            mCallback.onError(null);
        } else {
            mCallback.onUploadComplete(o);
        }
    }

    @Override
    protected ArrayList<File> doInBackground(Object[] params) {

        ArrayList<File> files=new ArrayList<>();
            // Upload to Dropbox
            for( File file:  (ArrayList<File>) params[0]) {
                try {
                InputStream inputStream = new FileInputStream(file);
                mDbxClient.files().uploadBuilder("/" + file.getName()) //Path in the user's Dropbox to save the file.
                        .withMode(WriteMode.OVERWRITE) //always overwrite existing file
                        .uploadAndFinish(inputStream);
                files.add(file);
                Log.d("Upload Status", "Success");
                } catch (DbxException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return files;
    }

}