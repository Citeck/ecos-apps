package ru.citeck.ecos.apps.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "ecos_application_revision")
public class EcosApplicationRevEntity extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_application_revision_id_gen")
    @SequenceGenerator(name = "ecos_application_revision_id_gen")
    @Getter @Setter private Long id;

    @Column(name="ext_id")
    @Getter @Setter private String extId;

    @Getter @Setter private String version;
    @Getter @Setter private String key;
    @Getter @Setter private String name;
    @Getter @Setter private Boolean fixed;

    @Column(name="app_id")
    @Getter @Setter private Long appId;

    @JsonIgnore
    @ManyToMany
    @JoinTable(
        name = "ecos_application_modules",
        joinColumns = {@JoinColumn(name = "app_rev_id", referencedColumnName = "id")},
        inverseJoinColumns = {@JoinColumn(name = "module_rev_id", referencedColumnName = "id")})
    @BatchSize(size = 20)
    @Getter private Set<EcosAppModuleRevEntity> modules = new HashSet<>();
}
