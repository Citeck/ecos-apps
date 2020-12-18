package ru.citeck.ecos.apps.domain.modulepatch.repo;

import lombok.Getter;
import lombok.Setter;
import ru.citeck.ecos.apps.app.audit.repo.AbstractAuditingEntity;

import javax.persistence.*;

@Entity
@Table(name = "ecos_module_patch")
public class EcosModulePatchEntity extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_module_patch_id_gen")
    @SequenceGenerator(name = "ecos_module_patch_id_gen")
    @Getter @Setter private Long id;

    @Column(name="ext_id")
    @Getter @Setter private String extId;
    @Getter @Setter private String name;
    @Getter @Setter private String target;
    @Column(name="patch_order")
    @Getter @Setter private float order;

    @Getter @Setter private String type;
    @Getter @Setter private String config;
}
