package de.intranda.goobi.plugins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.easymock.EasyMock;
import org.goobi.beans.Process;
import org.goobi.beans.Processproperty;
import org.goobi.beans.Ruleset;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginReturnValue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.StorageProviderInterface;
import de.sub.goobi.persistence.managers.PropertyManager;
import ugh.dl.Prefs;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ ConfigPlugins.class, PropertyManager.class, StorageProvider.class })
@PowerMockIgnore({ "javax.management.*" })
public class RenameFilesPluginTest {
    private static final String DEFAULT_RETURN_PAGE = "pageBefore";
    private static final int DEFAULT_PROCESS_ID = 1;

    private Processproperty processProperty;
    private SubnodeConfiguration pluginConfiguration;
    private StorageProviderInterface storage;

    private Process process;
    private Ruleset ruleset;
    private Prefs rulesetPreferences;
    private Step step;

    private RenameFilesPlugin plugin;

    @Before
    public void setup() {
        rulesetPreferences = Mockito.mock(Prefs.class);
        ruleset = Mockito.mock(Ruleset.class);
        Mockito.when(ruleset.getPreferences()).thenReturn(rulesetPreferences);
        process = Mockito.mock(Process.class);
        Mockito.when(process.getId()).thenReturn(DEFAULT_PROCESS_ID);
        Mockito.when(process.getRegelsatz()).thenReturn(ruleset);
        step = Mockito.mock(Step.class);
        Mockito.when(step.getProzess()).thenReturn(process);

        processProperty = Mockito.mock(Processproperty.class);
        setupProcessPropertyMocking(processProperty);

        storage = Mockito.mock(StorageProviderInterface.class);
        setupStorageProviderMocking(storage);
    }

    private void setupStorageProviderMocking(StorageProviderInterface storageProviderInterface) {
        PowerMock.mockStatic(StorageProvider.class);
        EasyMock.expect(StorageProvider.getInstance())
                .andReturn(storageProviderInterface)
                .anyTimes();
        PowerMock.replay(StorageProvider.class);
    }

    private void setupConfigurationFileMocking(SubnodeConfiguration config) {
        PowerMock.mockStatic(ConfigPlugins.class);
        EasyMock.expect(ConfigPlugins.getProjectAndStepConfig(plugin.getTitle(), step))
                .andReturn(config)
                .anyTimes();
        PowerMock.replay(ConfigPlugins.class);
    }

    private void setupProcessPropertyMocking(Processproperty property) {
        PowerMock.mockStatic(PropertyManager.class);
        EasyMock.expect(PropertyManager.getProcessPropertiesForProcess(DEFAULT_PROCESS_ID))
                .andReturn(List.of(processProperty))
                .anyTimes();
        PowerMock.replay(PropertyManager.class);
    }

    private void initializate() {
        plugin = new RenameFilesPlugin();
        setupConfigurationFileMocking(pluginConfiguration);
        plugin.initialize(step, DEFAULT_RETURN_PAGE);
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

    @Test
    public void whenExecutingPlugin_givenWorkingDefaultPluginConfiguration_expectPluginSucceeding() {
        //        setupDefaultPluginConfiguration();
        initializate();

        assertTrue(plugin.execute());
    }

    @Test
    public void whenExecutingPlugin_givenWorkingDefaultPluginConfiguration_expectStorageDirectoryCheck() {
        //        setupPluginConfigurationWithNonExistingFolder();
        initializate();

        assertEquals(PluginReturnValue.ERROR, plugin.run());
    }
}
