package ru.citeck.ecos.apps.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import javax.persistence.*;
import java.util.Set;

@Entity
@Table(name = "ecos_application")
public class EcosApplicationEntity extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_application_id_gen")
    @SequenceGenerator(name = "ecos_application_id_gen")
    @Getter @Setter private Long id;
    @Column(name="ext_id")
    @Getter @Setter private String extId;

    @JsonIgnore
    @ManyToMany
    @JoinTable(
        name = "ecos_application_modules",
        joinColumns = {@JoinColumn(name = "app_rev_id", referencedColumnName = "id")},
        inverseJoinColumns = {@JoinColumn(name = "module_rev_id", referencedColumnName = "id")})
    @BatchSize(size = 20)
    @Getter @Setter private Set<EcosAppModuleRevEntity> modules;
}
