package de.intranda.goobi.plugins;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.easymock.PowerMock.mockStatic;
import static org.powermock.api.easymock.PowerMock.replay;

import java.io.IOException;
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
import org.goobi.beans.Process;
import org.goobi.beans.Processproperty;
import org.goobi.beans.Project;
import org.goobi.beans.Ruleset;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginReturnValue;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.StorageProviderInterface;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.persistence.managers.PropertyManager;
import ugh.dl.Prefs;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ ConfigurationHelper.class, ConfigPlugins.class, PropertyManager.class, StorageProvider.class })
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
    private static final String DEFAULT_PROCESS_TITLE = "TestProcess123";
    private static final int DEFAULT_PROJECT_ID = 1;
    private static final int DEFAULT_PROCESS_ID = 1;

    private Processproperty processProperty;
    private SubnodeConfiguration pluginConfiguration;
    private StorageProviderInterface storage;
    private ConfigurationHelper configurationHelper;

    private Project project;
    private Process process;
    private Ruleset ruleset;
    private Prefs rulesetPreferences;
    private Step step;

    private RenameFilesPlugin plugin;

    @Before
    public void setup() throws IOException, SwapException, DAOException {
        rulesetPreferences = mock(Prefs.class);
        ruleset = mock(Ruleset.class);
        when(ruleset.getPreferences()).thenReturn(rulesetPreferences);
        project = mock(Project.class);
        when(project.getId()).thenReturn(DEFAULT_PROJECT_ID);
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

        storage = mock(StorageProviderInterface.class);
        setupStorageProviderMocking(storage);

        configurationHelper = mock(ConfigurationHelper.class);
        setupConfigurationHelperMocking(configurationHelper);
        when(configurationHelper.getGoobiFolder()).thenReturn("");
        when(configurationHelper.getScriptsFolder()).thenReturn("");
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

    //    private void setupDefaultPluginConfiguration() {
    //        HierarchicalConfiguration rootConfiguration = new HierarchicalConfiguration();
    //        ConfigurationNode configRoot = new DefaultConfigurationNode("config");
    //        ConfigurationNode folder = new DefaultConfigurationNode("folder", "*");
    //        configRoot.addChild(folder);
    //        pluginConfiguration = new SubnodeConfiguration(rootConfiguration, configRoot);
    //    }
    //
    //    private void setupPluginConfigurationWithNonExistingFolder() {
    //        HierarchicalConfiguration rootConfiguration = new HierarchicalConfiguration();
    //        ConfigurationNode configRoot = new DefaultConfigurationNode("config");
    //        ConfigurationNode folder = new DefaultConfigurationNode("folder", "non-existent");
    //        configRoot.addChild(folder);
    //        pluginConfiguration = new SubnodeConfiguration(rootConfiguration, configRoot);
    //    }

    private void setupPluginConfiguration(String configurationName) throws ConfigurationException {
        pluginConfiguration = loadPluginConfiguration(configurationName);
    }

    private void expectRenamingFromTo(List<Path> from, List<Path> to) throws IOException {
        if (from.size() != to.size()) {
            Assert.fail("\"from\" and \"to\" need to be the same size!");
        }
        for (int i = 0; i < from.size(); i++) {
            verify(storage, times(1)).move(from.get(i), to.get(i));
        }
    }

    private void mockDefaultRenamingFoldersExist(boolean doExist) {
        when(storage.isDirectory(Paths.get(DEFAULT_PROCESS_ORIG_IMAGES_DIRECTORY))).thenReturn(doExist);
        when(storage.isDirectory(Paths.get(DEFAULT_PROCESS_TIF_DIRECTORY))).thenReturn(doExist);
        when(storage.isDirectory(Paths.get(DEFAULT_PROCESS_OCR_ALTO_DIRECTORY))).thenReturn(doExist);
        when(storage.isDirectory(Paths.get(DEFAULT_PROCESS_OCR_PDF_DIRECTORY))).thenReturn(doExist);
        when(storage.isDirectory(Paths.get(DEFAULT_PROCESS_OCR_TXT_DIRECTORY))).thenReturn(doExist);
        when(storage.isDirectory(Paths.get(DEFAULT_PROCESS_OCR_XML_DIRECTORY))).thenReturn(doExist);
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

        mockDefaultRenamingFoldersExist(true);
        mockStorageFilePresence(oldFiles);

        assertEquals(PluginReturnValue.FINISH, plugin.run());

        expectRenamingFromTo(oldFiles, newFiles);
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

        mockDefaultRenamingFoldersExist(true);
        mockStorageFilePresence(oldFiles);

        assertEquals(PluginReturnValue.FINISH, plugin.run());

        expectRenamingFromTo(oldFiles, newFiles);
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

        mockDefaultRenamingFoldersExist(true);
        mockStorageFilePresence(oldFiles);

        assertEquals(PluginReturnValue.FINISH, plugin.run());

        expectRenamingFromTo(oldFiles, newFiles);
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

        mockDefaultRenamingFoldersExist(true);
        mockStorageFilePresence(oldFiles);

        assertEquals(PluginReturnValue.FINISH, plugin.run());

        expectRenamingFromTo(oldFiles, newFiles);
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

        mockDefaultRenamingFoldersExist(true);
        mockStorageFilePresence(oldFiles);

        assertEquals(PluginReturnValue.FINISH, plugin.run());

        expectRenamingFromTo(oldFiles, newFiles);
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

        mockDefaultRenamingFoldersExist(true);
        mockStorageFilePresence(oldFiles);

        assertEquals(PluginReturnValue.FINISH, plugin.run());

        expectRenamingFromTo(oldFiles, newFiles);
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

        mockDefaultRenamingFoldersExist(true);
        mockStorageFilePresence(oldFiles);

        assertEquals(PluginReturnValue.FINISH, plugin.run());

        expectRenamingFromTo(oldFiles, newFiles);
    }
}
