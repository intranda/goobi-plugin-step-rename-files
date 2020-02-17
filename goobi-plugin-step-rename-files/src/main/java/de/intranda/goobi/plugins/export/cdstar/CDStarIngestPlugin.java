package de.intranda.goobi.plugins.export.cdstar;

import java.util.HashMap;

import javax.jms.JMSException;

import org.apache.commons.configuration.XMLConfiguration;
import org.goobi.api.mq.TaskTicket;
import org.goobi.api.mq.TicketGenerator;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import de.sub.goobi.config.ConfigPlugins;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
@Log4j
public class CDStarIngestPlugin implements IStepPluginVersion2 {

    @Getter
    private String title = "intranda_step_cdstarIngest";

    @Getter
    private PluginType type = PluginType.Step;

    @Getter
    @Setter
    private Step step;

    @Override
    public String cancel() {
        return null;
    }

    @Override
    public boolean execute() {
        run();
        return false;
    }

    @Override
    public String finish() {
        return null;
    }

    @Override
    public String getPagePath() {
        return null;
    }

    @Override
    public PluginGuiType getPluginGuiType() {
        return PluginGuiType.NONE;
    }

    @Override
    public void initialize(Step step, String returnPath) {
        this.step = step;

    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null;
    }

    @Override
    public int getInterfaceVersion() {
        return 0;
    }

    @Override
    public PluginReturnValue run() {

        XMLConfiguration xmlConfig = ConfigPlugins.getPluginConfig(title);
        String cdstarUrl = xmlConfig.getString("url");
        String vault = xmlConfig.getString("vault");
        String user = xmlConfig.getString("user");
        String password = xmlConfig.getString("password");

        TaskTicket ticket = TicketGenerator.generateSimpleTicket("CDStarUpload");

        ticket.setProcessId(step.getProzess().getId());
        ticket.setProcessName(step.getProzess().getTitel());

        ticket.setStepId(step.getId());
        ticket.setStepName(step.getTitel());

        ticket.getProperties().put("userName", user);
        ticket.getProperties().put("password", password);

        ticket.getProperties().put("url", cdstarUrl);
        ticket.getProperties().put("vault", vault);

        ticket.getProperties().put("closeStep", "true");

        try {
            TicketGenerator.submitTicket(ticket, false);
        } catch (JMSException e) {
            log.error(e);
            return PluginReturnValue.ERROR;
        }
        return PluginReturnValue.WAIT;
    }

}
