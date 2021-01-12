package ru.citeck.ecos.apps.domain.artifact.repo;

import lombok.Getter;
import lombok.Setter;
import ru.citeck.ecos.apps.domain.artifact.dto.ArtifactSourceType;
import ru.citeck.ecos.apps.domain.common.repo.AbstractImmutableEntity;
import ru.citeck.ecos.apps.domain.content.repo.EcosContentEntity;

import javax.persistence.*;

@Entity
@Table(name = "ecos_module_rev")
public class EcosArtifactRevEntity extends AbstractImmutableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_module_rev_id_gen")
    @SequenceGenerator(name = "ecos_module_rev_id_gen")
    @Getter @Setter private Long id;

    @Getter @Setter private Integer modelVersion;

    @ManyToOne
    @JoinColumn(name = "module_id")
    @Getter @Setter private EcosArtifactEntity module;

    @Enumerated(EnumType.ORDINAL)
    @Getter @Setter private ArtifactSourceType sourceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prev_rev_id")
    @Getter @Setter private EcosArtifactRevEntity prevRev;

    @Getter @Setter private String extId;

    @Column(name="source")
    @Getter @Setter private String sourceId;
    //todo
    @Getter @Setter private ArtifactSourceType sourceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id")
    @Getter @Setter private EcosContentEntity content;
}
