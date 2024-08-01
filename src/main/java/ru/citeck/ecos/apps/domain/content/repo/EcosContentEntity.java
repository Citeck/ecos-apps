package ru.citeck.ecos.apps.domain.content.repo;

import lombok.Getter;
import lombok.Setter;
import ru.citeck.ecos.apps.domain.common.repo.AbstractImmutableEntity;

import jakarta.persistence.*;

/**
 * EcosContentEntity - immutable store for content
 */
@Entity
@Table(name = "ecos_apps_content")
public class EcosContentEntity extends AbstractImmutableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hibernate_sequence")
    @SequenceGenerator(name = "hibernate_sequence")
    @Getter @Setter private Long id;

    @Getter @Setter private Long size;
    @Getter @Setter private String hash;
    private byte[] data;

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}
