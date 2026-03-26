package org.hjug.graphbuilder.metrics.testclasses;

public class BaseService {

    protected String serviceName;
    protected int serviceId;
    protected boolean isActive;
    protected String configuration;
    protected int timeout;

    protected void initialize() {
        serviceName = "BaseService";
        isActive = true;
    }

    protected void configure(String config) {
        configuration = config;
    }

    protected void start() {
        isActive = true;
    }

    protected void stop() {
        isActive = false;
    }

    protected void restart() {
        stop();
        start();
    }

    protected String getStatus() {
        return isActive ? "Running" : "Stopped";
    }

    protected void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    protected int getTimeout() {
        return timeout;
    }
}
