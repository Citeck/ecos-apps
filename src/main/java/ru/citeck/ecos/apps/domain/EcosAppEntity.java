package ru.citeck.ecos.apps.domain;

import lombok.Getter;
import lombok.Setter;
import ru.citeck.ecos.apps.app.PublishStatus;

import javax.persistence.*;
import java.util.Set;

@Entity
@Table(name = "ecos_app")
public class EcosAppEntity extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_app_id_gen")
    @SequenceGenerator(name = "ecos_app_id_gen")
    @Getter @Setter private Long id;

    @Column(name = "ext_id")
    @Getter @Setter private String extId;
    @Getter @Setter private String version;

    @Column(name = "is_system")
    @Getter @Setter private Boolean isSystem;

    @OneToMany(mappedBy = "application", fetch = FetchType.LAZY)
    @Getter @Setter private Set<EcosAppRevEntity> revisions;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "publish_status")
    @Getter @Setter private PublishStatus publishStatus = PublishStatus.DRAFT;
}
