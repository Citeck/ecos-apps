package ru.citeck.ecos.apps.domain;

import lombok.Getter;
import lombok.Setter;
import ru.citeck.ecos.apps.app.PublishStatus;

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

    @Getter @Setter private boolean deleted;

    @OneToMany(mappedBy = "module", fetch = FetchType.LAZY)
    @Getter @Setter private Set<EcosModuleRevEntity> revisions;

    @OneToOne
    @JoinColumn(name = "last_rev_id")
    @Getter @Setter private EcosModuleRevEntity lastRev;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "publish_status")
    @Getter @Setter private PublishStatus publishStatus = PublishStatus.DRAFT;
    @Column(name = "publish_msg")
    @Getter @Setter private String publishMsg;

    @OneToMany(mappedBy = "source", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @Getter @Setter private Set<EcosModuleDepEntity> dependencies = new HashSet<>();
}
