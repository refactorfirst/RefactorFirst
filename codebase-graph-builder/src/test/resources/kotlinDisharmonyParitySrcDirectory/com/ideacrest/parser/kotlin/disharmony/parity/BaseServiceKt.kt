package com.ideacrest.parser.kotlin.disharmony.parity

/**
 * Kotlin disharmony parity fixture — Kotlin twin of the Java `BaseService`.
 *
 * Provides 5 protected fields and 10 protected methods (NProtM=15).
 * Matches the parent-class metrics needed for the Tradition Breaker
 * "parent non-dumb" condition (Fig. 7.9):
 *   NOM=10, WMC=23, AMW=2.3
 *
 * Plain-text fixture, NOT compiled by the Maven build.
 */
open class BaseServiceKt {

    protected var serviceName: String = "BaseService"
    protected var serviceId: Int = 0
    protected var isActive: Boolean = false
    protected var configuration: String = ""
    protected var timeout: Int = 0

    // CC=2
    protected open fun initialize() {
        if (serviceName.isEmpty()) {
            serviceName = "BaseService"
        } else {
            serviceName = "BaseService:$serviceName"
        }
        isActive = true
    }

    // CC=2
    protected open fun configure(config: String?) {
        if (config != null) {
            configuration = config
        } else {
            configuration = ""
        }
    }

    // CC=2
    protected open fun start() {
        if (!isActive) {
            isActive = true
        }
    }

    // CC=2
    protected open fun stop() {
        if (isActive) {
            isActive = false
        }
    }

    // CC=3
    protected open fun restart() {
        if (isActive) {
            stop()
        }
        if (!isActive) {
            start()
        }
    }

    // CC=2
    protected open fun getStatus(): String {
        return if (isActive) "Running" else "Stopped"
    }

    // CC=2
    protected open fun setTimeout(timeout: Int) {
        if (timeout >= 0) {
            this.timeout = timeout
        } else {
            this.timeout = 0
        }
    }

    // CC=2
    protected open fun getTimeout(): Int {
        if (timeout > 0) {
            return timeout
        }
        return 0
    }

    // CC=3
    protected open fun validateConfig(config: String?): String {
        if (config == null) {
            return "null"
        } else if (config.isEmpty()) {
            return "empty"
        } else {
            return "valid"
        }
    }

    // CC=3
    protected open fun applyTimeout(value: Int) {
        if (value > 0) {
            this.timeout = value
        } else if (value == 0) {
            this.timeout = 5000
        } else {
            this.timeout = 0
        }
    }
}
