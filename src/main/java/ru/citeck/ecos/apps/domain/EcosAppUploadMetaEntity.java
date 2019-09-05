package ru.citeck.ecos.apps.domain;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;

public class EcosAppUploadMetaEntity extends AbstractAuditingEntity {

    @Getter @Setter private Long id;
    @Column(name="app_id")
    @Getter @Setter private long appId;
    @Getter @Setter private String md5;
    @Getter @Setter private long size;
    @Getter @Setter private String location;
}
