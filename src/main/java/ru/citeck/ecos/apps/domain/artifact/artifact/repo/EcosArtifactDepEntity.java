package ru.citeck.ecos.apps.domain.artifact.artifact.repo;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "ecos_artifact_dep")
public class EcosArtifactDepEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hibernate_sequence")
    @SequenceGenerator(name = "hibernate_sequence")
    @Getter @Setter private Long id;

    @ManyToOne
    @JoinColumn(name = "source_id")
    @Getter @Setter private EcosArtifactEntity source;

    @ManyToOne
    @JoinColumn(name = "target_id")
    @Getter @Setter private EcosArtifactEntity target;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EcosArtifactDepEntity that = (EcosArtifactDepEntity) o;
        return Objects.equals(source, that.source)
            && Objects.equals(target, that.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
