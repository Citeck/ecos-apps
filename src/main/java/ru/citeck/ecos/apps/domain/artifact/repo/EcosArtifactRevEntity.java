package ru.citeck.ecos.apps.domain.artifact.repo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.jetbrains.annotations.Nullable;
import ru.citeck.ecos.apps.domain.common.repo.AbstractImmutableEntity;
import ru.citeck.ecos.apps.domain.application.repo.EcosAppRevEntity;
import ru.citeck.ecos.apps.domain.artifact.dto.ArtifactRevType;
import ru.citeck.ecos.apps.domain.content.repo.EcosContentEntity;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id")
    @Getter @Setter private EcosContentEntity content;

    @JsonIgnore
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "ecos_app_modules",
        joinColumns = {@JoinColumn(name = "module_rev_id", referencedColumnName = "id")},
        inverseJoinColumns = {@JoinColumn(name = "app_rev_id", referencedColumnName = "id")})
    @BatchSize(size = 20)
    @Getter @Setter private Set<EcosAppRevEntity> applications = new HashSet<>();
}
