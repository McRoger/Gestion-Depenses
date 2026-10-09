package com.app.tasks;

import android.util.Log;

import com.app.interfaceGestion.Callback;
import com.app.utils.AppExecutors;
import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Task to delete files from Dropbox asynchronously.
 */
public class DeleteFileTask {

    private final DbxClientV2 mDbxClient;
    private final Callback mCallback;

    public DeleteFileTask(DbxClientV2 dbxClient, Callback callback) {
        mDbxClient = dbxClient;
        mCallback = callback;
    }

    public void execute(final List<File> filesToDelete) {
        AppExecutors.getInstance().networkIO().execute(() -> {
            final ArrayList<File> deletedFiles = new ArrayList<>();
            Exception exception = null;

            if (mDbxClient != null && filesToDelete != null) {
                for (File file : filesToDelete) {
                    try {
                        FileMetadata metadata = (FileMetadata) mDbxClient.files().getMetadata("/" + file.getName());
                        mDbxClient.files().deleteV2(metadata.getPathLower());
                        deletedFiles.add(file);
                    } catch (DbxException e) {
                        Log.e("DeleteFileTask", "Error deleting file: " + file.getName(), e);
                        exception = e;
                    }
                }
            }

            final Exception finalException = exception;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (mCallback != null) {
                    if (finalException != null && deletedFiles.isEmpty()) {
                        mCallback.onError(finalException);
                    } else {
                        mCallback.onTaskComplete(deletedFiles);
                    }
                }
            });
        });
    }
}
