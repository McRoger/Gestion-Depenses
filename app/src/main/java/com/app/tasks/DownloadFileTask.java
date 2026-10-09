package com.app.tasks;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.app.interfaceGestion.Callback;
import com.app.utils.AppExecutors;
import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Task to download files from Dropbox asynchronously.
 */
public class DownloadFileTask {

    private final Context mContext;
    private final DbxClientV2 mDbxClient;
    private final Callback mCallback;
    private final File mPath;

    public DownloadFileTask(Context context, DbxClientV2 dbxClient, File path, Callback callback) {
        mContext = context != null ? context.getApplicationContext() : null;
        mDbxClient = dbxClient;
        mCallback = callback;
        mPath = path;
    }

    public void execute(final List<File> filesToDownload) {
        AppExecutors.getInstance().networkIO().execute(() -> {
            final ArrayList<File> downloadedFiles = new ArrayList<>();
            Exception exception = null;

            if (mDbxClient != null && filesToDownload != null && mPath != null) {
                if (!mPath.exists() && !mPath.mkdirs()) {
                    Log.e("DownloadFileTask", "Unable to create directory: " + mPath);
                }

                for (File fileSpec : filesToDownload) {
                    try {
                        FileMetadata metadata = (FileMetadata) mDbxClient.files().getMetadata("/" + fileSpec.getName());
                        File targetFile = new File(mPath, metadata.getName());

                        try (OutputStream outputStream = new FileOutputStream(targetFile)) {
                            mDbxClient.files().download(metadata.getPathLower(), metadata.getRev())
                                    .download(outputStream);
                            mDbxClient.files().delete(metadata.getPathLower());
                        }

                        if (mContext != null) {
                            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                            intent.setData(Uri.fromFile(targetFile));
                            mContext.sendBroadcast(intent);
                        }

                        downloadedFiles.add(targetFile);
                    } catch (DbxException | IOException e) {
                        Log.e("DownloadFileTask", "Error downloading file: " + fileSpec.getName(), e);
                        exception = e;
                    }
                }
            }

            final Exception finalException = exception;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (mCallback != null) {
                    if (finalException != null && downloadedFiles.isEmpty()) {
                        mCallback.onError(finalException);
                    } else {
                        mCallback.onTaskComplete(downloadedFiles);
                    }
                }
            });
        });
    }
}
