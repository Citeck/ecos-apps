package ru.citeck.ecos.apps.domain.artifact.artifact.repo;

import lombok.Getter;
import lombok.Setter;
import ru.citeck.ecos.apps.domain.artifact.artifact.dto.ArtifactRevSourceType;
import ru.citeck.ecos.apps.domain.common.repo.AbstractImmutableEntity;
import ru.citeck.ecos.apps.domain.content.repo.EcosContentEntity;

import jakarta.persistence.*;

@Entity
@Table(name = "ecos_artifact_rev")
public class EcosArtifactRevEntity extends AbstractImmutableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hibernate_sequence")
    @SequenceGenerator(name = "hibernate_sequence")
    @Getter @Setter private Long id;

    @ManyToOne
    @JoinColumn(name = "artifact_id")
    @Getter @Setter private EcosArtifactEntity artifact;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prev_rev_id")
    @Setter private EcosArtifactRevEntity prevRev;

    @Getter @Setter private String extId;

    @Column(name="source")
    private String sourceId;
    @Enumerated(EnumType.ORDINAL)
    private ArtifactRevSourceType sourceType;

    @Getter @Setter private Long typeRevId;
    @Getter @Setter private String modelVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id")
    @Getter @Setter private EcosContentEntity content;

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public ArtifactRevSourceType getSourceType() {
        return sourceType;
    }

    public EcosArtifactRevEntity getPrevRev() {
        return prevRev;
    }


    public void setSourceType(ArtifactRevSourceType sourceType) {
        this.sourceType = sourceType;
    }
}
