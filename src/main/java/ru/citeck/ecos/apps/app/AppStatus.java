package ru.citeck.ecos.apps.app;

import lombok.Getter;

/**
 * Warning! Ordinal used in DB. Do not change values order!
 */
public enum AppStatus {

    DRAFT(true),
    PUBLISHING(true),
    PUBLISHED(false),
    PUBLISH_FAILED(true);

    @Getter
    private boolean publishAllowed;

    AppStatus(boolean publishAllowed) {
        this.publishAllowed = publishAllowed;
    }
}
