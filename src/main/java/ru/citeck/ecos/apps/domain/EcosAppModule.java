package ru.citeck.ecos.apps.domain;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Table(name = "ecos_app_module")
public class EcosAppModule extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_app_module_id_gen")
    @SequenceGenerator(name = "ecos_app_module_id_gen")
    @Getter @Setter private Long id;

    @Getter @Setter private String uuid;
    @Getter @Setter private String type;
}
