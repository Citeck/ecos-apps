package ru.citeck.ecos.apps.domain.content.repo;

import lombok.Getter;
import lombok.Setter;
import ru.citeck.ecos.apps.domain.common.repo.AbstractImmutableEntity;

import javax.persistence.*;

/**
 * EcosContentEntity - immutable store for content
 */
@Entity
@Table(name = "ecos_content")
public class EcosContentEntity extends AbstractImmutableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_content_id_gen")
    @SequenceGenerator(name = "ecos_content_id_gen")
    @Getter @Setter private Long id;

    @Getter @Setter private Long size;
    @Getter @Setter private String hash;
    @Getter @Setter private byte[] data;
}
