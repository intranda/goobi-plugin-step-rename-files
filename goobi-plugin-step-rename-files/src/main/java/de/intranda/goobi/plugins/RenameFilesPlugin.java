package de.intranda.goobi.plugins;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang.StringUtils;
import org.goobi.beans.LogEntry;
import org.goobi.beans.Process;
import org.goobi.beans.Step;
import org.goobi.production.enums.LogType;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.persistence.managers.ProcessManager;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.Fileformat;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.WriteException;

@Log4j2
@PluginImplementation
public class RenameFilesPlugin implements IStepPluginVersion2 {

    @Getter
    private Step step;
    private String returnPath;
    private List<NamePartConfiguration> namePartList;
    private int startValue = 1;

    @Getter
    private String title = "intranda_step_rename-files";
    @Getter
    private PluginType type = PluginType.Step;

    @Getter
    private PluginGuiType pluginGuiType = PluginGuiType.NONE;

    @Override
    public PluginReturnValue run() {
        // get folder list
        List<Path> folders = new ArrayList<>();
        Process process = step.getProzess();
        try {
            Path masterFolder = Paths.get(process.getImagesOrigDirectory(false));
            Path derivateFolder = Paths.get(process.getImagesTifDirectory(false));
            Path thumbFolder = Paths.get(process.getImagesTifDirectory(true));
            Path altoFolder = Paths.get(process.getOcrAltoDirectory());
            Path pdfFolder = Paths.get(process.getOcrPdfDirectory());
            Path txtFolder = Paths.get(process.getOcrTxtDirectory());
            Path xmlFolder = Paths.get(process.getOcrXmlDirectory());

            if (Files.exists(masterFolder)) {
                folders.add(masterFolder);
            }
            if (Files.exists(derivateFolder) && !masterFolder.getFileName().toString().equals(derivateFolder.getFileName().toString())) {
                folders.add(derivateFolder);
            }

            if (Files.exists(thumbFolder) && !thumbFolder.getFileName().toString().equals(derivateFolder.getFileName().toString())) {
                folders.add(thumbFolder);
            }
            if (Files.exists(altoFolder)) {
                folders.add(altoFolder);
            }
            if (Files.exists(pdfFolder)) {
                folders.add(pdfFolder);
            }
            if (Files.exists(txtFolder)) {
                folders.add(txtFolder);
            }
            if (Files.exists(xmlFolder)) {
                folders.add(xmlFolder);
            }

        } catch (IOException | InterruptedException | SwapException | DAOException e) {
            log.error(e);
        }

        try {
            Fileformat fileformat = process.readMetadataFile();
            VariableReplacer replacer = new VariableReplacer(fileformat != null ? fileformat.getDigitalDocument() : null,
                    process.getRegelsatz().getPreferences(), process, step);

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
                int counter = startValue;
                List<Path> filesInFolder = StorageProvider.getInstance().listFiles(folder.toString());
                for (Path file : filesInFolder) {
                    String olfFileName = file.getFileName().toString();
                    String extension = olfFileName.substring(olfFileName.lastIndexOf(".") + 1);
                    // check if it is the barcode image
                    String filename = null;
                    // TODO check, if the counter is set to "0"
                    if (olfFileName.contains("barcode") ) {
                        //    rename it with '0' as counter
                        filename = getFilename(0, extension);
                    } else {
                        // create new filename
                        filename = getFilename(counter, extension);
                        counter++;
                    }
                    // if old and new filename don't match, rename it
                    if (!olfFileName.equals(filename)) {
                        StorageProvider.getInstance().move(file, Paths.get(file.getParent().toString(), filename));
                    }
                }
            }

        } catch (ReadException | PreferencesException | WriteException | IOException | InterruptedException | SwapException | DAOException e) {
            log.error(e);
            return PluginReturnValue.ERROR;
        }

        Helper.setMeldung("Renamed images in all folder");
        LogEntry entry = LogEntry.build(process.getId())
                .withCreationDate(new Date())
                .withType(LogType.DEBUG)
                .withUsername("")
                .withContent("Renamed images in all folder");
        ProcessManager.saveLogEntry(entry);

        return PluginReturnValue.FINISH;
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
        this.step = step;
        this.returnPath = returnPath;

        String projectName = step.getProzess().getProjekt().getTitel();

        XMLConfiguration xmlConfig = ConfigPlugins.getPluginConfig(getTitle());
        xmlConfig.setExpressionEngine(new XPathExpressionEngine());
        xmlConfig.setReloadingStrategy(new FileChangedReloadingStrategy());

        SubnodeConfiguration myconfig = null;

        // order of configuration is:
        // 1.) project name and step name matches
        // 2.) step name matches and project is *
        // 3.) project name matches and step name is *
        // 4.) project name and step name are *
        try {
            myconfig = xmlConfig.configurationAt("//config[./project = '" + projectName + "'][./step = '" + step.getTitel() + "']");
        } catch (IllegalArgumentException e) {
            try {
                myconfig = xmlConfig.configurationAt("//config[./project = '*'][./step = '" + step.getTitel() + "']");
            } catch (IllegalArgumentException e1) {
                try {
                    myconfig = xmlConfig.configurationAt("//config[./project = '" + projectName + "'][./step = '*']");
                } catch (IllegalArgumentException e2) {
                    myconfig = xmlConfig.configurationAt("//config[./project = '*'][./step = '*']");
                }
            }
        }

        initConfig(myconfig);
    }

    private void initConfig(SubnodeConfiguration config) {
    	startValue = config.getInt("startValue", 1);
        namePartList = new ArrayList<>();
        @SuppressWarnings("unchecked")
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
