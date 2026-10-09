package com.app.tasks;

import android.util.Log;

import com.app.interfaceGestion.Callback;
import com.app.models.Expense;
import com.app.utils.AppExecutors;
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

/**
 * Task to list files in Dropbox drive asynchronously.
 */
public class ListDriveTask {

    private final DbxClientV2 mDbxClient;
    private final Callback mCallback;
    private final File mPath;

    public ListDriveTask(DbxClientV2 dbxClient, File path, Callback callback) {
        mDbxClient = dbxClient;
        mCallback = callback;
        mPath = path;
    }

    public void execute() {
        AppExecutors.getInstance().networkIO().execute(() -> {
            final ArrayList<File> files = new ArrayList<>();
            Exception exception = null;

            if (mDbxClient != null && mPath != null) {
                if (!mPath.exists() && !mPath.mkdirs()) {
                    Log.e("ListDriveTask", "Unable to create directory: " + mPath);
                }

                try {
                    ListFolderResult result = mDbxClient.files().listFolder("");

                    for (Metadata o : result.getEntries()) {
                        if (o instanceof FileMetadata) {
                            FileMetadata metadata = (FileMetadata) o;
                            File file = new File(mPath, metadata.getName());

                            try (OutputStream outputStream = new FileOutputStream(file)) {
                                mDbxClient.files().download(metadata.getPathLower())
                                        .download(outputStream);
                            }

                            if (Expense.fromFile(file) != null) {
                                files.add(file);
                            } else {
                                try {
                                    mDbxClient.files().deleteV2(metadata.getPathLower());
                                } catch (DbxException ignored) {
                                }
                                if (file.exists()) {
                                    file.delete();
                                }
                            }
                        }
                    }
                } catch (DbxException | IOException e) {
                    Log.e("ListDriveTask", "Error listing Dropbox files", e);
                    exception = e;
                }
            }

            final Exception finalException = exception;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (mCallback != null) {
                    if (finalException != null && files.isEmpty()) {
                        mCallback.onError(finalException);
                    } else {
                        mCallback.onTaskComplete(files);
                    }
                }
            });
        });
    }
}
