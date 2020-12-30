package ru.citeck.ecos.apps.domain.application.repo;

import lombok.Getter;
import lombok.Setter;
import ru.citeck.ecos.apps.app.audit.repo.AbstractAuditingEntity;
import ru.citeck.ecos.apps.domain.artifact.dto.DeployStatus;
import ru.citeck.ecos.apps.app.EcosAppType;

import javax.persistence.*;

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
    @Enumerated(value = EnumType.STRING)
    @Getter @Setter private EcosAppType type;

    @Column(name = "is_system")
    @Getter @Setter private Boolean isSystem;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "publish_status")
    @Getter @Setter private DeployStatus publishStatus = DeployStatus.DRAFT;
}
