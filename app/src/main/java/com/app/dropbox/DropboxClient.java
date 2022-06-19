package com.app.dropbox;

import com.dropbox.core.DbxAppInfo;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxOAuth1AccessToken;
import com.dropbox.core.DbxOAuth1Upgrader;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.json.JsonReader;
import com.dropbox.core.v2.DbxClientV2;

public class DropboxClient {
    private DropboxClient() {
    }

    public static DbxClientV2 getClient(String accessToken) {
        // Create Dropbox client
        DbxRequestConfig config = new DbxRequestConfig("dropbox/sample-app");
//        initAndLoadData(accessToken);
        return new DbxClientV2(config, accessToken);
    }
}