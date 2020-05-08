package de.intranda.goobi.plugins;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@Log4j2
@PluginImplementation
public class DeleteBarcodeImagePlugin implements IStepPluginVersion2 {

    @Getter
    private Step step;

    private String returnPath;

    @Getter
    private String title = "intranda_step_delete-barcode-image";

    @Getter
    private PluginType type = PluginType.Step;

    @Getter
    private PluginGuiType pluginGuiType = PluginGuiType.NONE;



    @Override
    public String cancel() {
        return returnPath;
    }

    // TODO get this from configuration?
    private String namepartSplitter = "_";

    @Override
    public boolean execute() {

        try {
            Path masterFolderPath = Paths.get(step.getProzess().getImagesOrigDirectory(false));

            Path mediaFolderPath = Paths.get(step.getProzess().getImagesTifDirectory(false));

            List<Path> imagesInAllFolder = new ArrayList<>();

            if (Files.exists(masterFolderPath)) {
                imagesInAllFolder.addAll(StorageProvider.getInstance().listFiles(masterFolderPath.toString()));
            }

            if (Files.exists(mediaFolderPath)) {
                imagesInAllFolder.addAll(StorageProvider.getInstance().listFiles(mediaFolderPath.toString()));
            }

            for (Path imageName : imagesInAllFolder) {
                String filenameWithoutExtension = imageName.getFileName().toString();
                filenameWithoutExtension = filenameWithoutExtension.substring(0, filenameWithoutExtension.lastIndexOf("."));
                String[] nameParts = filenameWithoutExtension.split(namepartSplitter);
                // just check the last token
                String part = nameParts[nameParts.length -1];
                // if all parts should be checked, uncomment this for loop
//                for (String part : nameParts) {
                    if (StringUtils.isNumeric(part) && part.length() > 1 ) {
                        // check if it is 0, 00, 000, 0000, ....
                        if (Integer.parseInt(part) == 0) {
                            // delete image
                            StorageProvider.getInstance().deleteFile(imageName);
                        }
                    }
//                }
            }

        } catch (IOException | InterruptedException | SwapException | DAOException e) {
            log.error(e);
        }

        return true;
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

    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null;
    }

    @Override
    public int getInterfaceVersion() {

        return 1;
    }

    @Override
    public PluginReturnValue run() {
        if (execute()) {
            return PluginReturnValue.FINISH;
        }
        return PluginReturnValue.ERROR;
    }

}
