package ru.citeck.ecos.apps.domain.artifact.artifact.dto;

/**
 * Warning! Ordinal used in DB. Do not change values order!
 */
public enum DeployStatus {
    // an entity created but not deployed
    DRAFT, //0
    // an entity deploying, but response doesn't received
    DEPLOYING, //1
    // an entity deployed and response is OK
    DEPLOYED, //2
    // an entity deployed and response is ERROR
    DEPLOY_FAILED, //3
    // an entity waits until dependent entities will be deployed
    DEPS_WAITING, //4
    // an entity waits until content will be uploaded
    CONTENT_WAITING //5
}
