package ru.citeck.ecos.apps.app;

/**
 * Warning! Ordinal used in DB. Do not change values order!
 */
public enum PublishStatus {
    // an entity created but not published
    DRAFT,
    // an entity published, but response doesn't received
    PUBLISHING,
    // an entity published and response is OK
    PUBLISHED,
    // an entity published and response is ERROR
    PUBLISH_FAILED,
    // an entity waits until dependent entities will be published
    DEPS_WAITING
}
