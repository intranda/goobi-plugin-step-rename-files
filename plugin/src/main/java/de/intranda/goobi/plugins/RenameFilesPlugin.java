package de.intranda.goobi.plugins;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Process;
import org.goobi.beans.Processproperty;
import org.goobi.beans.Step;
import org.goobi.production.enums.LogType;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.Helper;
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

    @Getter
    private String title = "intranda_step_rename_files";
    @Getter
    private PluginType type = PluginType.Step;
    @Getter
    private PluginGuiType pluginGuiType = PluginGuiType.NONE;

    @Getter
    private Process process;
    @Getter
    private Step step;
    private String returnPath;
    private transient List<NamePartConfiguration> namePartList;
    private int startValue = 1;
    private List<String> foldersConfigured = List.of("*");
    private boolean updateMetsFile = false;
    private static ConfigurationHelper configHelper = ConfigurationHelper.getInstance();
    private static Gson gson = new Gson();

    private Processproperty property;

    private Map<String, Map<String, String>> renamingLog = new HashMap<>();

    @Override
    public PluginReturnValue run() {
        // prepare the VariableReplacer before trying to get the folder list, since it is needed when a folder is configured specifically
        VariableReplacer replacer = getVariableReplacer(process);

        List<Path> folders = getFolderList(process, replacer);

        for (Path folder : folders) {
            log.debug("object to rename: " + folder.getFileName().toString());
        }

        // only use name parts for which all conditions apply
        namePartList = namePartList.stream()
                .filter(npc -> npc.allConditionsMatch(replacer))
                .collect(Collectors.toList());

        for (NamePartConfiguration npc : namePartList) {
            if ("variable".equalsIgnoreCase(npc.getNamePartType())) {
                String replacementValue = replacer.replace(npc.getNamePartValue());
                for (ReplacementConfiguration replacement : npc.getReplacements()) {
                    replacementValue = replacement.replace(replacementValue);
                }
                npc.setNamePartValue(replacementValue);
            } else if ("counter".equalsIgnoreCase(npc.getNamePartType())) {
                npc.setFormat(new DecimalFormat(npc.getNamePartValue()));
            }
        }

        /*
         * First, create renaming rules for all files and check if they are valid. If all rules can be applied, do it.
         * Otherwise, stop the processing and don't rename any files at all.
         */
        Map<Path, Path> renamingQueue = new LinkedHashMap<>();
        // rename files in each folder
        for (Path folder : folders) {
            try {
                renamingQueue.putAll(renameFilesInFolder(folder));
            } catch (IOException e) {
                log.error("IOException caught while trying to rename the files in " + folder.toString());
                Helper.setMeldung("Error renaming file in folder " + folder);
                Helper.addMessageToProcessJournal(process.getId(), LogType.ERROR,
                        "Error renaming file in " + folder + ". Reason: " + e.toString(), "");
                revertFiles(renamedFileMap);
                return PluginReturnValue.ERROR;
            }

            // move all the files from the temp folder back again
            try {
                moveFilesFromTempBack(folder);
            } catch (IOException e) {
                log.error("IOException caught while trying to get files from the temp folder back.");
                Helper.setMeldung("Error moving files from temp folder in " + folder);
                Helper.addMessageToProcessJournal(process.getId(), LogType.ERROR,
                        "Error moving files from temp folder in " + folder + ". Reason: " + e.toString(), "");
                revertFiles(renamedFileMap);
                return PluginReturnValue.ERROR;
            }
        }

        if (this.updateMetsFile) {
            try {
                Map<String, String> filenameMap = createFilenameMap(renamedFileMap);
                new MetsFileUpdater().updateMetsFile(process, filenameMap);
            } catch (IOException e1) {
                log.error(
                        "IOException while trying to update mets file of process id={}. Filename changes won't be reflected in the mets file",
                        process.getId(), e1);
                Helper.setMeldung(
                        "IOException while trying to update mets file. Filename changes won't be reflected in the mets file. Please open Mets-Editor and save to correct this");
                Helper.addMessageToProcessJournal(process.getId(), LogType.ERROR,
                        "IOException while trying to update mets file. Filename changes won't be reflected in the mets file. Please open Mets-Editor and save to correct this",
                        "");
                return PluginReturnValue.ERROR;
            }
        }

        Helper.setMeldung("Renamed images in all folder");
        Helper.addMessageToProcessJournal(process.getId(), LogType.DEBUG, "Renamed images in all folder", "");

        return PluginReturnValue.FINISH;
    }

    private Map<String, String> createFilenameMap(Map<Path, Path> renamedFileMap) {
        Map<String, String> filenameMap = new LinkedHashMap<>();
        for (Map.Entry<Path, Path> entry : renamedFileMap.entrySet()) {
            String origFilename = MetsFileUpdater.getBasename(entry.getKey());
            String newFilename = MetsFileUpdater.getBasename(entry.getValue());
            filenameMap.put(origFilename, newFilename);
        }
        return filenameMap;
    }

    private VariableReplacer getVariableReplacer(Process process) {
        Fileformat fileformat = null;
        VariableReplacer replacer = null;
        try {
            fileformat = process.readMetadataFile();
            replacer = new VariableReplacer(fileformat != null ? fileformat.getDigitalDocument() : null,
                    process.getRegelsatz().getPreferences(), process, step);
        } catch (ReadException | IOException | SwapException | PreferencesException e1) {
            log.error("Errors happened while trying to initialize the Fileformat and VariableReplacer.");
            log.error(e1);
        }
        return replacer;
    }

    private Map<String, String> getFolderRenamingLog(Path folder) {
        String folderIdentifier = extractFolderIdentifier(folder);
        if (!renamingLog.containsKey(folderIdentifier)) {
            renamingLog.put(folderIdentifier, new HashMap<>());
        }
        return renamingLog.get(folderIdentifier);
    }

    private String extractFolderIdentifier(Path folder) {
        return folder.getParent().getFileName().toString() + "_"
                + folder.getFileName().toString().substring(folder.getFileName().toString().lastIndexOf("_") + 1);
    }

    private List<Path> getFolderList(Process process, VariableReplacer replacer)
            throws IOException, SwapException, DAOException {
        List<Path> folders = new ArrayList<>();

        for (String folderSpecified : foldersConfigured) {
            try {
                if (StringUtils.isBlank(folderSpecified) || "*".equals(folderSpecified)) {
                    getFolderListDefault(process, folders);
                } else {
                    getFolderListConfigured(process, folders, replacer, folderSpecified);
                }
            } catch (IOException | SwapException | DAOException e) {
                log.error("Errors happened while trying to get the list of folders.");
                log.error(e);
            }
        }
        return folders.stream().distinct().collect(Collectors.toList());
    }

    private void getFolderListDefault(Process process, List<Path> folders)
            throws IOException, SwapException, DAOException {
        Path masterFolder = Paths.get(process.getImagesOrigDirectory(false));
        Path derivateFolder = Paths.get(process.getImagesTifDirectory(false));
        Path altoFolder = Paths.get(process.getOcrAltoDirectory());
        Path pdfFolder = Paths.get(process.getOcrPdfDirectory());
        Path txtFolder = Paths.get(process.getOcrTxtDirectory());
        Path xmlFolder = Paths.get(process.getOcrXmlDirectory());

        if (Files.exists(masterFolder)) {
            log.debug("add masterfolder: " + masterFolder.getFileName().toString());
            folders.add(masterFolder);
        }
        if (Files.exists(derivateFolder)
                && !masterFolder.getFileName().toString().equals(derivateFolder.getFileName().toString())) {
            log.debug("add derivateFolder: " + derivateFolder.getFileName().toString());
            folders.add(derivateFolder);
        }

        if (Files.exists(altoFolder)) {
            log.debug("add altoFolder: " + altoFolder.getFileName().toString());
            folders.add(altoFolder);
        }
        if (Files.exists(pdfFolder)) {
            log.debug("add pdfFolder: " + pdfFolder.getFileName().toString());
            folders.add(pdfFolder);
        }
        if (Files.exists(txtFolder)) {
            log.debug("add txtFolder: " + txtFolder.getFileName().toString());
            folders.add(txtFolder);
        }
        if (Files.exists(xmlFolder)) {
            log.debug("add xmlFolder: " + xmlFolder.getFileName().toString());
            folders.add(xmlFolder);
        }
    }

    private void getFolderListConfigured(Process process, List<Path> folders, VariableReplacer replacer,
            String folderConfigured) throws IOException, SwapException {
        if (replacer == null) {
            // an error should be triggered here since replacer is needed
            log.error("VariableReplacer is needed here, but it is null!");
            throw new SwapException("Null is not a valid VariableReplacer!");
        }

        String folder = configHelper.getAdditionalProcessFolderName(folderConfigured);
        log.debug("folderConfigured before replacement = " + folder);
        folder = replacer.replace(folder);
        log.debug("folderConfigured after replacement = " + folder);
        Path configuredFolder = Paths.get(process.getImagesDirectory(), folder);

        if (Files.exists(configuredFolder)) {
            log.debug("add configuredFolder: " + configuredFolder.getFileName().toString());
            folders.add(configuredFolder);
        }
    }

    //  private Map<Path, Path> renameFilesInFolder(Path folder) throws IOException {
    private void renameFilesInFolder(Path folder, Map<String, String> folderRenamingLog) throws IOException {
        if (!StorageProvider.getInstance().isDirectory(folder)) {
            throw new IOException("Cannot rename all files in directory. The given path \"" + folder.toString() + "\" is not a directory");
        }

        int counter = startValue;
        List<Path> filesInFolder = StorageProvider.getInstance().listFiles(folder.toString());
        Map<Path, Path> filenameMap = new HashMap<>(filesInFolder.size());
        for (Path file : filesInFolder) {
            if (StorageProvider.getInstance().isDirectory(file)) {
                throw new IOException("Cannot rename file in directory. The given path \"" + file.toString() + "\" is a directory");
            }
            log.debug("start renaming file: " + file.getFileName().toString());
            String oldFileName = file.getFileName().toString();
            String extension = oldFileName.substring(oldFileName.lastIndexOf(".") + 1);
            // check if it is the barcode image
            String fileName = null;
            // TODO check, if the counter is set to "0"
            if (oldFileName.contains("barcode")) {
                //    rename it with '0' as counter
                fileName = generateNewFileName(folderRenamingLog, oldFileName, 0, extension);
            } else {
                // create new filename
                fileName = generateNewFileName(folderRenamingLog, oldFileName, counter, extension);
                counter++;
            }
            // if old and new filename don't match, rename it
            if (!oldFileName.equals(fileName) && tryRenameFile(file, fileName)) {
                updateRenameLog(folderRenamingLog, oldFileName, fileName);

                //            if (!oldFileName.equals(fileName)) {
                //                Path newFilePath = tryRenameFile(file, fileName);
                //                filenameMap.put(file, newFilePath);
            }
        }
        return filenameMap;
    }

    private void updateRenameLog(Map<String, String> renamingLog, String from, String to) {
        String originalFileName = from;
        if (renamingLog.containsKey(from)) {
            originalFileName = renamingLog.get(from);
            renamingLog.remove(from);
        }
        renamingLog.put(to, originalFileName);
    }

    private String generateNewFileName(Map<String, String> folderRenamingLog, String currentFileName, int counter,
            String extension) {
        StringBuilder sb = new StringBuilder();
        for (NamePartConfiguration npc : namePartList) {
            if (npc.getFormat() != null) {
                sb.append(npc.getFormat().format(counter));
            } else if (NAME_PART_TYPE_ORIGINALFILENAME.equals(npc.getNamePartType())) {
                String originalFileName = folderRenamingLog.getOrDefault(currentFileName, currentFileName);
                sb.append(getFileNameWithoutExtension(originalFileName));
            } else {
                sb.append(npc.getNamePartValue());
            }
        }
        if (StringUtils.isNotBlank(extension)) {
            sb.append(".");
            sb.append(extension);
        }
        return sb.toString();
    }

    private String getFileNameWithoutExtension(String originalFileName) {
        if (!originalFileName.contains(".")) {
            return originalFileName;
        }
        return originalFileName.substring(0, originalFileName.lastIndexOf('.'));
    }

    private Path tryRenameFile(Path filePath, String newFileName) throws IOException {
        Path targetPath = Paths.get(filePath.getParent().toString(), newFileName);
        if (StorageProvider.getInstance().isFileExists(targetPath)) {
            log.debug("targetPath is occupied: " + targetPath.toString());
            log.debug("Moving the file " + newFileName + " to temp folder for the moment instead.");
            // move files to a temp folder
            return moveFileToTempFolder(filePath, newFileName);
        } else {
            StorageProvider.getInstance().move(filePath, targetPath);
            return targetPath;
        }
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

    @Override
    public void initialize(Step step, String returnPath) {
        log.debug("================= Starting RenameFilesPlugin =================");
        this.step = step;
        this.process = step.getProzess();
        this.returnPath = returnPath;
        SubnodeConfiguration myconfig = ConfigPlugins.getProjectAndStepConfig(title, step);
        initConfig(myconfig);
        this.property = initializeProcessProperty(step.getProzess());
        this.renamingLog = deserializeRenamingLogFromJson(this.property.getWert());
    }

    private void initConfig(SubnodeConfiguration config) {
        startValue = config.getInt("startValue", 1);
        foldersConfigured = config.getList("folder", List.of("*"))
                .stream()
                .map(Object::toString)
                .collect(Collectors.toList());
        log.debug("foldersConfigured = " + foldersConfigured);
        namePartList = new ArrayList<>();
        this.updateMetsFile = config.getBoolean("metsFile/update", false);
        List<HierarchicalConfiguration> fields = config.configurationsAt("namepart");
        for (HierarchicalConfiguration hc : fields) {
            NamePartConfiguration npc = new NamePartConfiguration(hc.getString("."), hc.getString("@type", "static"),
                    parseReplacements(hc.configurationsAt("replace")),
                    parseConditions(hc.configurationsAt("condition")));
            namePartList.add(npc);
        }
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

    private void saveProcessProperty() {
        property.setWert(serializeRenamingLogIntoJson(renamingLog));
        PropertyManager.saveProcessProperty(property);
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

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null; // NOSONAR
    }

    @Override
    public int getInterfaceVersion() {
        return 0;
    }

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
}
