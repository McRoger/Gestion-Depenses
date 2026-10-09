package com.app.tasks;

import android.util.Log;

import com.app.interfaceGestion.Callback;
import com.app.utils.AppExecutors;
import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.WriteMode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Task to upload files to Dropbox asynchronously.
 */
public class UploadFileTask {

    private final DbxClientV2 mDbxClient;
    private final Callback mCallback;

    public UploadFileTask(DbxClientV2 dbxClient, Callback callback) {
        mDbxClient = dbxClient;
        mCallback = callback;
    }

    public void execute(final List<File> filesToUpload) {
        AppExecutors.getInstance().networkIO().execute(() -> {
            final ArrayList<File> uploadedFiles = new ArrayList<>();
            Exception exception = null;

            if (mDbxClient != null && filesToUpload != null) {
                for (File file : filesToUpload) {
                    try (InputStream inputStream = new FileInputStream(file)) {
                        mDbxClient.files().uploadBuilder("/" + file.getName())
                                .withMode(WriteMode.OVERWRITE)
                                .uploadAndFinish(inputStream);
                        uploadedFiles.add(file);
                        Log.d("Upload Status", "Success: " + file.getName());
                    } catch (DbxException | IOException e) {
                        Log.e("Upload Status", "Error uploading file: " + file.getName(), e);
                        exception = e;
                    }
                }
            }

            final Exception finalException = exception;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (mCallback != null) {
                    if (finalException != null && uploadedFiles.isEmpty()) {
                        mCallback.onError(finalException);
                    } else {
                        mCallback.onTaskComplete(uploadedFiles);
                    }
                }
            });
        });
    }
}
