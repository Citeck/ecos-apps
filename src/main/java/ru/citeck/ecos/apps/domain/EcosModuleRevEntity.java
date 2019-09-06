package ru.citeck.ecos.apps.domain;

import lombok.Getter;
import lombok.Setter;
import ru.citeck.ecos.apps.app.AppStatus;

import javax.persistence.*;
import java.sql.Blob;

@Entity
@Table(name = "ecos_app_module_rev")
public class EcosModuleRevEntity extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_app_module_rev_id_gen")
    @SequenceGenerator(name = "ecos_app_module_rev_id_gen")
    @Getter @Setter private Long id;

    @Column(name="model_version")
    @Getter @Setter private Integer modelVersion;

    @ManyToOne
    @JoinColumn(name = "module_id", nullable = false)
    @Getter @Setter private EcosModuleEntity module;

    @Column(name="ext_id")
    @Getter @Setter private String extId;

    @Getter @Setter private String name;
    @Getter @Setter private String mimetype;

    @Getter @Setter private Blob data;
    @Getter @Setter private Long size;
    @Getter @Setter private String hash;

    @Enumerated(EnumType.ORDINAL)
    @Getter @Setter private AppStatus status = AppStatus.DRAFT;
}
