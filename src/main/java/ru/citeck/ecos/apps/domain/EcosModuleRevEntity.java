package ru.citeck.ecos.apps.domain;

import lombok.Getter;
import lombok.Setter;
import ru.citeck.ecos.apps.app.AppStatus;
import ru.citeck.ecos.apps.module.type.DataType;

import javax.persistence.*;

@Entity
@Table(name = "ecos_module_rev")
public class EcosModuleRevEntity extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_module_rev_id_gen")
    @SequenceGenerator(name = "ecos_module_rev_id_gen")
    @Getter @Setter private Long id;

    @Column(name="model_version")
    @Getter @Setter private Integer modelVersion;

    @ManyToOne
    @JoinColumn(name = "module_id")
    @Getter @Setter private EcosModuleEntity module;

    @Column(name="ext_id")
    @Getter @Setter private String extId;

    @Getter @Setter private String name;

    @Enumerated(EnumType.ORDINAL)
    @Column(name="data_type")
    @Getter @Setter private DataType dataType;

    @ManyToOne
    @JoinColumn(name = "content_id")
    @Getter @Setter private EcosContentEntity content;

    @Enumerated(EnumType.ORDINAL)
    @Getter @Setter private AppStatus status = AppStatus.DRAFT;
    @Column(name = "publish_msg")
    @Getter @Setter private String publishMsg;
}
