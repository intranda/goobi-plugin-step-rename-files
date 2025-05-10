package de.intranda.goobi.plugins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.goobi.beans.Process;
import org.goobi.beans.Ruleset;
import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Test;

import de.sub.goobi.helper.exceptions.SwapException;
import ugh.dl.ContentFile;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.WriteException;
import ugh.fileformats.mets.MetsMods;

public class MetsFileUpdaterTest {
    private static final String DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY = "/opt/digiverso/goobi/metadata/1/images/bergsphi_625017145_media";

    private MetsFileUpdater metsFileUpdater;

    private Process process;
    private Fileformat fileFormat;
    private Ruleset ruleset;

    @Before
    public void setup() throws ReadException, IOException, SwapException, PreferencesException {
        metsFileUpdater = new MetsFileUpdater();
        process = mock(Process.class);
        ruleset = mock(Ruleset.class);
        Prefs rulesetPrefs = new Prefs();
        rulesetPrefs.loadPrefs(getClass().getResource("/ruleset.xml").getFile());
        when(ruleset.getPreferences()).thenReturn(rulesetPrefs);
    }

    private void mockMetaFileReading(Process process, String metaDataFileName)
            throws ReadException, PreferencesException, IOException, SwapException {
        URL resource = getClass().getResource("/" + metaDataFileName);
        fileFormat = new MetsMods();
        fileFormat.setPrefs(ruleset.getPreferences());
        fileFormat.read(resource.getFile());
        when(process.readMetadataFile()).thenReturn(fileFormat);
    }

    private List<String> extractFileLocations() throws PreferencesException {
        return fileFormat.getDigitalDocument()
                .getFileSet()
                .getAllFiles()
                .stream()
                .map(ContentFile::getLocation)
                .collect(Collectors.toList());
    }

    private void verifyMetadataFileLocationUpdateCorrect(List<String> originalFileLocations, List<String> updatedFileLocations,
            Map<Path, Path> renamingMap) throws WriteException, PreferencesException, IOException, SwapException {
        verify(process, times(1)).writeMetadataFile(fileFormat);

        assertEquals(originalFileLocations.size(), updatedFileLocations.size());

        for (int i = 0; i < updatedFileLocations.size(); i++) {
            String expectedName = applyRenaming(originalFileLocations.get(i), renamingMap);
            assertThat(updatedFileLocations.get(i), Is.is(expectedName));
        }
    }

    private String applyRenaming(String originalFileLocation, Map<Path, Path> renamingMap) {
        String[] pathElements = originalFileLocation.split("/");
        for (Map.Entry<Path, Path> e : renamingMap.entrySet()) {
            if (pathElements[pathElements.length - 1].equals(e.getKey().getFileName().toString())) {
                pathElements[pathElements.length - 1] = e.getValue().getFileName().toString();
                return String.join("/", pathElements);
            }
        }
        fail("File \"" + originalFileLocation + "\" is not covered!");
        return originalFileLocation;
    }

    @Test
    public void simpleRenamingDone_expectCorrectModificationsToMetsFile()
            throws IOException, ReadException, PreferencesException, SwapException, WriteException {
        NumberFormat oldFormat = new DecimalFormat("00000000");
        NumberFormat newFormat = new DecimalFormat("0000");

        Map<Path, Path> renamingMap = new HashMap<>();
        // Test file is a real example and contains 56 files
        for (int i = 1; i <= 56; i++) {
            renamingMap.put(
                    Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, oldFormat.format(i) + ".jpg"),
                    Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "FILE_" + newFormat.format(i) + ".jpg"));
        }

        mockMetaFileReading(process, "before-mets-update.xml");
        List<String> originalFileLocations = extractFileLocations();
        metsFileUpdater.updateMetsFile(process, renamingMap);
        List<String> updatedFileLocations = extractFileLocations();

        verifyMetadataFileLocationUpdateCorrect(originalFileLocations, updatedFileLocations, renamingMap);
    }
}
