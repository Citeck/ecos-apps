package ru.citeck.ecos.apps.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "ecos_application_version")
public class EcosApplicationVersion extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Getter private Long id;

    @Getter @Setter private long version;
    @Getter @Setter private String key;
    @Getter @Setter private String name;
    @Getter @Setter private String status;

    @JsonIgnore
    @ManyToMany
    @JoinTable(
        name = "ecos_application_modules",
        joinColumns = {@JoinColumn(name = "app_ver_id", referencedColumnName = "id")},
        inverseJoinColumns = {@JoinColumn(name = "module_ver_id", referencedColumnName = "id")})
    @BatchSize(size = 20)
    @Getter private Set<EcosAppModuleVersion> modules = new HashSet<>();
    @Column(name="app_id")
    @Getter @Setter private Set<EcosApplication> application;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EcosApplicationVersion that = (EcosApplicationVersion) o;
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
            ", version=" + version +
            ", key='" + key + '\'' +
            ", name='" + name + '\'' +
            ", status=" + status +
            ", modules=" + modules +
            '}';
    }
}
