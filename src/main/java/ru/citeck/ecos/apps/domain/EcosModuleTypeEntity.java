package ru.citeck.ecos.apps.domain;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Table(name = "ecos_module_type")
public class EcosModuleTypeEntity extends AbstractImmutableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_module_type_id_gen")
    @SequenceGenerator(name = "ecos_module_type_id_gen")
    @Getter @Setter private Long id;

    @Column(name="source")
    @Getter @Setter private String source;

    @ManyToOne
    @JoinColumn(name = "content_id")
    @Getter @Setter private EcosContentEntity content;
}
