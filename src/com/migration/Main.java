package com.migration;

import com.box.sdk.*;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVWriter;
import org.jose4j.lang.StringUtil;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.HashMap;
import java.util.ArrayList;

public class Main {

    private static final int MAX_DEPTH = 0;
    // This is the Id for the Accounts directory used for production.
//    private static final String ACCOUNTS_DIRECTORY = "56475663396";
    // This is the Id for the Accounts directory used for the sandbox.
    private static final String ACCOUNTS_DIRECTORY = "49753601950";
    private static final String FILES_TO_MIGRATE_DIRECTORY = "65741957999";

    private static final String DEFAULT_FILE_PATH = "/Users/zacharyfield/Desktop/sacscoc-box-migration-logs/";
    private static final String DEFAULT_LOG_FILE_NAME = "sacscoc-box-file-migration-log";

    private static final String BOX_FOLDER_MAPPING_INFO_CSV = "/Users/zacharyfield/Desktop/SACSCOC Box Migration - Legacy Id Mapping - legacy-id-mapping (1).csv";
    private static final String BOX_CHILD_LIST_FILE_PATH = "/Users/zacharyfield/Desktop/";

    private static final String[] MIGRATION_LOG_COLUMNS = new String[] {"Origin Folder Name", "Origin Folder ID", "Destination Folder Name","Destination Folder ID", "Migrated Folder Name", "Migrated Folder ID", "Number of Files", "Status", "Details"} ;

    public static void main(String[] args) {
        // Turn off logging to prevent polluting the output.
        Logger.getLogger("com.box.sdk").setLevel(Level.OFF);

        System.out.println("BEGIN: Box File Migration");

        try {
//            runBoxExample();
            runBoxFileMigration();
//            runGenerateCsvOfBoxChildren();
        } catch (BoxAPIException ex) {
            System.out.println("BoxAPIException - Error: " + ex.getMessage());
            System.out.println("API Response: " + ex.getResponse());
        } catch (Exception ex) {
            System.out.println("Error: " + ex.getMessage());
            System.out.println("Stack Trace: " + ex.getStackTrace());
        }
    }

    private static final Integer MAX_FOLDERS_TO_MIGRATE = 21;
    private static void runBoxFileMigration() throws IOException {
        // Set up our CSV log file that will contain the results of each migration.
        File logFile = null;
        CSVWriter writer = null;
        long startTime = System.nanoTime();

        try {
            logFile = getNewUniqueFileInPath(DEFAULT_FILE_PATH, DEFAULT_LOG_FILE_NAME);

            writer = new CSVWriter(new FileWriter(logFile));
            writer.writeNext(MIGRATION_LOG_COLUMNS);
        } catch (Exception ex) {
            System.out.println("No Files Migrated. Error creating the log file: " + ex.getMessage());
            return;
        }

        // If there is some error creating the csv file we need to generate, don't try to do the migration.
        if (logFile == null || writer == null) {
            System.out.println("Unable to generate the log file. No files were migrated.");
            return;
        }

        // Establish a connection to the API.
        BoxApiService.getAPI();

        try {
            // Now get the folders in the directory that contains the files which need to be migrated.
            BoxFolder migrateDirectory = new BoxFolder(BoxApiService.getAPI(), FILES_TO_MIGRATE_DIRECTORY);

            System.out.println("Begin matching folders to migrate with their destination by name");
            // Now get the folders in the directory where the files will be migrated to. (i.e. The destination folders)
            // Group them by name. Again, the names should correspond to the names of an account.
            HashMap<String, BoxFolder> destinationFoldersByName = BoxApiService.getChildFoldersByName(ACCOUNTS_DIRECTORY);

            // Once we have two maps, we will use the keys from the map containing the files to migrate to build
            // The BoxFileMigrator instances.
            // These BoxFileMigrators will contain the BoxFolders (Destination and Origin)
            // If there is only one BoxFolder, we will assume that we couldn't find a matching destination folder.
            // In this circumstance, we will write to our CSV log the name of Origin folder so we can resolve these name differences in future iterations.
            ArrayList<BoxFileMigrator> boxFileMigrators = new ArrayList<BoxFileMigrator>();

            Integer counter = 0;
            // First we will create the BoxFolderMigrators by matching the folders we must migrate to the account folders by name.
            for (BoxItem.Info itemInfo : migrateDirectory) {
                // TODO Remove this for real migration.
//                if (counter >= MAX_FOLDERS_TO_MIGRATE) {
//                    break;
//                }

                if (itemInfo instanceof BoxFolder.Info) {
                    BoxFolder destinationFolder = destinationFoldersByName.get(itemInfo.getName());

                    boxFileMigrators.add(new BoxFileMigrator((BoxFolder)itemInfo.getResource(), destinationFolder));
                }
                counter++;
            }
            System.out.println("Finished matching folders to migrate with their destination by name.");

            // Next we will create additional BoxFolderMigrators with a provided CSV that contains the destination box folder Id to the box folder that needs to be migrated.
//            boxFileMigrators.addAll(getBoxFileMigratorsFromFolderMappingFile());

            System.out.println("Beginning to migrate each folder...");
            for (BoxFileMigrator migrator : boxFileMigrators) {
                BoxFileMigrator.MigrationResult result = migrator.migrateFolders();
                result.writeToCsv(writer);
                writer.flush();
            }
        } catch (Exception ex) {
            System.out.println("Errors with Box Migration: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            writer.flush();
            writer.close();
        }
        long endTime = System.nanoTime();
        long timeElapsed = (endTime - startTime)/1000000;
        long timeElapsedMinutes = timeElapsed / 60000;
        System.out.println("COMPLETED: Box File Migration. Duration: " + String.valueOf(timeElapsed) + " (ms) or " + String.valueOf(timeElapsedMinutes) + " minutes");
    }

    private static ArrayList<BoxFileMigrator> getBoxFileMigratorsFromFolderMappingFile() {
        System.out.println("Begin matching folders to migrate with their destination by folder Ids.");
        ArrayList<BoxFileMigrator> mappedFolderMigrators = new ArrayList<BoxFileMigrator>();

        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(BOX_FOLDER_MAPPING_INFO_CSV));
            reader.readLine(); // Skip the first line which should just be column names.

            String mappingData = null;
            while ((mappingData = reader.readLine()) != null) {
                String[] splitData = mappingData.split(",");
                String folderToMigrateId = splitData[1];
                String destinationFolderId = splitData[2];

                System.out.println("Folder To Migrate Id: " + folderToMigrateId);
                System.out.println("Destination Folder Id: " + destinationFolderId);

                BoxFolder folderToMigrate = new BoxFolder(BoxApiService.getAPI(), folderToMigrateId);
                BoxFolder destinationFolder = null;
                if (destinationFolderId != null && !destinationFolderId.isBlank() && (destinationFolderId.length() == 11)) {
                    destinationFolder = new BoxFolder(BoxApiService.getAPI(), destinationFolderId);
                }

                BoxFileMigrator fileMigrator = new BoxFileMigrator(folderToMigrate, destinationFolder);
                fileMigrator.print();
                mappedFolderMigrators.add(fileMigrator);
            }

            reader.close();
        } catch (Exception ex) {
            System.out.println("ERROR Building File Migrators From CSV: " + ex.getMessage());
            return new ArrayList<BoxFileMigrator>();
        }

        System.out.println("Finished matching folders to migrate with their destination by folder Ids.");
        return mappedFolderMigrators;
    }

    private static void runGenerateCsvOfBoxChildren() throws IOException {
        // Establish a connection to the API.
        BoxApiService.getAPI();

        BoxFolder folderToList = new BoxFolder(BoxApiService.getAPI(), ACCOUNTS_DIRECTORY);
        BoxFolder.Info folderInfo = folderToList.getInfo();
        String baseFileName = folderInfo.getName() + "-contents-list";

        // Set up our CSV log file that will contain the results of each migration.
        File logFile = null;
        CSVWriter writer = null;
        try {
            logFile = getNewUniqueFileInPath(BOX_CHILD_LIST_FILE_PATH, baseFileName);

            writer = new CSVWriter(new FileWriter(logFile));
            writer.writeNext(new String[] { "Folder Name", "Folder ID" } );
        } catch (Exception ex) {
            System.out.println("Error creating the log file: " + ex.getMessage());
            return;
        }

        // If there is some error creating the csv file we need to generate, don't try to do the migration.
        if (logFile == null || writer == null) {
            System.out.println("Unable to generate the log file.");
            return;
        }

        try {
            for (BoxItem.Info itemInfo : folderToList) {
                writer.writeNext(new String[] { itemInfo.getName(), itemInfo.getID() });
            }
        } catch (Exception ex) {
            System.out.println("Errors with Box Migration: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            writer.flush();
            writer.close();
        }
    }

    private static File getNewUniqueFileInPath(String filePath, String fileName) {
        String currentFileName = fileName;
        Integer currentCounter = 1;
        // See if the specified file name exists in the file path. If so, append an integer until we get a unique file name then return.
        File currentFile = new File(filePath + currentFileName + ".csv");

        if (!currentFile.exists()) {
            return currentFile;
        }

        while (currentFile.exists() && !currentFile.isDirectory()) {
            currentFile = new File(filePath + fileName + "-" + currentCounter + ".csv");

            if (!currentFile.exists()) {
                return currentFile;
            }
            currentCounter++;
        }

        return null;
    }

    /**
     * @description This code was just an example used to demonstrate how to connect to the box API and read folder contents.
     */
    private static void runBoxExample() {
        BoxAPIConnection api = BoxApiService.getAPI();

        BoxUser.Info userInfo = BoxUser.getCurrentUser(api).getInfo();
        System.out.format("Welcome, %s <%s>!\n\n", userInfo.getName(), userInfo.getLogin());
        System.out.println("User Id: " + userInfo.getID());

        BoxFolder rootFolder = BoxFolder.getRootFolder(api);
        listFolder(rootFolder, 0);
        System.out.println("Print folders in Accounts directory.");
        BoxFolder accountsDirectory = new BoxFolder(api, FILES_TO_MIGRATE_DIRECTORY);
        listFolder(accountsDirectory, 0);
    }

    private static void listFolder(BoxFolder folder, int depth) {
        for (BoxItem.Info itemInfo : folder) {
            String indent = "";
            for (int i = 0; i < depth; i++) {
                indent += "    ";
            }

            System.out.println(indent + itemInfo.getName() + ": " + itemInfo.getID());
            if (itemInfo instanceof BoxFolder.Info) {
                BoxFolder childFolder = (BoxFolder) itemInfo.getResource();
                if (depth < MAX_DEPTH) {
                    listFolder(childFolder, depth + 1);
                }
            }
        }
    }
}
