package ru.citeck.ecos.apps.app.module;

/**
 * Warning! Ordinal used in DB. Do not change values order!
 */
public enum ModuleRevType {
    /**
     * Base type for deployed modules.
     */
    BASE,
    /**
     * Module revision created by user.
     */
    USER,
    /**
     * Module revision created after patches was applied to BASE revision.
     */
    PATCHED
}
