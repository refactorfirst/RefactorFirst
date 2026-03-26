package org.hjug.graphbuilder.metrics.testclasses;

public class TraditionBreakerExample extends BaseService {

    private String newImplementation;

    @Override
    protected void initialize() {
        serviceName = "TraditionBreaker";
        isActive = false;
        newImplementation = "custom";
    }

    @Override
    protected void configure(String config) {
        configuration = config + "_modified";
        newImplementation = config;
    }

    @Override
    protected void start() {
        isActive = true;
        newImplementation = "started";
    }

    @Override
    protected void stop() {
        isActive = false;
        newImplementation = "stopped";
    }

    @Override
    protected void restart() {
        newImplementation = "restarting";
        stop();
        start();
    }

    @Override
    protected String getStatus() {
        return newImplementation + ": " + (isActive ? "Running" : "Stopped");
    }

    public String getNewImplementation() {
        return newImplementation;
    }
}
