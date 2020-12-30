package ru.citeck.ecos.apps.domain.artifact.dto;

/**
 * Warning! Ordinal used in DB. Do not change values order!
 */
public enum DeployStatus {
    // an entity created but not deployed
    DRAFT,
    // an entity deploying, but response doesn't received
    DEPLOYING,
    // an entity deployed and response is OK
    DEPLOYED,
    // an entity deployed and response is ERROR
    DEPLOY_FAILED,
    // an entity waits until dependent entities will be deployed
    DEPS_WAITING
}
