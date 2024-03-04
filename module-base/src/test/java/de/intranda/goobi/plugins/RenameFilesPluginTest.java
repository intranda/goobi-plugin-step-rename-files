package de.intranda.goobi.plugins;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.easymock.PowerMock.mockStatic;
import static org.powermock.api.easymock.PowerMock.replay;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.easymock.EasyMock;
import org.goobi.beans.Process;
import org.goobi.beans.Processproperty;
import org.goobi.beans.Project;
import org.goobi.beans.Ruleset;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginReturnValue;
import org.hamcrest.core.Is;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.google.gson.Gson;

import de.intranda.goobi.plugins.RenameFilesPlugin.OriginalFileNameHistory;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.StorageProviderInterface;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.persistence.managers.PropertyManager;
import ugh.dl.Prefs;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ MetsFileUpdater.class, ConfigurationHelper.class, ConfigPlugins.class, PropertyManager.class, StorageProvider.class })
@PowerMockIgnore({ "javax.management.*" })
public class RenameFilesPluginTest {
    private static final String DEFAULT_PROCESS_IMAGES_DIRECTORY = "/opt/digiverso/goobi/metadata/1/images";
    private static final String DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY = "/opt/digiverso/goobi/metadata/1/images/media";
    private static final String DEFAULT_PROCESS_TIF_DIRECTORY = "/opt/digiverso/goobi/metadata/1/images/tif";
    private static final String DEFAULT_PROCESS_OCR_ALTO_DIRECTORY = "/opt/digiverso/goobi/metadata/1/images/alto";
    private static final String DEFAULT_PROCESS_OCR_PDF_DIRECTORY = "/opt/digiverso/goobi/metadata/1/images/pdf";
    private static final String DEFAULT_PROCESS_OCR_TXT_DIRECTORY = "/opt/digiverso/goobi/metadata/1/images/txt";
    private static final String DEFAULT_PROCESS_OCR_XML_DIRECTORY = "/opt/digiverso/goobi/metadata/1/images/xml";

    private static final String DEFAULT_RETURN_PAGE = "pageBefore";
    private static final String DEFAULT_PROJECT_TITLE = "ProjectABC";
    private static final int DEFAULT_PROJECT_ID = 1;
    private static final String DEFAULT_PROCESS_TITLE = "TestProcess_123";
    private static final int DEFAULT_PROCESS_ID = 1;

    private Processproperty processProperty;
    private SubnodeConfiguration pluginConfiguration;
    private StorageProviderInterface storage;
    private ConfigurationHelper configurationHelper;
    private MetsFileUpdater metsFileUpdater;

    private Project project;
    private Process process;
    private Ruleset ruleset;
    private Prefs rulesetPreferences;
    private Step step;
    private Gson gson;

    private RenameFilesPlugin plugin;

    @BeforeClass
    public static void setUpClass() throws Exception {
        URL log4JResource = RenameFilesPlugin.class.getResource("/log4j2.xml");
        System.setProperty("log4j.configurationFile", log4JResource.toString());
    }

    @Before
    public void setup() throws IOException, SwapException, DAOException {
        rulesetPreferences = mock(Prefs.class);
        ruleset = mock(Ruleset.class);
        when(ruleset.getPreferences()).thenReturn(rulesetPreferences);
        project = mock(Project.class);
        when(project.getId()).thenReturn(DEFAULT_PROJECT_ID);
        when(project.getTitel()).thenReturn(DEFAULT_PROJECT_TITLE);
        process = mock(Process.class);
        when(process.getMetadataFilePath()).thenReturn("");
        when(process.getId()).thenReturn(DEFAULT_PROCESS_ID);
        when(process.getTitel()).thenReturn(DEFAULT_PROCESS_TITLE);
        when(process.getProjekt()).thenReturn(project);
        when(process.getRegelsatz()).thenReturn(ruleset);
        when(process.getImagesDirectory()).thenReturn(DEFAULT_PROCESS_IMAGES_DIRECTORY);
        when(process.getImagesOrigDirectory(false)).thenReturn(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY);
        when(process.getImagesTifDirectory(false)).thenReturn(DEFAULT_PROCESS_TIF_DIRECTORY);
        when(process.getOcrAltoDirectory()).thenReturn(DEFAULT_PROCESS_OCR_ALTO_DIRECTORY);
        when(process.getOcrPdfDirectory()).thenReturn(DEFAULT_PROCESS_OCR_PDF_DIRECTORY);
        when(process.getOcrTxtDirectory()).thenReturn(DEFAULT_PROCESS_OCR_TXT_DIRECTORY);
        when(process.getOcrXmlDirectory()).thenReturn(DEFAULT_PROCESS_OCR_XML_DIRECTORY);
        step = mock(Step.class);
        when(step.getProzess()).thenReturn(process);

        processProperty = mock(Processproperty.class);
        setupProcessPropertyMocking(processProperty);
        when(processProperty.getTitel()).thenReturn(RenameFilesPlugin.PROPERTY_TITLE);

        storage = mock(StorageProviderInterface.class);
        setupStorageProviderMocking(storage);

        configurationHelper = mock(ConfigurationHelper.class);
        setupConfigurationHelperMocking(configurationHelper);
        when(configurationHelper.getGoobiFolder()).thenReturn("");
        when(configurationHelper.getScriptsFolder()).thenReturn("");

        metsFileUpdater = mock(MetsFileUpdater.class);
        setupMetsFileUpdaterMocking(metsFileUpdater);

        gson = new Gson();
    }

    private void setupConfigurationFileMocking(SubnodeConfiguration config) {
        mockStatic(ConfigPlugins.class);
        expect(ConfigPlugins.getProjectAndStepConfig(plugin.getTitle(), step))
                .andReturn(config)
                .anyTimes();
        replay(ConfigPlugins.class);
    }

    private void setupProcessPropertyMocking(Processproperty property) {
        mockStatic(PropertyManager.class);
        expect(PropertyManager.getProcessPropertiesForProcess(DEFAULT_PROCESS_ID))
                .andReturn(List.of(processProperty))
                .anyTimes();
        PropertyManager.saveProcessProperty(EasyMock.anyObject());
        replay(PropertyManager.class);
    }

    private void setupStorageProviderMocking(StorageProviderInterface storageProviderInterface) {
        mockStatic(StorageProvider.class);
        expect(StorageProvider.getInstance())
                .andReturn(storageProviderInterface)
                .anyTimes();
        replay(StorageProvider.class);
    }

    private void setupConfigurationHelperMocking(ConfigurationHelper configurationHelper) {
        mockStatic(ConfigurationHelper.class);
        expect(ConfigurationHelper.getInstance())
                .andReturn(configurationHelper)
                .anyTimes();
        replay(ConfigurationHelper.class);
    }

    private void setupMetsFileUpdaterMocking(MetsFileUpdater metsFileUpdater) {
        mockStatic(MetsFileUpdater.class);
        expect(MetsFileUpdater.getInstance())
                .andReturn(metsFileUpdater)
                .anyTimes();
        replay(MetsFileUpdater.class);
    }

    private void initializate() {
        plugin = new RenameFilesPlugin();
        setupConfigurationFileMocking(pluginConfiguration);
        plugin.initialize(step, DEFAULT_RETURN_PAGE);
    }

    private SubnodeConfiguration loadPluginConfiguration(String testFileName) throws ConfigurationException {
        XMLConfiguration xmlConfig = new XMLConfiguration();
        xmlConfig.load(getClass().getResource("/" + testFileName + ".xml"));
        xmlConfig.setDelimiterParsingDisabled(true);
        xmlConfig.setExpressionEngine(new XPathExpressionEngine());
        return xmlConfig.configurationAt("//config");
    }

    private void setupPluginConfiguration(String configurationName) throws ConfigurationException {
        pluginConfiguration = loadPluginConfiguration(configurationName);
    }

    private void verifyRenamingFromTo(List<Path> from, List<Path> to) throws IOException {
        if (from.size() != to.size()) {
            Assert.fail("\"from\" and \"to\" need to be the same size!");
        }
        for (int i = 0; i < from.size(); i++) {
            verify(storage, times(1)).move(from.get(i), to.get(i));
        }
    }

    private void verifyOrderedRenamingFromTo(List<Path> from, List<Path> to) throws IOException {
        if (from.size() != to.size()) {
            Assert.fail("\"from\" and \"to\" need to be the same size!");
        }
        InOrder inOrder = Mockito.inOrder(storage);
        for (int i = 0; i < from.size(); i++) {
            inOrder.verify(storage, times(1)).move(from.get(i), to.get(i));
        }
    }

    private void verifyOriginalFileNameHistoryUpdatedCorrectly(String jsonFile) throws IOException, URISyntaxException {
        String json = loadJsonResource(jsonFile);
        OriginalFileNameHistory expectedHistory = gson.fromJson(json, OriginalFileNameHistory.class);
        assertThat(expectedHistory, Is.is(plugin.originalFileNameHistory));
        // TODO: Currently not tested, that saving the property is invoked!
    }

    private String loadJsonResource(String jsonFile) throws IOException, URISyntaxException {
        URL resource = getClass().getResource("/" + jsonFile + ".json");
        String json = Files.readString(Path.of(resource.toURI()));
        return json;
    }

    private void mockDefaultRenamingFoldersExist(boolean doExist) {
        when(storage.isDirectory(Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY))).thenReturn(doExist);
        when(storage.isDirectory(Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY))).thenReturn(doExist);
        when(storage.isDirectory(Paths.get(DEFAULT_PROCESS_OCR_ALTO_DIRECTORY))).thenReturn(doExist);
        when(storage.isDirectory(Paths.get(DEFAULT_PROCESS_OCR_PDF_DIRECTORY))).thenReturn(doExist);
        when(storage.isDirectory(Paths.get(DEFAULT_PROCESS_OCR_TXT_DIRECTORY))).thenReturn(doExist);
        when(storage.isDirectory(Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY))).thenReturn(doExist);
    }

    private void mockStorageFileParentPathPresence(List<Path> files) {
        files.stream()
                .map(f -> f.getParent())
                .distinct()
                .forEach(p -> when(storage.isDirectory(p)).thenReturn(true));
    }

    private void mockStorageFilePresence(List<Path> files) {
        Map<Path, List<Path>> directoryFileMapping = new HashMap<>();
        for (Path file : files) {
            Path parent = file.getParent();
            if (!directoryFileMapping.containsKey(parent)) {
                directoryFileMapping.put(parent, new LinkedList<>());
            }
            directoryFileMapping.get(parent).add(file);
        }
        directoryFileMapping.entrySet()
                .stream()
                .forEach(e -> when(storage.listFiles(e.getKey().toString())).thenReturn(e.getValue()));
    }

    @Test
    public void noRenamingFormat_expectPluginSucceeding() throws ConfigurationException {
        setupPluginConfiguration("folder_star");
        initializate();

        mockDefaultRenamingFoldersExist(true);

        assertEquals(PluginReturnValue.FINISH, plugin.run());
    }

    @Test
    public void nonExistingFolder_expectPluginToFail() throws ConfigurationException {
        setupPluginConfiguration("folder_non-existent");
        initializate();

        assertEquals(PluginReturnValue.ERROR, plugin.run());
    }

    @Test
    public void onlySingleStatic_expectCorrectFileRenaming() throws ConfigurationException, IOException {
        setupPluginConfiguration("static-only_renaming_star");
        initializate();

        List<Path> oldFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "001.jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "001.tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "001.xml"));
        List<Path> newFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "STATIC.jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "STATIC.tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "STATIC.xml"));

        mockStorageFileParentPathPresence(oldFiles);
        mockStorageFilePresence(oldFiles);

        assertEquals(PluginReturnValue.FINISH, plugin.run());

        verifyRenamingFromTo(oldFiles, newFiles);
    }

    @Test
    public void onlySingleCounter_renameOneFolderOnly_expectCorrectFileRenaming() throws ConfigurationException, IOException {
        setupPluginConfiguration("counter-only_renaming_star");
        initializate();

        List<Path> oldFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_01.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_02.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_03.jpg"));
        List<Path> newFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "00001.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "00002.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "00003.jpg"));

        mockStorageFileParentPathPresence(oldFiles);
        mockStorageFilePresence(oldFiles);

        assertEquals(PluginReturnValue.FINISH, plugin.run());

        verifyRenamingFromTo(oldFiles, newFiles);
    }

    @Test
    public void onlySingleCounter_renameMultipleFolders_expectCorrectFileRenaming()
            throws ConfigurationException, IOException {
        setupPluginConfiguration("counter-only_renaming_star");
        initializate();

        List<Path> oldFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_01.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_02.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_03.jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_01.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_02.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_03.tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_01.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_02.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_03.xml"));
        List<Path> newFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "00001.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "00002.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "00003.jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "00001.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "00002.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "00003.tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "00001.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "00002.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "00003.xml"));

        mockStorageFileParentPathPresence(oldFiles);
        mockStorageFilePresence(oldFiles);

        assertEquals(PluginReturnValue.FINISH, plugin.run());

        verifyRenamingFromTo(oldFiles, newFiles);
    }

    @Test
    public void onlySingleCounterWithStartValue_renameMultipleFolders_expectCorrectFileRenaming()
            throws ConfigurationException, IOException {
        setupPluginConfiguration("counter-only-with-startValue_renaming_star");
        initializate();

        List<Path> oldFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_01.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_02.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_03.jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_01.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_02.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_03.tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_01.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_02.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_03.xml"));
        List<Path> newFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "00004.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "00005.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "00006.jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "00004.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "00005.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "00006.tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "00004.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "00005.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "00006.xml"));

        mockStorageFileParentPathPresence(oldFiles);
        mockStorageFilePresence(oldFiles);

        assertEquals(PluginReturnValue.FINISH, plugin.run());

        verifyRenamingFromTo(oldFiles, newFiles);
    }

    @Test
    public void onlySingleVariable_expectCorrectFileRenaming() throws ConfigurationException, IOException {
        setupPluginConfiguration("variable-only_renaming_star");
        initializate();

        List<Path> oldFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "001.jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "001.tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "001.xml"));
        List<Path> newFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, DEFAULT_PROCESS_TITLE + ".jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, DEFAULT_PROCESS_TITLE + ".tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, DEFAULT_PROCESS_TITLE + ".xml"));

        mockStorageFileParentPathPresence(oldFiles);
        mockStorageFilePresence(oldFiles);

        assertEquals(PluginReturnValue.FINISH, plugin.run());

        verifyRenamingFromTo(oldFiles, newFiles);
    }

    @Test
    public void mixedVariableCounterStaticWithStartValue_renameMultipleFolders_expectCorrectFileRenaming()
            throws ConfigurationException, IOException {
        setupPluginConfiguration("mixed-variable-static-counter-with-startValue_renaming_star");
        initializate();

        List<Path> oldFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_01.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_02.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_03.jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_01.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_02.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_03.tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_01.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_02.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_03.xml"));
        List<Path> newFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, DEFAULT_PROCESS_TITLE + "_00004.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, DEFAULT_PROCESS_TITLE + "_00005.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, DEFAULT_PROCESS_TITLE + "_00006.jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, DEFAULT_PROCESS_TITLE + "_00004.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, DEFAULT_PROCESS_TITLE + "_00005.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, DEFAULT_PROCESS_TITLE + "_00006.tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, DEFAULT_PROCESS_TITLE + "_00004.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, DEFAULT_PROCESS_TITLE + "_00005.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, DEFAULT_PROCESS_TITLE + "_00006.xml"));

        mockStorageFileParentPathPresence(oldFiles);
        mockStorageFilePresence(oldFiles);

        assertEquals(PluginReturnValue.FINISH, plugin.run());

        verifyRenamingFromTo(oldFiles, newFiles);
    }

    @Test
    public void onlyOriginalFileNameAndStatic_renameMultipleFolders_expectCorrectFileRenaming()
            throws ConfigurationException, IOException {
        setupPluginConfiguration("originalfilename-static-suffix_renaming_star");
        initializate();

        List<Path> oldFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_01.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_02.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_03.jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_01.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_02.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_03.tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_01.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_02.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_03.xml"));
        List<Path> newFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_01_SUFFIX.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_02_SUFFIX.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_03_SUFFIX.jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_01_SUFFIX.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_02_SUFFIX.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_03_SUFFIX.tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_01_SUFFIX.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_02_SUFFIX.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_03_SUFFIX.xml"));

        mockStorageFileParentPathPresence(oldFiles);
        mockStorageFilePresence(oldFiles);

        assertEquals(PluginReturnValue.FINISH, plugin.run());

        verifyRenamingFromTo(oldFiles, newFiles);
    }

    @Test
    public void mixedVariableStaticCounterWithReplacement_renameMultipleFolders_expectCorrectFileRenaming()
            throws ConfigurationException, IOException {
        setupPluginConfiguration("mixed-variable-static-counter-with-replacement_renaming_star");
        initializate();

        List<Path> oldFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_01.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_02.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_03.jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_01.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_02.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_03.tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_01.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_02.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_03.xml"));
        String projectTitleSuffix = DEFAULT_PROCESS_TITLE.substring(DEFAULT_PROCESS_TITLE.lastIndexOf('_') + 1);
        List<Path> newFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, projectTitleSuffix + "_0001.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, projectTitleSuffix + "_0002.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, projectTitleSuffix + "_0003.jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, projectTitleSuffix + "_0001.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, projectTitleSuffix + "_0002.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, projectTitleSuffix + "_0003.tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, projectTitleSuffix + "_0001.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, projectTitleSuffix + "_0002.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, projectTitleSuffix + "_0003.xml"));

        mockStorageFileParentPathPresence(oldFiles);
        mockStorageFilePresence(oldFiles);

        assertEquals(PluginReturnValue.FINISH, plugin.run());

        verifyRenamingFromTo(oldFiles, newFiles);
    }

    @Test
    public void mixedVariableStaticCounterWithConditionMatching_renameMultipleFolders_expectCorrectFileRenaming()
            throws ConfigurationException, IOException {
        setupPluginConfiguration("mixed-variable-static-counter-with-condition_match_renaming_star");
        initializate();

        List<Path> oldFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_01.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_02.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_03.jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_01.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_02.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_03.tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_01.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_02.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_03.xml"));
        List<Path> newFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, DEFAULT_PROCESS_TITLE + "_0001.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, DEFAULT_PROCESS_TITLE + "_0002.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, DEFAULT_PROCESS_TITLE + "_0003.jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, DEFAULT_PROCESS_TITLE + "_0001.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, DEFAULT_PROCESS_TITLE + "_0002.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, DEFAULT_PROCESS_TITLE + "_0003.tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, DEFAULT_PROCESS_TITLE + "_0001.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, DEFAULT_PROCESS_TITLE + "_0002.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, DEFAULT_PROCESS_TITLE + "_0003.xml"));

        mockStorageFileParentPathPresence(oldFiles);
        mockStorageFilePresence(oldFiles);

        assertEquals(PluginReturnValue.FINISH, plugin.run());

        verifyRenamingFromTo(oldFiles, newFiles);
    }

    @Test
    public void mixedVariableStaticCounterWithConditionNotMatching_renameMultipleFolders_expectCorrectFileRenaming()
            throws ConfigurationException, IOException {
        setupPluginConfiguration("mixed-variable-static-counter-with-condition_no-match_renaming_star");
        initializate();

        List<Path> oldFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_01.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_02.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_03.jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_01.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_02.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_03.tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_01.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_02.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_03.xml"));
        List<Path> newFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "_0001.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "_0002.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "_0003.jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "_0001.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "_0002.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "_0003.tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "_0001.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "_0002.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "_0003.xml"));

        mockStorageFileParentPathPresence(oldFiles);
        mockStorageFilePresence(oldFiles);

        assertEquals(PluginReturnValue.FINISH, plugin.run());

        verifyRenamingFromTo(oldFiles, newFiles);
    }

    @Test
    public void barcodeConfigurationTest_renameMultipleFolders_expectCorrectFileRenaming()
            throws ConfigurationException, IOException {
        setupPluginConfiguration("barcode-feature");
        initializate();

        List<Path> oldFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_01.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_barcode_02.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_03.jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_01.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIFbarcode_02.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_03.tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_01.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "barcodec_02.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_03.xml"));
        List<Path> newFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "FILE_0001.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "FILE_0000.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "FILE_0002.jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "FILE_0001.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "FILE_0000.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "FILE_0002.tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "FILE_0001.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "FILE_0000.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "FILE_0002.xml"));

        mockStorageFileParentPathPresence(oldFiles);
        mockStorageFilePresence(oldFiles);

        assertEquals(PluginReturnValue.FINISH, plugin.run());

        verifyRenamingFromTo(oldFiles, newFiles);
    }

    @Test
    public void mixedStaticCounterWithMetsFileUpdate_renameMultipleFolders_expectMetsFileUpdaterCall()
            throws ConfigurationException, IOException {
        setupPluginConfiguration("mets-file-update");
        initializate();

        List<Path> oldFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_01.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_02.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_03.jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_01.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_02.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_03.tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_01.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_02.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_03.xml"));
        List<Path> newFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "FILE_0001.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "FILE_0002.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "FILE_0003.jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "FILE_0001.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "FILE_0002.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "FILE_0003.tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "FILE_0001.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "FILE_0002.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "FILE_0003.xml"));
        Map<Path, Path> renamingMap = new HashMap<>();
        for (int i = 0; i < oldFiles.size(); i++) {
            renamingMap.put(oldFiles.get(i), newFiles.get(i));
        }

        mockStorageFileParentPathPresence(oldFiles);
        mockStorageFilePresence(oldFiles);

        assertEquals(PluginReturnValue.FINISH, plugin.run());

        verify(metsFileUpdater, times(1)).updateMetsFile(process, renamingMap);
    }

    @Test
    public void onlySingleCounter_renameOneFolderOnly_expectOriginalFileNameHistoryUpdatedCorrectly()
            throws ConfigurationException, IOException, URISyntaxException {
        setupPluginConfiguration("counter-only_renaming_star");
        initializate();

        List<Path> oldFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_01.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_02.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_03.jpg"));

        mockStorageFileParentPathPresence(oldFiles);
        mockStorageFilePresence(oldFiles);

        assertEquals(PluginReturnValue.FINISH, plugin.run());

        verifyOriginalFileNameHistoryUpdatedCorrectly("counter-only_renaming_star");
    }

    @Test
    public void mixedVariableCounterStaticWithStartValue_renameMultipleFolders_expectOriginalFileNameHistoryUpdatedCorrectly()
            throws ConfigurationException, IOException, URISyntaxException {
        setupPluginConfiguration("mixed-variable-static-counter-with-startValue_renaming_star");
        initializate();

        List<Path> oldFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_01.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_02.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_03.jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_01.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_02.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_03.tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_01.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_02.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_03.xml"));
        List<Path> newFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, DEFAULT_PROCESS_TITLE + "_00004.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, DEFAULT_PROCESS_TITLE + "_00005.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, DEFAULT_PROCESS_TITLE + "_00006.jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, DEFAULT_PROCESS_TITLE + "_00004.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, DEFAULT_PROCESS_TITLE + "_00005.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, DEFAULT_PROCESS_TITLE + "_00006.tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, DEFAULT_PROCESS_TITLE + "_00004.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, DEFAULT_PROCESS_TITLE + "_00005.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, DEFAULT_PROCESS_TITLE + "_00006.xml"));

        mockStorageFileParentPathPresence(oldFiles);
        mockStorageFilePresence(oldFiles);

        assertEquals(PluginReturnValue.FINISH, plugin.run());

        verifyOriginalFileNameHistoryUpdatedCorrectly("mixed-variable-static-counter-with-startValue_renaming_star");
    }

    @Test
    public void onlyRestoredOriginalFileName_renameMultipleFolders_expectCorrectFileRenaming()
            throws ConfigurationException, IOException, URISyntaxException {
        setupPluginConfiguration("original-file-name-restore_renaming_star");
        initializate();

        List<Path> oldFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "TestProcess_123_00004.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "TestProcess_123_00005.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "TestProcess_123_00006.jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "TestProcess_123_00004.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "TestProcess_123_00005.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "TestProcess_123_00006.tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "TestProcess_123_00004.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "TestProcess_123_00005.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "TestProcess_123_00006.xml"));
        List<Path> newFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_01.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_02.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_03.jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_01.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_02.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_03.tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_01.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_02.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_03.xml"));

        mockStorageFileParentPathPresence(oldFiles);
        mockStorageFilePresence(oldFiles);

        when(processProperty.getWert()).thenReturn(loadJsonResource("original-file-name-restore_renaming_star"));

        assertEquals(PluginReturnValue.FINISH, plugin.run());

        verifyRenamingFromTo(oldFiles, newFiles);
    }

    @Test
    public void onlyRestoredOriginalFileName_renameMultipleFolders_expectOriginalFileNameHistoryUpdatedCorrectly()
            throws ConfigurationException, IOException, URISyntaxException {
        setupPluginConfiguration("original-file-name-restore_renaming_star");
        initializate();

        List<Path> oldFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "TestProcess_123_00004.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "TestProcess_123_00005.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "TestProcess_123_00006.jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "TestProcess_123_00004.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "TestProcess_123_00005.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "TestProcess_123_00006.tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "TestProcess_123_00004.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "TestProcess_123_00005.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "TestProcess_123_00006.xml"));
        List<Path> newFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_01.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_02.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_03.jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_01.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_02.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_03.tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_01.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_02.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_03.xml"));

        mockStorageFileParentPathPresence(oldFiles);
        mockStorageFilePresence(oldFiles);

        when(processProperty.getWert()).thenReturn(loadJsonResource("original-file-name-restore_renaming_star"));

        assertEquals(PluginReturnValue.FINISH, plugin.run());

        verifyOriginalFileNameHistoryUpdatedCorrectly("original-file-name-restore_renaming_star_updated");
    }

    @Test
    public void onlyCounterFileNameShifted_renameMultipleFolders_expectCorrectFileRenamingOrder()
            throws ConfigurationException, IOException, URISyntaxException {
        setupPluginConfiguration("counter-only-shifted_renaming_star");
        initializate();

        List<Path> oldFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "00009.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "00008.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "00007.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "00006.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "00005.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "00004.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "00003.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "00002.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "00001.jpg"));
        List<Path> newFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "00010.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "00009.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "00008.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "00007.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "00006.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "00005.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "00004.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "00003.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "00002.jpg"));

        mockStorageFileParentPathPresence(oldFiles);
        mockStorageFilePresence(oldFiles);

        assertEquals(PluginReturnValue.FINISH, plugin.run());

        verifyOrderedRenamingFromTo(oldFiles, newFiles);
    }

    @Test
    public void onlyStaticFileName_renameMultipleFoldersWithMultipleFiles_expectNamingCollisionDetectedAndErrorReturn()
            throws ConfigurationException, IOException, URISyntaxException {
        setupPluginConfiguration("static-only_renaming_star");
        initializate();

        List<Path> oldFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_01.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_02.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "a_03.jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_01.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_02.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "b_TIF_03.tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_01.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_02.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "c_03.xml"));
        List<Path> newFiles = List.of(
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "STATIC.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "STATIC.jpg"),
                Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY, "STATIC.jpg"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "STATIC.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "STATIC.tif"),
                Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY, "STATIC.tif"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "STATIC.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "STATIC.xml"),
                Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY, "STATIC.xml"));

        mockStorageFileParentPathPresence(oldFiles);
        mockStorageFilePresence(oldFiles);

        assertEquals(PluginReturnValue.ERROR, plugin.run());
    }
}
