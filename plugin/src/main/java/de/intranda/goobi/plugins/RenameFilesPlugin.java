package de.intranda.goobi.plugins;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

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
import com.google.gson.annotations.SerializedName;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.persistence.managers.PropertyManager;
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

    public static final String PROPERTY_TITLE = "plugin_intranda_step_rename_files";
    private static final String NAME_PART_TYPE_STATIC = "static";
    private static final String NAME_PART_TYPE_COUNTER = "counter";
    private static final String NAME_PART_TYPE_VARIABLE = "variable";
    private static final String NAME_PART_TYPE_ORIGINAL_FILE_NAME = "originalfilename";
    private static final String CUSTOM_VARIABLE_ORIGINAL_FILE_NAME = "{" + NAME_PART_TYPE_ORIGINAL_FILE_NAME + "}";

    private Gson gson = new Gson();
    private ConfigurationHelper configurationHelper = ConfigurationHelper.getInstance();
    private MetsFileUpdater metsFileUpdater = MetsFileUpdater.getInstance();

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
    private RenamingFormatter renamingFormatter;

    private boolean updateMetsFile;
    // Must be visible in test to compare correct update
    OriginalFileNameHistory originalFileNameHistory;

    // ###################################################################################
    // # Required plugin methods
    // ###################################################################################

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
    // # Original File Name History class
    // ###################################################################################

    class OriginalFileNameHistory {
        @SerializedName("originalFileNameMapping")
        private Map<String, Map<String, String>> perFolderCurrentToOriginalFileNameMapping = new HashMap<>();

        public String getOriginalFileNameOf(Path currentFilePath) {
            Map<String, String> folderMapping = getOriginalFileNameMappingOfFolder(extractFolderIdentifier(currentFilePath));
            String currentFileName = extractFileName(currentFilePath);
            if (!folderMapping.containsKey(currentFileName)) {
                return currentFileName;
            }
            return folderMapping.get(currentFileName);
        }

        public void updateFileName(Path from, Path to) {
            String fromFolder = extractFolderIdentifier(from);
            String toFolder = extractFolderIdentifier(to);
            if (!fromFolder.equals(toFolder)) {
                throw new IllegalArgumentException("Moving files between folders (" + fromFolder + " -> " + toFolder + ") not permitted!");
            }
            Map<String, String> folderMapping = getOriginalFileNameMappingOfFolder(fromFolder);
            String fromFile = extractFileName(from);
            String toFile = extractFileName(to);
            String originalFileName = fromFile;
            if (folderMapping.containsKey(fromFile)) {
                originalFileName = folderMapping.get(fromFile);
                folderMapping.remove(fromFile);
            }
            folderMapping.put(toFile, originalFileName);
        }

        private String extractFolderIdentifier(Path path) {
            // If path is pointing to a file, use the parent directory for folder identifier calculation
            if (path.getFileName().toString().contains(".")) {
                path = path.getParent();
            }
            return path.getParent().getFileName().toString() + "_"
                    + path.getFileName().toString().substring(path.getFileName().toString().lastIndexOf("_") + 1);
        }

        private String extractFileName(Path path) {
            return path.getFileName().toString();
        }

        private Map<String, String> getOriginalFileNameMappingOfFolder(String folderIdentifier) {
            if (!perFolderCurrentToOriginalFileNameMapping.containsKey(folderIdentifier)) {
                perFolderCurrentToOriginalFileNameMapping.put(folderIdentifier, new HashMap<>());
            }
            return perFolderCurrentToOriginalFileNameMapping.get(folderIdentifier);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || !(o instanceof OriginalFileNameHistory)) {
                return false;
            }
            OriginalFileNameHistory other = (OriginalFileNameHistory) o;
            return other.perFolderCurrentToOriginalFileNameMapping.equals(perFolderCurrentToOriginalFileNameMapping);
        }

        @Override
        public String toString() {
            return perFolderCurrentToOriginalFileNameMapping.toString();
        }
    }

    // ###################################################################################
    // # Name formatter classes
    // ###################################################################################

    class OverlayVariableReplacer {
        private final VariableReplacer variableReplacer;

        public OverlayVariableReplacer(VariableReplacer variableReplacer) {
            this.variableReplacer = variableReplacer;
        }

        public String replace(Path fileName, String replacement) {
            replacement = internalReplacer(fileName, replacement);
            return variableReplacer.replace(replacement);
        }

        private String internalReplacer(Path fileName, String replacement) {
            if (CUSTOM_VARIABLE_ORIGINAL_FILE_NAME.equals(replacement)) {
                String originalFileName = originalFileNameHistory.getOriginalFileNameOf(fileName);
                // Remove file extension
                int fileExtensionIndex = originalFileName.lastIndexOf('.');
                return originalFileName.substring(0, fileExtensionIndex);
            }
            return replacement;
        }
    }

    class RenamingFormatter {
        @NonNull
        private final List<NamePart> nameParts;
        @NonNull
        @Getter
        private final OverlayVariableReplacer replacer;
        @Getter
        private final int startValue;

        public RenamingFormatter(OverlayVariableReplacer replacer, List<NamePart> nameParts, int startValue) {
            this.replacer = replacer;
            this.nameParts = nameParts;
            this.startValue = startValue;
            reset();
        }

        public void reset() {
            nameParts.stream().forEach(np -> np.reset(this));
        }

        public String generateNewName(Path oldName) {
            StringBuilder sb = new StringBuilder();
            nameParts.stream().forEachOrdered(np -> sb.append(np.generateNamePart(oldName)));
            return sb.toString();
        }
    }

    @Data
    @RequiredArgsConstructor
    abstract class NamePart {
        private OverlayVariableReplacer replacer;

        public boolean allConditionsMatch(Path oldName) {
            return this.conditions.stream().allMatch(c -> c.matches(replacer, oldName));
        }

        @NonNull
        private List<NamePartReplacement> replacements;
        @NonNull
        private List<NamePartCondition> conditions;

        public String generateNamePart(Path oldName) {
            if (!allConditionsMatch(oldName)) {
                return "";
            }
            String result = generate(oldName);
            for (NamePartReplacement r : replacements) {
                result = r.replace(result);
            }
            return result;
        }

        protected abstract String generate(Path oldName);

        protected void reset(RenamingFormatter parent) {
            this.replacer = parent.getReplacer();
        }
    }

    @RequiredArgsConstructor
    class NamePartCondition {
        public boolean matches(OverlayVariableReplacer replacer, Path oldName) {
            String replacedValue = replacer.replace(oldName, value);
            return replacedValue.matches(matches);
        }

        @NonNull
        private String value;
        @NonNull
        private String matches;
    }

    @RequiredArgsConstructor
    class NamePartReplacement {
        public String replace(String value) {
            return value.replaceAll(regex, replacement);
        }

        @NonNull
        private String regex;
        @NonNull
        private String replacement;
    }

    class StaticNamePart extends NamePart {
        private String staticPart;

        public StaticNamePart(@NonNull List<NamePartReplacement> replacements, @NonNull List<NamePartCondition> conditions, String staticPart) {
            super(replacements, conditions);
            this.staticPart = staticPart;
        }

        @Override
        protected String generate(Path oldName) {
            return this.staticPart;
        }
    }

    class CounterNamePart extends NamePart {
        private NumberFormat format;
        private int counter = 1;

        public CounterNamePart(@NonNull List<NamePartReplacement> replacements, @NonNull List<NamePartCondition> conditions, String format) {
            super(replacements, conditions);
            this.format = new DecimalFormat(format);
        }

        @Override
        protected String generate(Path oldName) {
            return format.format(counter++);
        }

        @Override
        protected void reset(RenamingFormatter parent) {
            super.reset(parent);
            this.counter = parent.getStartValue();
        }
    }

    class VariableNamePart extends NamePart {
        private String rawString;

        public VariableNamePart(@NonNull List<NamePartReplacement> replacements, @NonNull List<NamePartCondition> conditions, String rawString) {
            super(replacements, conditions);
            this.rawString = rawString;
        }

        @Override
        protected String generate(Path oldName) {
            return getReplacer().replace(oldName, rawString);
        }
    }

    // ###################################################################################
    // # Plugin initialization and formatter setup
    // ###################################################################################

    @Override
    public void initialize(Step step, String returnPath) {
        log.debug("================= Starting RenameFilesPlugin =================");
        this.step = step;
        this.process = step.getProzess();
        this.returnPath = returnPath;
        // TODO: Plugin initialization should also throw exceptions!
        try {
            this.variableReplacer = getVariableReplacer();
            SubnodeConfiguration myconfig = ConfigPlugins.getProjectAndStepConfig(title, step);
            loadPluginConfiguration(myconfig);
        } catch (PluginException e) {
            log.error(e.getMessage());
            log.error(e);
        }
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

    private void loadPluginConfiguration(SubnodeConfiguration config) throws PluginException {
        configuredFoldersToRename = config.getList("folder", List.of("*"))
                .stream()
                .map(Object::toString)
                .collect(Collectors.toList());
        log.debug("configuredFoldersToRename = " + configuredFoldersToRename);

        int counterStartValue = config.getInt("startValue", 1);

        try {
            List<NamePart> nameParts = config.configurationsAt("namepart")
                    .stream()
                    .map(this::parseNamePartConfiguration)
                    .collect(Collectors.toList());
            renamingFormatter = new RenamingFormatter(new OverlayVariableReplacer(getVariableReplacer()), nameParts, counterStartValue);
        } catch (IllegalArgumentException e) {
            throw new PluginException("Error during namepart parsing!", e);
        }

        this.updateMetsFile = config.getBoolean("metsFile/update", false);
    }

    private NamePart parseNamePartConfiguration(HierarchicalConfiguration namePartXML) throws IllegalArgumentException {
        String type = namePartXML.getString("@type");
        String value = namePartXML.getString(".");
        List<NamePartReplacement> replacements = parseReplacements(namePartXML.configurationsAt("replace"));
        List<NamePartCondition> conditions = parseConditions(namePartXML.configurationsAt("condition"));

        switch (type) {
            case NAME_PART_TYPE_STATIC:
                return new StaticNamePart(replacements, conditions, value);
            case NAME_PART_TYPE_COUNTER:
                return new CounterNamePart(replacements, conditions, value);
            case NAME_PART_TYPE_VARIABLE:
                return new VariableNamePart(replacements, conditions, value);
            case NAME_PART_TYPE_ORIGINAL_FILE_NAME:
                return new VariableNamePart(replacements, conditions, CUSTOM_VARIABLE_ORIGINAL_FILE_NAME);
            default:
                throw new IllegalArgumentException("Unable to parse namepart configuration of type \"" + type + "\"!");
        }
    }

    private @NonNull List<NamePartReplacement> parseReplacements(
            List<HierarchicalConfiguration> replacementConfigs) {
        return replacementConfigs.stream()
                .map(config -> new NamePartReplacement(config.getString("@regex", ""),
                        config.getString("@replacement", "")))
                .collect(Collectors.toList());
    }

    private @NonNull List<NamePartCondition> parseConditions(List<HierarchicalConfiguration> configs) {
        return configs.stream()
                .map(
                        config -> new NamePartCondition(config.getString("@value", ""), config.getString("@matches", "")))
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

    private void updateProcessPropertyWithNewFileNameHistory() {
        property.setWert(serializeOriginalFileNameHistoryIntoJson(originalFileNameHistory));
    }

    private void saveProcessProperty() {
        PropertyManager.saveProcessProperty(property);
    }

    private OriginalFileNameHistory deserializeOriginalFileNameHistoryFromJson(String json) {
        OriginalFileNameHistory originalFileNameHistory = gson.fromJson(json, OriginalFileNameHistory.class);
        // Initialize empty, if deserialization was not successful
        if (originalFileNameHistory == null) {
            originalFileNameHistory = new OriginalFileNameHistory();
        }
        return originalFileNameHistory;
    }

    private String serializeOriginalFileNameHistoryIntoJson(OriginalFileNameHistory originalFileNameHistory) {
        return gson.toJson(originalFileNameHistory);
    }

    // ###################################################################################
    // # Renaming Algorithm
    // ###################################################################################

    @Override
    public PluginReturnValue run() {
        try {
            property = initializeProcessProperty(step.getProzess());
            originalFileNameHistory = deserializeOriginalFileNameHistoryFromJson(this.property.getWert());
            List<Path> foldersToRename = determineFoldersToRename();
            Map<Path, Path> renamingMapping = determineRenamingForAllFilesInAllFolders(foldersToRename);
            performRenaming(renamingMapping);
            if (updateMetsFile) {
                metsFileUpdater.updateMetsFile(null, null);
            }
            updateProcessPropertyWithNewFileNameHistory();
            saveProcessProperty();
        } catch (IOException | PluginException | SwapException | DAOException e) {
            log.error(e.getMessage());
            log.error(e);
            return PluginReturnValue.ERROR;
        }

        return PluginReturnValue.FINISH;
    }

    private List<Path> determineFoldersToRename() throws IOException, SwapException, DAOException {
        List<Path> result = new LinkedList<>();
        for (String folderSpecification : configuredFoldersToRename) {
            result.addAll(determineRealPathsForConfiguredFolder(folderSpecification));
        }
        return result.stream().distinct().collect(Collectors.toList());
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
                Paths.get(process.getOcrXmlDirectory()))
                .stream()
                .filter(this::pathIsPresent)
                .collect(Collectors.toList());
    }

    private List<Path> transformConfiguredFolderSpecificationToRealPath(String folderSpecification) throws IOException, SwapException {
        String folder = configurationHelper.getAdditionalProcessFolderName(folderSpecification);
        folder = variableReplacer.replace(folder);
        Path configuredFolder = Paths.get(process.getImagesDirectory(), folder);
        return List.of(configuredFolder);
    }

    private boolean pathIsPresent(Path path) {
        return StorageProvider.getInstance().isDirectory(path);
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

        Map<Path, Path> result = new TreeMap<>();

        List<Path> filesToRename = StorageProvider.getInstance().listFiles(folder.toString());
        renamingFormatter.reset();

        for (Path file : filesToRename) {
            String oldFullFileName = file.getFileName().toString();
            int extensionIndex = oldFullFileName.lastIndexOf(".");
            String fileExtension = oldFullFileName.substring(extensionIndex + 1);
            String newFullFileName = renamingFormatter.generateNewName(file) + "." + fileExtension;

            if (!oldFullFileName.equals(newFullFileName)) {
                result.put(Paths.get(folder.toString(), oldFullFileName), Paths.get(folder.toString(), newFullFileName));
            }
        }

        return result;
    }

    private void performRenaming(Map<Path, Path> renamingMap) throws IOException {
        try {
            for (Map.Entry<Path, Path> e : renamingMap.entrySet()) {
                StorageProvider.getInstance().move(e.getKey(), e.getValue());
                originalFileNameHistory.updateFileName(e.getKey(), e.getValue());
            }
        } catch (IOException e) {
            log.error("Error during renaming. The renamed files might be inconsistent");
            throw e;
        }
    }
}
