package ru.citeck.ecos.apps.domain.module.repo;

import lombok.Getter;
import lombok.Setter;
import ru.citeck.ecos.apps.domain.common.repo.AbstractImmutableEntity;
import ru.citeck.ecos.apps.domain.content.repo.EcosContentEntity;

import javax.persistence.*;

@Entity
@Table(name = "ecos_module_types")
public class EcosModuleTypesEntity extends AbstractImmutableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_module_type_id_gen")
    @SequenceGenerator(name = "ecos_module_type_id_gen")
    @Getter @Setter private Long id;

    @Getter @Setter private String source;

    @ManyToOne
    @JoinColumn(name = "content_id")
    @Getter @Setter private EcosContentEntity content;
}
