package ru.citeck.ecos.apps.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.BatchSize;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "ecos_application")
public class EcosApplication extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @JsonIgnore
    @ManyToMany
    @JoinTable(
        name = "ecos_application_modules",
        joinColumns = {@JoinColumn(name = "app_ver_id", referencedColumnName = "id")},
        inverseJoinColumns = {@JoinColumn(name = "module_ver_id", referencedColumnName = "id")})
    @BatchSize(size = 20)
    private Set<EcosAppModuleVersion> modules = new HashSet<>();

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EcosApplication that = (EcosApplication) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "EcosApplication{" +
            "id=" + id +
            ", modules=" + modules +
            '}';
    }
}
