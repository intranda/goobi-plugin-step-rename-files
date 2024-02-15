package de.intranda.goobi.plugins;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.collections4.list.TreeList;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.goobi.beans.Process;
import org.goobi.beans.Processproperty;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.persistence.managers.PropertyManager;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.Fileformat;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;

@Log4j2
@PluginImplementation
public class RenameFilesPlugin implements IStepPluginVersion2 {
    private static final long serialVersionUID = -5097830334502599546L;

    private static final String PROPERTY_TITLE = "plugin_intranda_step_rename_files";
    private static final String NAME_PART_TYPE_ORIGINALFILENAME = "originalfilename";

    private static Gson gson = new Gson();
    private static ConfigurationHelper configurationHelper = ConfigurationHelper.getInstance();

    @Getter
    private String title = "intranda_step_rename_files";
    @Getter
    private PluginType type = PluginType.Step;
    @Getter
    private PluginGuiType pluginGuiType = PluginGuiType.NONE;

    private Process process;
    private Processproperty property;
    @Getter
    private Step step;
    private String returnPath;

    private VariableReplacer variableReplacer;
    private List<String> configuredFoldersToRename;
    private List<NamePartConfiguration> renamingConfigurations;

    private Map<String, Map<String, String>> renamingLog = new HashMap<>();

    // ###################################################################################

    @Data
    @RequiredArgsConstructor
    @AllArgsConstructor
    public class NamePartConfiguration {
        public boolean allConditionsMatch(VariableReplacer replacer) {
            return this.conditions.stream().allMatch(c -> c.matches(replacer));
        }

        @NonNull
        private String namePartValue;
        @NonNull
        private String namePartType;
        @NonNull
        private List<ReplacementConfiguration> replacements;
        @NonNull
        private List<ConditionConfiguration> conditions;
        private NumberFormat format;
    }

    @Data
    @RequiredArgsConstructor
    public class ConditionConfiguration {
        public boolean matches(VariableReplacer replacer) {
            String replacedValue = replacer.replace(value);
            return replacedValue.matches(matches);
        }

        @NonNull
        private String value;
        @NonNull
        private String matches;
    }

    @Data
    @RequiredArgsConstructor
    public class ReplacementConfiguration {
        public String replace(String value) {
            return value.replaceAll(regex, replacement);
        }

        @NonNull
        private String regex;
        @NonNull
        private String replacement;
    }

    // ###################################################################################

    @Override
    public void initialize(Step step, String returnPath) {
        log.debug("================= Starting RenameFilesPlugin =================");
        this.step = step;
        this.process = step.getProzess();
        this.returnPath = returnPath;
        SubnodeConfiguration myconfig = ConfigPlugins.getProjectAndStepConfig(title, step);
        loadPluginConfiguration(myconfig);
        this.property = initializeProcessProperty(step.getProzess());
        this.renamingLog = deserializeRenamingLogFromJson(this.property.getWert());
    }

    private void loadPluginConfiguration(SubnodeConfiguration config) {
        configuredFoldersToRename = config.getList("folder", List.of("*"))
                .stream()
                .map(Object::toString)
                .collect(Collectors.toList());
        log.debug("configuredFoldersToRename = " + configuredFoldersToRename);

        // TODO: Stream map
        renamingConfigurations = new ArrayList<>();
        config.configurationsAt("namepart")
                .stream()
                .forEachOrdered(namePartConfigXML -> {
                    NamePartConfiguration npc = new NamePartConfiguration(namePartConfigXML.getString("."),
                            namePartConfigXML.getString("@type", "static"),
                            parseReplacements(namePartConfigXML.configurationsAt("replace")),
                            parseConditions(namePartConfigXML.configurationsAt("condition")));
                    renamingConfigurations.add(npc);
                });

        //        this.updateMetsFile = config.getBoolean("metsFile/update", false);
        //        startValue = config.getInt("startValue", 1);
    }

    private @NonNull List<ReplacementConfiguration> parseReplacements(
            List<HierarchicalConfiguration> replacementConfigs) {
        return replacementConfigs.stream()
                .map(config -> new ReplacementConfiguration(config.getString("@regex", ""),
                        config.getString("@replacement", "")))
                .collect(Collectors.toList());
    }

    private @NonNull List<ConditionConfiguration> parseConditions(List<HierarchicalConfiguration> configs) {
        return configs.stream()
                .map(
                        config -> new ConditionConfiguration(config.getString("@value", ""), config.getString("@matches", "")))
                .collect(Collectors.toList());
    }

    private Processproperty initializeProcessProperty(Process process) {
        List<Processproperty> props = PropertyManager.getProcessPropertiesForProcess(process.getId());
        for (Processproperty p : props) {
            if (PROPERTY_TITLE.equals(p.getTitel())) {
                return p;
            }
        }

        property = new Processproperty();
        property.setProcessId(process.getId());
        property.setTitel(PROPERTY_TITLE);
        return property;
    }

    private void saveProcessProperty() {
        property.setWert(serializeRenamingLogIntoJson(renamingLog));
        PropertyManager.saveProcessProperty(property);
    }

    private Map<String, Map<String, String>> deserializeRenamingLogFromJson(String json) {
        Type renamingLogType = new TypeToken<Map<String, Map<String, String>>>() {
        }.getType();
        Map<String, Map<String, String>> renamingLog = gson.fromJson(property.getWert(), renamingLogType);
        // Initialize empty, if deserialization was not successful
        if (renamingLog == null) {
            renamingLog = new HashMap<>();
        }
        return renamingLog;
    }

    private String serializeRenamingLogIntoJson(Map<String, Map<String, String>> renamingLog) {
        return gson.toJson(renamingLog);
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null; // NOSONAR
    }

    @Override
    public int getInterfaceVersion() {
        return 0;
    }

    @Override
    public String cancel() {
        return returnPath;
    }

    @Override
    public boolean execute() {
        return PluginReturnValue.FINISH.equals(run());
    }

    @Override
    public String finish() {
        return returnPath;
    }

    @Override
    public String getPagePath() {
        return null;
    }

    // ###################################################################################

    @Override
    public PluginReturnValue run() {
        try {
            variableReplacer = getVariableReplacer();
            List<Path> foldersToRename = determineFoldersToRename();
            Map<Path, Path> renamingMapping = determineRenamingForAllFilesInAllFolders(foldersToRename);
        } catch (IOException | PluginException | SwapException | DAOException e) {
            log.error(e.getMessage());
            log.error(e);
            return PluginReturnValue.ERROR;
        }

        return PluginReturnValue.FINISH;
    }

    private VariableReplacer getVariableReplacer() throws PluginException {
        try {
            Fileformat fileformat = process.readMetadataFile();
            return new VariableReplacer(fileformat != null ? fileformat.getDigitalDocument() : null,
                    process.getRegelsatz().getPreferences(), process, step);
        } catch (ReadException | IOException | SwapException | PreferencesException e1) {
            throw new PluginException("Errors happened while trying to initialize the Fileformat and VariableReplacer", e1);
        }
    }

    private List<Path> determineFoldersToRename() throws IOException, SwapException, DAOException {
        List<Path> result = new TreeList<>();
        for (String folderSpecification : configuredFoldersToRename) {
            result.addAll(determineRealPathsForConfiguredFolder(folderSpecification));
        }
        return result;
    }

    private List<Path> determineRealPathsForConfiguredFolder(String configuredFolder) throws IOException, SwapException, DAOException {
        if ("*".equals(configuredFolder)) {
            return determineDefaultFoldersToRename();
        } else {
            return transformConfiguredFolderSpecificationToRealPath(configuredFolder);
        }
    }

    private List<Path> determineDefaultFoldersToRename() throws IOException, SwapException, DAOException {
        return List.of(
                Paths.get(process.getImagesOrigDirectory(false)),
                Paths.get(process.getImagesTifDirectory(false)),
                Paths.get(process.getOcrAltoDirectory()),
                Paths.get(process.getOcrPdfDirectory()),
                Paths.get(process.getOcrTxtDirectory()),
                Paths.get(process.getOcrXmlDirectory()));
    }

    private List<Path> transformConfiguredFolderSpecificationToRealPath(String folderSpecification) throws IOException, SwapException {
        String folder = configurationHelper.getAdditionalProcessFolderName(folderSpecification);
        folder = variableReplacer.replace(folder);
        Path configuredFolder = Paths.get(process.getImagesDirectory(), folder);
        return List.of(configuredFolder);
    }

    private Map<Path, Path> determineRenamingForAllFilesInAllFolders(List<Path> foldersToRename) throws PluginException {
        Map<Path, Path> result = new TreeMap<>();
        for (Path folder : foldersToRename) {
            result.putAll(determineRenamingForAllFilesInFolder(folder));
        }
        return result;
    }

    private Map<Path, Path> determineRenamingForAllFilesInFolder(Path folder) throws PluginException {
        if (!StorageProvider.getInstance().isDirectory(folder)) {
            throw new PluginException("Cannot rename all files in directory. The given path \"" + folder.toString() + "\" is not a directory");
        }

        return Collections.emptyMap();
    }
}
