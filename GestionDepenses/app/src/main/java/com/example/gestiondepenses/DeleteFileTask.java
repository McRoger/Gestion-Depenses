package com.example.gestiondepenses;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

class DeleteFileTask extends AsyncTask {

    private final Context mContext;
    private final DbxClientV2 mDbxClient;
    private final DeleteFileTask.Callback mCallback;
    private Exception mException;

    public interface Callback {
        void onDeleteComplete(Object result);
        void onError(Exception e);
    }

    DeleteFileTask(Context context, DbxClientV2 dbxClient, DeleteFileTask.Callback callback) {
        mContext = context;
        mDbxClient = dbxClient;
        mCallback = callback;
    }

    @Override
    protected void onPostExecute(Object result) {
        super.onPostExecute(result);
        if (mException != null) {
            mCallback.onError(mException);
        } else {
            mCallback.onDeleteComplete(result);
        }
    }

    @Override
    protected  Object doInBackground(Object[] params) {

        ArrayList<File> files= new ArrayList<>();
        for(  File file:  (ArrayList<File>) params[0]) {
            try {
                FileMetadata metadata= (FileMetadata)  mDbxClient.files().getMetadata("/"+file.getName());
                mDbxClient.files().delete(metadata.getPathLower());
                files.add(file);

            } catch (DbxException e) {
                mException = e;
            }
        }

        return  files;
    }
}