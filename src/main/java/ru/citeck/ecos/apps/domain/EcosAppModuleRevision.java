package ru.citeck.ecos.apps.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.sql.Blob;

@Entity
@Table(name = "ecos_app_module_revision")
public class EcosAppModuleRevision extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_app_module_revision_id_gen")
    @SequenceGenerator(name = "ecos_app_module_revision_id_gen")
    @Getter @Setter private Long id;

    @Column(name="model_version")
    @Getter @Setter private Integer modelVersion;

    @JsonIgnore
    @Column(name="module_id")
    @Getter @Setter private EcosApplication module;

    @Getter @Setter private String uuid;
    @Getter @Setter private String key;
    @Getter @Setter private String name;
    @Getter @Setter private Blob data;
    @Getter @Setter private String checksum;
    @Getter @Setter private Boolean deployed;
}
