package de.intranda.goobi.plugins;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.goobi.beans.Process;
import org.junit.Before;
import org.junit.Test;

import de.sub.goobi.helper.exceptions.SwapException;
import ugh.dl.ContentFile;
import ugh.dl.DigitalDocument;
import ugh.dl.FileSet;
import ugh.dl.Fileformat;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;

// TODO: These tests are a big too much whiteboxing.
// The tests shouldn't know how locations of the file entries in the METs file are updated
// but better check the input and output files for correct diff.
public class MetsFileUpdaterTest {
    private MetsFileUpdater metsFileUpdater;

    private Process process;
    private Fileformat fileFormat;
    private DigitalDocument digitalDocument;
    private FileSet fileSet;
    private List<ContentFile> contentFiles;

    @Before
    public void setup() throws ReadException, IOException, SwapException, PreferencesException {
        metsFileUpdater = new MetsFileUpdater();
        process = mock(Process.class);
        fileFormat = mock(Fileformat.class);
        when(process.readMetadataFile()).thenReturn(fileFormat);
        digitalDocument = mock(DigitalDocument.class);
        when(fileFormat.getDigitalDocument()).thenReturn(digitalDocument);
        fileSet = mock(FileSet.class);
        when(digitalDocument.getFileSet()).thenReturn(fileSet);
    }

    private void setupMetsContentFileEntries(Map<String, String> renamingMap) {
        // TODO: Determine correct format
        contentFiles = new ArrayList<>(renamingMap.size());

        for (Map.Entry<String, String> e : renamingMap.entrySet()) {
            ContentFile contentFile = mock(ContentFile.class);
            when(contentFile.getLocation()).thenReturn("file:///somepath/" + e.getKey());
            contentFiles.add(contentFile);
        }

        when(fileSet.getAllFiles()).thenReturn(contentFiles);
    }

    private void verifyContentFileLocationUpdates(Map<String, String> renamingMap) {

    }

    @Test
    public void noRenamingDone_expectNoModificationToMetsFile() {
        Map<String, String> renamingMap = Map.of(
                "0001.jpg", "FILE_0001.jpg",
                "0002.jpg", "FILE_0002.jpg",
                "0003.jpg", "FILE_0003.jpg");
    }

    @Test
    public void simpleRenamingDone_expectCorrectModificationsToMetsFile() throws IOException {
        Map<String, String> renamingMap = Map.of(
                "0001.jpg", "FILE_0001.jpg",
                "0002.jpg", "FILE_0002.jpg",
                "0003.jpg", "FILE_0003.jpg");

        setupMetsContentFileEntries(renamingMap);

        metsFileUpdater.updateMetsFile(process, renamingMap);

        verifyContentFileLocationUpdates(renamingMap);
    }
}
