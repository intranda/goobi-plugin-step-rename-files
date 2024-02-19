package de.intranda.goobi.plugins;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.goobi.beans.Process;

import de.sub.goobi.helper.exceptions.SwapException;
import ugh.dl.ContentFile;
import ugh.dl.DigitalDocument;
import ugh.dl.FileSet;
import ugh.dl.Fileformat;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.WriteException;

public class MetsFileUpdater {
    private static MetsFileUpdater instance;

    public static synchronized MetsFileUpdater getInstance() {
        if (instance == null) {
            instance = new MetsFileUpdater();
        }
        return instance;
    }

    /**
     * update information of ContentFiles' locations in the METS file
     * 
     * @param process Process
     * @param namesMap Map from old names to new names
     * @return true if the METS file is updated successfully, false if any error should occur
     */
    public void updateMetsFile(Process process, Map<String, String> namesMap) throws IOException {
        try {
            Fileformat fileformat = process.readMetadataFile();
            DigitalDocument dd = fileformat.getDigitalDocument();
            FileSet fileSet = dd.getFileSet();
            List<ContentFile> filesList = fileSet.getAllFiles();
            for (ContentFile file : filesList) {
                String oldLocation = file.getLocation();
                int fileNameStartIndex = oldLocation.lastIndexOf("/") + 1;
                String locationPrefix = oldLocation.substring(0, fileNameStartIndex);

                String oldFileName = oldLocation.substring(fileNameStartIndex);
                String newFileName = getNewFileName(oldFileName, namesMap);
                String newLocation = locationPrefix.concat(newFileName);
                file.setLocation(newLocation);
            }

            process.writeMetadataFile(fileformat);

        } catch (ReadException | IOException | SwapException | PreferencesException | WriteException e) {
            throw new IOException("Error writing updated filenames to meta.xml of process " + process.getTitel() + ": " + e.toString(), e);
        }
    }

    /**
     * get the new file name given the old one
     * 
     * @param oldFileName the old file name, including the file suffix
     * @param namesMap Map from old names to new names
     * @return the new file name including the file suffix
     */
    private String getNewFileName(String oldFileName, Map<String, String> namesMap) {
        int suffixIndex = oldFileName.lastIndexOf(".");
        String suffix = oldFileName.substring(suffixIndex);
        String oldName = oldFileName.substring(0, suffixIndex);
        if (namesMap.containsKey(oldName)) {
            return namesMap.get(oldName).concat(suffix);
        } else {
            return oldFileName;
        }
    }
}
