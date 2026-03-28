package org.hjug.graphbuilder.metrics.testclasses;

/**
 * Base class providing protected members for inheritance tests.
 * NProtM = 15 (5 protected fields + 10 protected methods).
 *
 * Complexity targets for Tradition Breaker "parent non-dumb" condition
 * (Fig. 7.9: AMW > AVERAGE(2.0) AND NOM > NOM_HIGH/2(6) AND WMC >= VERY_HIGH/2(23)):
 *   NOM = 10, WMC = 23, AMW = 2.3
 *
 * CC per method:
 *   initialize=2, configure=2, start=2, stop=2, restart=3,
 *   getStatus=2, setTimeout=2, getTimeout=2, validateConfig=3, applyTimeout=3
 *   Total WMC = 23
 */
public class BaseService {

    protected String serviceName;
    protected int serviceId;
    protected boolean isActive;
    protected String configuration;
    protected int timeout;

    // CC=2
    protected void initialize() {
        if (serviceName == null) {
            serviceName = "BaseService";
        } else {
            serviceName = "BaseService:" + serviceName;
        }
        isActive = true;
    }

    // CC=2
    protected void configure(String config) {
        if (config != null) {
            configuration = config;
        } else {
            configuration = "";
        }
    }

    // CC=2
    protected void start() {
        if (!isActive) {
            isActive = true;
        }
    }

    // CC=2
    protected void stop() {
        if (isActive) {
            isActive = false;
        }
    }

    // CC=3
    protected void restart() {
        if (isActive) {
            stop();
        }
        if (!isActive) {
            start();
        }
    }

    // CC=2
    protected String getStatus() {
        return isActive ? "Running" : "Stopped";
    }

    // CC=2
    protected void setTimeout(int timeout) {
        if (timeout >= 0) {
            this.timeout = timeout;
        } else {
            this.timeout = 0;
        }
    }

    // CC=2
    protected int getTimeout() {
        if (timeout > 0) {
            return timeout;
        }
        return 0;
    }

    // CC=3
    protected String validateConfig(String config) {
        if (config == null) {
            return "null";
        } else if (config.isEmpty()) {
            return "empty";
        } else {
            return "valid";
        }
    }

    // CC=3
    protected void applyTimeout(int value) {
        if (value > 0) {
            this.timeout = value;
        } else if (value == 0) {
            this.timeout = 5000;
        } else {
            this.timeout = 0;
        }
    }
}
