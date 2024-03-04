package de.intranda.goobi.plugins;

public class PluginException extends Exception {
    public PluginException(String msg) {
        super(msg);
    }

    public PluginException(String msg, Throwable t) {
        super(msg, t);
    }
}
