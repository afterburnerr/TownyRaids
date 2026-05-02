package gg.afterburner.townyRaids.config;

public class ConfigLoadException extends Exception {
    public ConfigLoadException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConfigLoadException(String message) {
        super(message);
    }
}
