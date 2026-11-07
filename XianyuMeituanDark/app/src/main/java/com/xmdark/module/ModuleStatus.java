package com.xmdark.module;

/** Set by the Xposed hook when the module is active in its own process. */
public final class ModuleStatus {
    public static volatile boolean active = false;

    private ModuleStatus() {
    }
}