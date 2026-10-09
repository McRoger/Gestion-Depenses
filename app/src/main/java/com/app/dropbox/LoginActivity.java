package com.app.dropbox;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.app.gestiondepenses.R;
import com.dropbox.core.android.Auth;
import com.dropbox.core.oauth.DbxCredential;

import java.util.Arrays;
import java.util.List;

public abstract class LoginActivity extends AppCompatActivity {
    private final static boolean USE_SLT = true;

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

    private void setCredentialsOnCreate() {
        SharedPreferences prefs = getSharedPreferences("dropbox-sample", MODE_PRIVATE);
        String serializedCredential = prefs.getString("credential", null);

        if (serializedCredential != null) {
            try {
                DbxCredential credential = DbxCredential.Reader.readFully(serializedCredential);
                DropboxClientFactory.init(credential);
            } catch (Exception e) {
                prefs.edit().remove("credential").apply();
                DropboxClientFactory.clearClient();
            }
        }
    }

    private void setCredentialsOnResume() {
        SharedPreferences prefs = getSharedPreferences("dropbox-sample", MODE_PRIVATE);
        String serializedCredential = prefs.getString("credential", null);

        if (serializedCredential == null) {
            // Reçoit les identifiants si l'utilisateur revient du navigateur d'authentification
            DbxCredential credential = Auth.getDbxCredential();
            if (credential != null) {
                prefs.edit().putString("credential", credential.toString()).apply();
                initAndLoadData(credential);
            }
        } else {
            try {
                DbxCredential credential = DbxCredential.Reader.readFully(serializedCredential);
                initAndLoadData(credential);
            } catch (Exception e) {
                prefs.edit().remove("credential").apply();
                DropboxClientFactory.clearClient();
            }
        }
    }

    private void initAndLoadData(DbxCredential dbxCredential) {
        DropboxClientFactory.init(dbxCredential);
        PicassoClient.init(getApplicationContext(), DropboxClientFactory.getClient());
        loadData();
    }

    protected abstract void loadData();

    public static void startOAuth2Authentication(Context context, String appKey, List<String> scope) {
        if (USE_SLT) {
            Auth.startOAuth2PKCE(context, appKey, DbxRequestConfigFactory.getRequestConfig(), scope);
        } else {
            Auth.startOAuth2Authentication(context, appKey);
        }
    }
}
