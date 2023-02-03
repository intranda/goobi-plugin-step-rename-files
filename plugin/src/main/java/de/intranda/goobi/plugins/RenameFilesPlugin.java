package de.intranda.goobi.plugins;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Process;
import org.goobi.beans.Step;
import org.goobi.production.enums.LogType;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
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

    @Getter
    private Step step;
    private String returnPath;
    private List<NamePartConfiguration> namePartList;
    private int startValue = 1;
    private String folderConfigured = "*";
    private static ConfigurationHelper configHelper = ConfigurationHelper.getInstance();
    //    private static StorageProvider provider = (StorageProvider) StorageProvider.getInstance();

    @Getter
    private String title = "intranda_step_rename_files";
    @Getter
    private PluginType type = PluginType.Step;

    @Getter
    private PluginGuiType pluginGuiType = PluginGuiType.NONE;

    @Override
    public PluginReturnValue run() {
        Process process = step.getProzess();
        List<Path> folders = new ArrayList<>();

        // prepare the VariableReplacer before trying to get the folder list, since it is needed when a folder is configured specifically
        Fileformat fileformat = null;
        VariableReplacer replacer = null;
        try {
            fileformat = process.readMetadataFile();
            replacer = new VariableReplacer(fileformat != null ? fileformat.getDigitalDocument() : null,
                    process.getRegelsatz().getPreferences(), process, step);
        } catch (ReadException | IOException | SwapException | PreferencesException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        // get folder list
        try {
            getFolderList(process, folders, replacer, folderConfigured);

        } catch (IOException | SwapException | DAOException e) {
            log.error(e);
        }

        for (Path folder : folders) {
            log.debug("object to rename: " + folder.getFileName().toString());
        }

        // create filename rule
        for (NamePartConfiguration npc : namePartList) {
            if (npc.getNamePartType().equalsIgnoreCase("variable")) {
                npc.setNamePartValue(replacer.replace(npc.getNamePartValue()));
            } else if (npc.getNamePartType().equalsIgnoreCase("counter")) {
                npc.setFormat(new DecimalFormat(npc.getNamePartValue()));
            }
        }
        // for each folder
        for (Path folder : folders) {
            log.debug("start renaming inside of: " + folder.getFileName().toString());

            int counter = startValue;
            List<Path> filesInFolder = StorageProvider.getInstance().listFiles(folder.toString());
            for (Path file : filesInFolder) {
                log.debug("start renaming file: " + file.getFileName().toString());
                String olfFileName = file.getFileName().toString();
                String extension = olfFileName.substring(olfFileName.lastIndexOf(".") + 1);
                // check if it is the barcode image
                String fileName = null;
                // TODO check, if the counter is set to "0"
                if (olfFileName.contains("barcode")) {
                    //    rename it with '0' as counter
                    fileName = getFilename(0, extension);
                } else {
                    // create new filename
                    fileName = getFilename(counter, extension);
                    counter++;
                }

                // if old and new filename don't match, rename it
                if (!olfFileName.equals(fileName)) {
                    try {
                        tryRenameFile(file, fileName);
                    } catch (IOException e) {
                        log.error("IOException caught while trying to rename the files in " + folder.toString());
                    }
                }
            }
            // move all the files from the temp folder back again
            try {
                moveFilesFromTempBack(folder);
            } catch (IOException e) {
                log.error("IOException caught while trying to get files from the temp folder back.");
            }
        }

        Helper.setMeldung("Renamed images in all folder");
        Helper.addMessageToProcessJournal(process.getId(), LogType.DEBUG, "Renamed images in all folder", "");

        return PluginReturnValue.FINISH;
    }

    private void getFolderList(Process process, List<Path> folders, VariableReplacer replacer, String folder)
            throws IOException, SwapException, DAOException {
        if (StringUtils.isBlank(folder) || folder.equals("*")) {
            getFolderListDefault(process, folders);
        } else {
            getFolderListConfigured(process, folders, replacer);
        }
    }

    private void getFolderListDefault(Process process, List<Path> folders) throws IOException, SwapException, DAOException {
        Path masterFolder = Paths.get(process.getImagesOrigDirectory(false));
        Path derivateFolder = Paths.get(process.getImagesTifDirectory(false));
        //            Path thumbFolder = Paths.get(process.getImagesTifDirectory(true));
        Path altoFolder = Paths.get(process.getOcrAltoDirectory());
        Path pdfFolder = Paths.get(process.getOcrPdfDirectory());
        Path txtFolder = Paths.get(process.getOcrTxtDirectory());
        Path xmlFolder = Paths.get(process.getOcrXmlDirectory());

        if (Files.exists(masterFolder)) {
            log.debug("add masterfolder: " + masterFolder.getFileName().toString());
            folders.add(masterFolder);
        }
        if (Files.exists(derivateFolder) && !masterFolder.getFileName().toString().equals(derivateFolder.getFileName().toString())) {
            log.debug("add derivateFolder: " + derivateFolder.getFileName().toString());
            folders.add(derivateFolder);
        }

        //            if (Files.exists(thumbFolder) && !thumbFolder.getFileName().toString().equals(derivateFolder.getFileName().toString())) {
        //              log.error("add thumbFolder: " + thumbFolder.getFileName().toString());
        //              folders.add(thumbFolder);
        //            }

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

    private void getFolderListConfigured(Process process, List<Path> folders, VariableReplacer replacer) throws IOException, SwapException {
        if (replacer == null) {
            // an error should be triggered here since replacer is needed
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

    private void tryRenameFile(Path file, String fileName) throws IOException {
        Path targetPath = Paths.get(file.getParent().toString(), fileName);
        if (StorageProvider.getInstance().isFileExists(targetPath)) {
            log.debug("targetPath is occupied: " + targetPath.toString());
            log.debug("Moving the file " + fileName + " to temp folder for the moment instead.");
            // move files to a temp folder
            moveFileToTempFolder(file, fileName);
        } else {
            StorageProvider.getInstance().move(file, Paths.get(file.getParent().toString(), fileName));
        }
    }

    private void moveFileToTempFolder(Path filePath, String fileName) throws IOException {
        String tempFolder = "temp";
        Path tempFolderPath = Paths.get(filePath.getParent().toString(), "temp");
        if (!StorageProvider.getInstance().isFileExists(tempFolderPath)) {
            StorageProvider.getInstance().createDirectories(tempFolderPath);
        }
        StorageProvider.getInstance().move(filePath, Paths.get(tempFolderPath.toString(), fileName));
    }

    private void moveFilesFromTempBack(Path folderPath) throws IOException {
        String tempFolder = "temp";
        Path tempFolderPath = Paths.get(folderPath.toString(), "temp");
        if (StorageProvider.getInstance().isFileExists(tempFolderPath)) {
            log.debug("Moving files back from the temp folder: " + tempFolderPath.toString());
            List<Path> files = StorageProvider.getInstance().listFiles(tempFolderPath.toString());
            for (Path file : files) {
                StorageProvider.getInstance().move(file, Paths.get(folderPath.toString(), file.getFileName().toString()));
            }
            StorageProvider.getInstance().deleteDir(tempFolderPath);
        }
    }

    private String getFilename(int counter, String extension) {
        StringBuilder sb = new StringBuilder();
        for (NamePartConfiguration npc : namePartList) {
            if (npc.getFormat() != null) {
                sb.append(npc.getFormat().format(counter));
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

    @Override
    public String cancel() {
        return returnPath;
    }

    @Override
    public boolean execute() {
        if (run().equals(PluginReturnValue.FINISH)) {
            return true;
        } else {
            return false;
        }
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

        log.debug(configHelper.getAdditionalProcessFolderName("greyscale"));

        this.step = step;
        this.returnPath = returnPath;
        SubnodeConfiguration myconfig = ConfigPlugins.getProjectAndStepConfig(title, step);
        initConfig(myconfig);
    }

    private void initConfig(SubnodeConfiguration config) {
        startValue = config.getInt("startValue", 1);
        folderConfigured = config.getString("folder", "*");
        log.debug("folderConfigured = " + folderConfigured);
        namePartList = new ArrayList<>();
        List<HierarchicalConfiguration> fields = config.configurationsAt("namepart");
        for (HierarchicalConfiguration hc : fields) {
            NamePartConfiguration npc = new NamePartConfiguration(hc.getString("."), hc.getString("@type", "static"));
            namePartList.add(npc);
        }
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null;
    }

    @Override
    public int getInterfaceVersion() {
        return 0;
    }

    @Data
    @RequiredArgsConstructor
    public class NamePartConfiguration {
        @NonNull
        private String namePartValue;
        @NonNull
        private String namePartType;

        private NumberFormat format;
    }
}
