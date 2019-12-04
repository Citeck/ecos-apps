package ru.citeck.ecos.apps.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
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

    @JsonIgnore
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "ecos_module_deps",
        joinColumns = {@JoinColumn(name = "module_id", referencedColumnName = "id")},
        inverseJoinColumns = {@JoinColumn(name = "dep_id", referencedColumnName = "id")})
    @BatchSize(size = 20)
    @Getter @Setter private Set<EcosModuleEntity> dependencies = new HashSet<>();
}
