package ru.citeck.ecos.apps.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "ecos_app_rev")
public class EcosAppRevEntity extends AbstractImmutableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_app_rev_id_gen")
    @SequenceGenerator(name = "ecos_app_rev_id_gen")
    @Getter @Setter private Long id;

    @Column(name="ext_id")
    @Getter @Setter private String extId;

    @Getter @Setter private String version;
    @Getter @Setter private String name;

    @ManyToOne
    @JoinColumn(name = "app_id")
    @Getter @Setter private EcosAppEntity application;

    @JsonIgnore
    @ManyToMany
    @JoinTable(
        name = "ecos_app_modules",
        joinColumns = {@JoinColumn(name = "app_rev_id", referencedColumnName = "id")},
        inverseJoinColumns = {@JoinColumn(name = "module_rev_id", referencedColumnName = "id")})
    @BatchSize(size = 20)
    @Getter @Setter private Set<EcosModuleRevEntity> modules = new HashSet<>();
}
