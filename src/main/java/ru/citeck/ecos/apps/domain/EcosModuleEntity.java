package ru.citeck.ecos.apps.domain;

import lombok.Getter;
import lombok.Setter;
import ru.citeck.ecos.apps.app.DeployStatus;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "ecos_module")
public class EcosModuleEntity extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_module_id_gen")
    @SequenceGenerator(name = "ecos_module_id_gen")
    @Getter @Setter private Long id;

    @Column(name="ext_id")
    @Getter @Setter private String extId;
    @Getter @Setter private String type;
    @Getter @Setter private String key;

    @Getter @Setter private boolean deleted;

    @OneToOne
    @JoinColumn(name = "last_rev_id")
    @Getter @Setter private EcosModuleRevEntity lastRev;

    @OneToOne
    @JoinColumn(name = "user_module_rev_id")
    @Getter @Setter private EcosModuleRevEntity userRev;

    @OneToOne
    @JoinColumn(name = "patched_module_rev_id")
    @Getter @Setter private EcosModuleRevEntity patchedRev;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "deploy_status")
    @Getter @Setter private DeployStatus deployStatus = DeployStatus.DRAFT;
    @Column(name = "deploy_msg")
    @Getter @Setter private String deployMsg;

    @OneToMany(mappedBy = "source", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @Getter private Set<EcosModuleDepEntity> dependencies = new HashSet<>();

    public void setDependencies(Set<EcosModuleDepEntity> dependencies) {
        EntityUtils.changeHibernateSet(this.dependencies, dependencies, EcosModuleDepEntity::getTarget);
    }
}
