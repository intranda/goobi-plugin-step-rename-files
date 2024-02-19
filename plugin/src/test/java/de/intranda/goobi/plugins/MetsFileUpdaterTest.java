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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.goobi.beans.Process;
import org.goobi.beans.Ruleset;
import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Test;

import de.sub.goobi.helper.exceptions.SwapException;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.WriteException;
import ugh.fileformats.mets.MetsMods;

public class MetsFileUpdaterTest {
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
                .map(cf -> cf.getLocation())
                .collect(Collectors.toList());
    }

    private void verifyMetadataFileLocationUpdateCorrect(List<String> originalFileLocations, List<String> updatedFileLocations,
            Map<String, String> renamingMap) throws WriteException, PreferencesException, IOException, SwapException {
        verify(process, times(1)).writeMetadataFile(fileFormat);

        assertEquals(originalFileLocations.size(), updatedFileLocations.size());

        for (int i = 0; i < updatedFileLocations.size(); i++) {
            String expectedName = applyRenaming(originalFileLocations.get(i), renamingMap);
            assertThat(updatedFileLocations.get(i), Is.is(expectedName));
        }
    }

    private String applyRenaming(String originalFileLocation, Map<String, String> renamingMap) {
        String[] pathElements = originalFileLocation.split("/");
        for (Map.Entry<String, String> e : renamingMap.entrySet()) {
            if (pathElements[pathElements.length - 1].equals(e.getKey())) {
                pathElements[pathElements.length - 1] = e.getValue();
                return String.join("/", pathElements);
            }
        }
        fail("File \"" + originalFileLocation + "\" is not covered!");
        return originalFileLocation;
    }

    @Test
    public void noRenamingDone_expectNoModificationToMetsFile() {
        Map<String, String> renamingMap = Map.of(
                "00000001.jpg", "FILE_0001.jpg",
                "00000002.jpg", "FILE_0002.jpg",
                "00000003.jpg", "FILE_0003.jpg");
    }

    @Test
    public void simpleRenamingDone_expectCorrectModificationsToMetsFile()
            throws IOException, ReadException, PreferencesException, SwapException, WriteException {
        Map<String, String> renamingMap = Map.of(
                "00000001.jpg", "FILE_0001.jpg",
                "00000002.jpg", "FILE_0002.jpg",
                "00000003.jpg", "FILE_0003.jpg");

        mockMetaFileReading(process, "before-mets-update.xml");
        List<String> originalFileLocations = extractFileLocations();
        metsFileUpdater.updateMetsFile(process, renamingMap);
        List<String> updatedFileLocations = extractFileLocations();

        verifyMetadataFileLocationUpdateCorrect(originalFileLocations, originalFileLocations, renamingMap);
    }
}
