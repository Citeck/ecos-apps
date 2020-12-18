package ru.citeck.ecos.apps.domain.module.dto;

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
