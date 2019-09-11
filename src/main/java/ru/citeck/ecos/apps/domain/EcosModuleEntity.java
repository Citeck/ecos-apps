package ru.citeck.ecos.apps.domain;

import lombok.Getter;
import lombok.Setter;

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

    @OneToMany(mappedBy = "module", fetch = FetchType.LAZY)
    @Getter @Setter private Set<EcosModuleRevEntity> revisions;
}