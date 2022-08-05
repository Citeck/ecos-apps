package ru.citeck.ecos.apps.domain.artifact.artifact.repo;

import lombok.Getter;
import lombok.Setter;
import ru.citeck.ecos.apps.domain.artifact.artifact.dto.DeployStatus;
import ru.citeck.ecos.webapp.lib.spring.hibernate.entity.AbstractAuditingEntity;
import ru.citeck.ecos.apps.domain.common.repo.utils.EntityUtils;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "ecos_artifact")
public class EcosArtifactEntity extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_module_id_gen")
    @SequenceGenerator(name = "ecos_module_id_gen")
    @Getter @Setter private Long id;

    @Column(name="ext_id")
    @Getter @Setter private String extId;
    @Getter @Setter private String type;

    @Getter @Setter private String name;
    @Getter @Setter private String tags;
    @Getter @Setter private Long typeRevId;

    @Getter @Setter private boolean deleted;

    /**
     * Last revision without patches
     */
    @OneToOne
    @JoinColumn(name = "last_rev_id")
    @Getter @Setter private EcosArtifactRevEntity lastRev;

    /**
     * Patched revision has a higher priority
     */
    @OneToOne
    @JoinColumn(name = "patched_module_rev_id")
    @Getter @Setter private EcosArtifactRevEntity patchedRev;

    @Enumerated(EnumType.ORDINAL)
    @Getter @Setter private DeployStatus deployStatus = DeployStatus.DRAFT;

    @Getter @Setter private String deployMsg;
    @Getter @Setter private String deployErrors;

    @Getter @Setter private Integer deployRetryCounter;

    @Getter @Setter private String ecosApp;

    @Getter @Setter private Boolean system;

    @OneToMany(mappedBy = "source", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @Getter private Set<EcosArtifactDepEntity> dependencies = new HashSet<>();

    public boolean setDependencies(Set<EcosArtifactDepEntity> dependencies) {
        return EntityUtils.changeHibernateSet(this.dependencies, dependencies, EcosArtifactDepEntity::getTarget);
    }
}
