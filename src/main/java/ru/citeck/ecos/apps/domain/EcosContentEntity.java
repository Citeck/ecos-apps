package ru.citeck.ecos.apps.domain;

import lombok.Getter;
import lombok.Setter;

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
