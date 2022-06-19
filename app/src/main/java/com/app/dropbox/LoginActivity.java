package com.app.dropbox;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.app.gestiondepenses.MainActivity;
import com.app.gestiondepenses.R;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.android.Auth;
import com.dropbox.core.json.JsonReadException;
import com.dropbox.core.oauth.DbxCredential;

import java.util.Arrays;
import java.util.List;

public abstract class LoginActivity extends AppCompatActivity {
    private final static boolean USE_SLT = true; //If USE_SLT is set to true, our Android example

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setCredentialsOnCreate();
    }


    @Override
    protected void onResume() {
        super.onResume();

        setCredentialsOnResume();
    }

//    private void storeDropboxCredentials() {
//        DbxCredential dbxCredential = Auth.getDbxCredential();
//        String accessToken = dbxCredential.getAccessToken();
//        String refreshToken = dbxCredential.getRefreshToken();
//        Long expiresAt = dbxCredential.getExpiresAt();
//
//        SharedPreferences preferences = getSharedPreferences(
//                "dropbox-sample", MODE_PRIVATE);
//        if (accessToken != null) {
//            preferences.edit().putString(
//                    AppPreferences.DROPBOX_ACCESS_TOKEN, accessToken).apply();
//        }
//        if (refreshToken != null) {
//            preferences.edit().putString(
//                    AppPreferences.DROPBOX_REFRESH_TOKEN, refreshToken).apply();
//        }
//        if (expiresAt != null) {
//            preferences.edit().putLong(
//                    AppPreferences.DROPBOX_EXPIRES_AT, expiresAt).apply();
//        }
//    }

    private void setCredentialsOnCreate(){
        SharedPreferences prefs = getSharedPreferences("dropbox-sample", MODE_PRIVATE);

            String serailizedCredental = prefs.getString("credential", null);

            if (serailizedCredental == null) {
                startOAuth2Authentication(getApplicationContext(), getString(R.string.APP_KEY), Arrays.asList("account_info.read", "files.content.write","files.content.read"));

                DbxCredential credential = Auth.getDbxCredential();

                if (credential != null) {
                    prefs.edit().putString("credential", credential.toString()).apply();
                    initAndLoadData(credential);
                }
            } else {
                try {
                    DbxCredential credential = DbxCredential.Reader.readFully(serailizedCredental);
                    DropboxClientFactory.init(credential);
                } catch (JsonReadException e) {
                    throw new IllegalStateException("Credential data corrupted: " + e.getMessage());
                }
            }

        String uid = Auth.getUid();
        String storedUid = prefs.getString("user-id", null);
        if (uid != null && !uid.equals(storedUid)) {
            prefs.edit().putString("user-id", uid).apply();
        }
    }

    private void setCredentialsOnResume(){
        SharedPreferences prefs = getSharedPreferences("dropbox-sample", MODE_PRIVATE);

            String serailizedCredental = prefs.getString("credential", null);

            if (serailizedCredental == null) {
                DbxCredential credential = Auth.getDbxCredential();

                if (credential != null) {
                    prefs.edit().putString("credential", credential.toString()).apply();
                    initAndLoadData(credential);
                }
            } else {
                try {
                    DbxCredential credential = DbxCredential.Reader.readFully(serailizedCredental);
                    initAndLoadData(credential);
                } catch (JsonReadException e) {
                    throw new IllegalStateException("Credential data corrupted: " + e.getMessage());
                }
            }



        String uid = Auth.getUid();
        String storedUid = prefs.getString("user-id", null);
        if (uid != null && !uid.equals(storedUid)) {
            prefs.edit().putString("user-id", uid).apply();
        }
    }

    private void initAndLoadData(String accessToken) {
        DropboxClientFactory.init(accessToken);
        PicassoClient.init(getApplicationContext(), DropboxClientFactory.getClient());
//        loadData();
    }

    private void initAndLoadData(DbxCredential dbxCredential) {
        DropboxClientFactory.init(dbxCredential);
        PicassoClient.init(getApplicationContext(), DropboxClientFactory.getClient());
//        loadData();
    }

    protected abstract void loadData();

//    public static boolean hasToken() {
//        SharedPreferences prefs = getSharedPreferences("com.app.gestiondepenses", MODE_PRIVATE);
//        if (USE_SLT) {
//            return prefs.getString("credential", null) != null;
//        } else {
//            String accessToken = prefs.getString("access-token", null);
//            return accessToken != null;
//        }
//    }

    public void getAccessToken() {
        String accessToken = Auth.getOAuth2Token(); //generate Access Token
        if (accessToken != null) {
            //Store accessToken in SharedPreferences
//            SharedPreferences prefs = getSharedPreferences("com.app.gestiondepenses", Context.MODE_PRIVATE);
//            prefs.edit().putString("access-token", accessToken).apply();

//            DbxCredential credential = Auth.getDbxCredential();
//
//            if (credential != null) {
//                prefs.edit().putString("credential", credential.toString()).apply();
//                initAndLoadData(credential);
//            }

            //Proceed to MainActivity
//            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
//            startActivity(intent);
        }
    }

    public static void startOAuth2Authentication(Context context, String app_key, List<String> scope) {
        if (USE_SLT) {
            Auth.startOAuth2PKCE(context, app_key, DbxRequestConfigFactory.getRequestConfig(), scope);
        } else {
            Auth.startOAuth2Authentication(context, app_key);
        }
    }
}
