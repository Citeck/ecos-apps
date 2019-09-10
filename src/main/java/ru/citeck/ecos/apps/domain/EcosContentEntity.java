package ru.citeck.ecos.apps.domain;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Table(name = "ecos_content")
public class EcosContentEntity extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_content_id_gen")
    @SequenceGenerator(name = "ecos_content_id_gen")
    @Getter
    @Setter
    private Long id;

    @Getter @Setter private String source;
    @Getter @Setter private Long size;
    @Getter @Setter private String hash;
    @Getter @Setter private byte[] data;
}
