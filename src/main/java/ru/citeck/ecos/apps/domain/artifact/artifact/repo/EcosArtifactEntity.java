package ru.citeck.ecos.apps.domain.artifact.artifact.repo;

import lombok.Getter;
import lombok.Setter;
import ru.citeck.ecos.apps.domain.artifact.artifact.dto.DeployStatus;
import ru.citeck.ecos.apps.app.audit.repo.AbstractAuditingEntity;
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

    @Getter @Setter private boolean deleted;

    @OneToOne
    @JoinColumn(name = "last_rev_id")
    @Getter @Setter private EcosArtifactRevEntity lastRev;

    @OneToOne
    @JoinColumn(name = "patched_module_rev_id")
    @Getter @Setter private EcosArtifactRevEntity patchedRev;

    @Enumerated(EnumType.ORDINAL)
    @Getter @Setter private DeployStatus deployStatus = DeployStatus.DRAFT;

    @Getter @Setter private String deployMsg;
    @Getter @Setter private String deployErrors;

    @Getter @Setter private Integer deployRetryCounter;

    @Getter @Setter private String ecosApp;

    @OneToMany(mappedBy = "source", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @Getter private Set<EcosArtifactDepEntity> dependencies = new HashSet<>();

    public boolean setDependencies(Set<EcosArtifactDepEntity> dependencies) {
        return EntityUtils.changeHibernateSet(this.dependencies, dependencies, EcosArtifactDepEntity::getTarget);
    }
}
