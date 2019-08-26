package ru.citeck.ecos.apps.domain;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.sql.Blob;

@Entity
@Table(name = "ecos_app_module_revision")
public class EcosAppModuleRevEntity extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_app_module_revision_id_gen")
    @SequenceGenerator(name = "ecos_app_module_revision_id_gen")
    @Getter @Setter private Long id;

    @Column(name="model_version")
    @Getter @Setter private Integer modelVersion;
    @Column(name="module_id")
    @Getter @Setter private Long moduleId;
    @Column(name="ext_id")
    @Getter @Setter private String extId;

    @Getter @Setter private String key;
    @Getter @Setter private String name;
    @Getter @Setter private String mimetype;
    @Getter @Setter private Blob data;
    @Getter @Setter private String checksum;
    @Getter @Setter private Boolean fixed;
}
