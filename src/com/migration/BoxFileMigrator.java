package com.migration;

import com.opencsv.CSVWriter;

import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import com.box.sdk.BoxItem;
import com.box.sdk.BoxFolder;

/**
 * @description This class is used to represent the relationship between the old box folder, the new box folder destination, and the Salesforce SObject (Account) that the docs belong to
 */
public class BoxFileMigrator {

    public BoxFileMigrator(BoxFolder originFolder, BoxFolder destinationFolder) {
        if (originFolder == null) {
            throw new IllegalArgumentException("The origin folder can not be null.");
        }

        Origin = originFolder;
        Destination = destinationFolder;
    }

    /**
     * @description The folder that contains the data which needs to be migrated.
     */
    public BoxFolder Origin;

    /**
     * @description The folder where the data needs to be sent.
     */
    public BoxFolder Destination;

    public void print() {
        System.out.println(getMigrationDetails());
    }

    private String getMigrationDetails() {
        String migrationDetails = "Original Box Folder: " + Origin.getInfo().getName() + " - " + Origin.getInfo().getID();

        if (Destination != null) {
            migrationDetails += " New Box Folder: " + Destination.getInfo().getName() + " - " + Destination.getInfo().getID();
        } else {
            migrationDetails += " New Box Folder: null - null";
        }

        return migrationDetails;
    }

    public MigrationResult migrateFolders() {
        MigrationResult result = new MigrationResult(Origin.getInfo());

        System.out.println("Migrate Files From Folder: " + Origin.getInfo().getName());

        // If the Destination is null, we couldn't find an exact match to the origin folder.
        // Therefore we don't need to do any actual migration so return early.
        if (Destination == null) {
            CopyResult copyResult = new CopyResult();
            copyResult.Status = "No Match";
            copyResult.Details = "No Match for Folder Named: " + Origin.getInfo().getName();
            result.FileCopyResults.add(copyResult);
            return result;
        }

        HashMap<String, BoxFolder> destinationFoldersByName = BoxApiService.getChildFoldersByName(Destination);

        for (BoxItem.Info itemInfo : Origin) {
            // For each folder in the origin folder, look to see if a folder by that name already exists in the destination.
            // If so, don't do any migration but still create a log entry.
            if (itemInfo instanceof BoxFolder.Info) {
                CopyResult copyResult = new CopyResult();
                copyResult.DestinationFolderInfo = Destination.getInfo();
                copyResult.MigratedFolderInfo = (BoxFolder.Info)itemInfo;

                BoxFolder existingFolder = destinationFoldersByName.get(itemInfo.getName());
                if (existingFolder == null) {
                    try {
                        // No existing folder so do the migration.
                        // TODO The actual migration. For now, lets just generate a log file to verify the output.
                        BoxFolder folderToMigrate = (BoxFolder)itemInfo.getResource();
                        folderToMigrate.copy(Destination);
                        copyResult.Status = "Success";
                    } catch (Exception ex) {
                        copyResult.Status = "Error";
                        copyResult.Details = "Error: " + ex.getMessage();
                    }
                } else {
                    // A folder by that name already exists in the destination. Assume we already migrated these files, write a log entry and continue.
                    copyResult.Status = "Folder Already Exists";
                    copyResult.Details = "Folder " + itemInfo.getName() + " already exists in destination " + copyResult.DestinationFolderInfo.getName();
                }
                result.FileCopyResults.add(copyResult);
            }
        }

        return result;
    }

    private static final String EMPTY_VALUE = " ";

    /**
     * @description A summary of all the results. It is used to write results to CSV.
     */
    public class MigrationResult {

        public MigrationResult(BoxFolder.Info originInfo) {
            OriginFolderInfo = originInfo;
            FileCopyResults = new ArrayList<CopyResult>();
        }

        public BoxFolder.Info OriginFolderInfo;
        public ArrayList<CopyResult> FileCopyResults;

        public void writeToCsv(CSVWriter csvToWrite) {
            for (CopyResult result : FileCopyResults) {
                ArrayList<String> csvRowData = startNewCsvRow();
                csvRowData.addAll(result.getCsvRowData());
                csvToWrite.writeNext(csvRowData.toArray(new String[csvRowData.size()]));
            }
        }

        public ArrayList<String> startNewCsvRow() {
            ArrayList<String> newCsvRow = new ArrayList<String>();
            newCsvRow.add(OriginFolderInfo.getName());
            newCsvRow.add(OriginFolderInfo.getID());
            return newCsvRow;
        }
    }

    /**
     * @description The result of copying one folder/file.
     */
    public class CopyResult {
        public BoxFolder.Info DestinationFolderInfo;
        public BoxFolder.Info MigratedFolderInfo;
        public String Details;
        public String Status;

        public ArrayList<String> getCsvRowData() {
            ArrayList<String> csvRowData = new ArrayList<String>();

            if (DestinationFolderInfo == null) {
                csvRowData.add(EMPTY_VALUE);
                csvRowData.add(EMPTY_VALUE);
            } else {
                csvRowData.add(DestinationFolderInfo.getName());
                csvRowData.add(DestinationFolderInfo.getID());
            }

            if (MigratedFolderInfo == null) {
                csvRowData.add(EMPTY_VALUE);
                csvRowData.add(EMPTY_VALUE);
                csvRowData.add("0");
            } else {
                csvRowData.add(MigratedFolderInfo.getName());
                csvRowData.add(MigratedFolderInfo.getID());
                csvRowData.add(String.valueOf(MigratedFolderInfo.getSize()));
            }

            csvRowData.add(Status);
            csvRowData.add(Details);
            return csvRowData;
        }
    }
}
