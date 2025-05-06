package de.intranda.goobi.plugins;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.goobi.beans.Process;

import de.sub.goobi.helper.exceptions.SwapException;
import lombok.extern.log4j.Log4j2;
import ugh.dl.ContentFile;
import ugh.dl.DigitalDocument;
import ugh.dl.FileSet;
import ugh.dl.Fileformat;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.WriteException;

@Log4j2
public class MetsFileUpdater {
    private static MetsFileUpdater instance;

    public static synchronized MetsFileUpdater getInstance() {
        if (instance == null) {
            instance = new MetsFileUpdater();
        }
        return instance;
    }

    public void updateMetsFile(Process process, Map<Path, Path> renamingMapping) throws IOException {
        try {
            Fileformat fileformat = process.readMetadataFile();
            DigitalDocument dd = fileformat.getDigitalDocument();
            FileSet fileSet = dd.getFileSet();
            List<ContentFile> filesList = fileSet.getAllFiles();
            for (ContentFile file : filesList) {
                String oldLocation = file.getLocation();
                try {
                    String newLocation = lookUpNewLocation(renamingMapping, oldLocation);
                    file.setLocation(newLocation);
                } catch (IllegalArgumentException e) {
                    log.debug("Cannot update file reference {}: {}", oldLocation, e.toString());
                }
            }

            process.writeMetadataFile(fileformat);

        } catch (ReadException | IOException | SwapException | PreferencesException | WriteException e) {
            throw new IOException("Error writing updated filenames to meta.xml of process " + process.getTitel() + ": " + e.toString(), e);
        }
    }

    private String lookUpNewLocation(Map<Path, Path> namesMap, String oldLocation) {
        List<String> newLocations = namesMap.entrySet()
                .stream()
                .filter(e -> renamingDoesMatchToOldLocation(e, oldLocation))
                .map(e -> applyRenamingToOldLocation(e, oldLocation))
                .toList();
        if (newLocations.size() != 1) {
            throw new IllegalArgumentException("Number of results for the change of file location \"" + oldLocation
                    + "\" is not unique! Number of found results: " + newLocations.size());
        }
        return newLocations.get(0);
    }

    private boolean renamingDoesMatchToOldLocation(Map.Entry<Path, Path> renamingEntry, String oldLocation) {
        String[] from = renamingEntry.getKey().toString().split("/");
        String[] old = oldLocation.split("/");

        int maxComparisons = 2;
        int fromIndex = from.length - 1;
        int oldIndex = old.length - 1;
        int comparisons = 0;

        while (fromIndex >= 0 && oldIndex >= 0 && comparisons < maxComparisons) {
            if (!from[fromIndex].equals(old[oldIndex])) {
                return false;
            }
            fromIndex--;
            oldIndex--;
            comparisons++;
        }

        return true;
    }

    private String applyRenamingToOldLocation(Map.Entry<Path, Path> renamingEntry, String oldLocation) {
        String[] to = renamingEntry.getValue().toString().split("/");
        String[] old = oldLocation.split("/");
        old[old.length - 1] = to[to.length - 1];
        return String.join("/", old);
    }
}
