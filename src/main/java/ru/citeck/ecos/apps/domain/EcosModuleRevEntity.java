package ru.citeck.ecos.apps.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import ru.citeck.ecos.apps.app.module.ModuleRevType;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "ecos_module_rev")
public class EcosModuleRevEntity extends AbstractImmutableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_module_rev_id_gen")
    @SequenceGenerator(name = "ecos_module_rev_id_gen")
    @Getter @Setter private Long id;

    @Column(name="model_version")
    @Getter @Setter private Integer modelVersion;

    @ManyToOne
    @JoinColumn(name = "module_id")
    @Getter @Setter private EcosModuleEntity module;

    @Deprecated
    @Column(name="is_user_rev")
    @Getter @Setter private Boolean isUserRev;

    @Enumerated(EnumType.ORDINAL)
    @Column(name="rev_type")
    @Getter @Setter private ModuleRevType revType;

    @ManyToOne
    @JoinColumn(name = "prev_rev_id")
    @Getter @Setter private EcosModuleRevEntity prevRev;

    @Column(name="ext_id")
    @Getter @Setter private String extId;
    @Getter @Setter private String source;

    @ManyToOne
    @JoinColumn(name = "content_id")
    @Getter @Setter private EcosContentEntity content;

    @JsonIgnore
    @ManyToMany
    @JoinTable(
        name = "ecos_app_modules",
        joinColumns = {@JoinColumn(name = "module_rev_id", referencedColumnName = "id")},
        inverseJoinColumns = {@JoinColumn(name = "app_rev_id", referencedColumnName = "id")})
    @BatchSize(size = 20)
    @Getter @Setter private Set<EcosAppRevEntity> applications = new HashSet<>();
}
