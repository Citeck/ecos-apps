package ru.citeck.ecos.apps.domain;

import lombok.Getter;
import lombok.Setter;
import ru.citeck.ecos.apps.app.module.EcosModuleRev;

import javax.persistence.*;
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

    @OneToOne
    @JoinColumn(name = "upload_rev_id")
    @Getter @Setter private EcosModuleRevEntity uploadRev;

    @OneToMany(mappedBy = "module", fetch = FetchType.LAZY)
    @Getter @Setter private Set<EcosModuleRevEntity> revisions;
}
