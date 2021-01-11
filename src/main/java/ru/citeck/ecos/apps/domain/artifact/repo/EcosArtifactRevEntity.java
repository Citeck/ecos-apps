package ru.citeck.ecos.apps.domain.artifact.repo;

import lombok.Getter;
import lombok.Setter;
import ru.citeck.ecos.apps.domain.artifact.dto.ArtifactSourceType;
import ru.citeck.ecos.apps.domain.common.repo.AbstractImmutableEntity;
import ru.citeck.ecos.apps.domain.artifact.dto.ArtifactRevType;
import ru.citeck.ecos.apps.domain.content.repo.EcosContentEntity;

import javax.persistence.*;

@Entity
@Table(name = "ecos_module_rev")
public class EcosArtifactRevEntity extends AbstractImmutableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_module_rev_id_gen")
    @SequenceGenerator(name = "ecos_module_rev_id_gen")
    @Getter @Setter private Long id;

    @Column(name="model_version")
    @Getter @Setter private Integer modelVersion;

    @ManyToOne
    @JoinColumn(name = "module_id")
    @Getter @Setter private EcosArtifactEntity module;

    @Deprecated
    @Column(name="is_user_rev")
    @Getter @Setter private Boolean isUserRev;

    @Enumerated(EnumType.ORDINAL)
    @Column(name="rev_type")
    @Getter @Setter private ArtifactRevType revType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prev_rev_id")
    @Getter @Setter private EcosArtifactRevEntity prevRev;

    @Column(name="ext_id")
    @Getter @Setter private String extId;

    @Getter @Setter private String source;

    @Getter @Setter private ArtifactSourceType sourceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id")
    @Getter @Setter private EcosContentEntity content;
}
