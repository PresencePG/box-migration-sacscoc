package com.migration;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxFolder;
import com.box.sdk.BoxItem;
import com.box.sdk.BoxConfig;
import com.box.sdk.BoxDeveloperEditionAPIConnection;
import com.box.sdk.DeveloperEditionEntityType;
import com.box.sdk.IAccessTokenCache;
import com.box.sdk.InMemoryLRUAccessTokenCache;

import java.io.IOException;
import java.io.Reader;
import java.io.FileReader;
import java.util.HashMap;

/**
 * @description This class is used to easily access the Box API throughout the program since only one API
 *              connection is needed per execution.
 */
public class BoxApiService {

    private static final String JSON_CONFIG = "src/config/sacscoc-box-app-config.json";
    private static final String USER_ID = "3668833979";

    private static BoxAPIConnection api;

    private static final int DEFAULT_MAX_DEPTH = 0;
    private static final int DEFAULT_MAX_TOKEN_ENTRIES = 100;

    private BoxApiService() {}

    public static BoxAPIConnection getAPI() {
        if (api == null) {
            try {
                Reader reader = new FileReader(JSON_CONFIG);
                BoxConfig boxConfig = BoxConfig.readFrom(reader);

                IAccessTokenCache accessTokenCache = new InMemoryLRUAccessTokenCache(DEFAULT_MAX_TOKEN_ENTRIES);
                api = new BoxDeveloperEditionAPIConnection(USER_ID, DeveloperEditionEntityType.USER, boxConfig, accessTokenCache);
            } catch (IOException ex) {
                System.out.println("Unable to open JSON Config to connect to box Api. Error: " + ex.getMessage());
                return null;
            }
        }

        return api;
    }

    public static HashMap<String, BoxFolder> getChildFoldersByName(String boxFolderId) {
        BoxFolder folder = new BoxFolder(BoxApiService.getAPI(), boxFolderId);
        return getChildFoldersByName(folder);
    }

    public static HashMap<String, BoxFolder> getChildFoldersByName(BoxFolder folder) {
        HashMap<String, BoxFolder> childFoldersByName = new HashMap<String, BoxFolder>();

        for (BoxItem.Info itemInfo : folder) {
            if (itemInfo instanceof BoxFolder.Info) {
                childFoldersByName.put(itemInfo.getName(), (BoxFolder)itemInfo.getResource());
            }
        }

        return childFoldersByName;
    }
}

