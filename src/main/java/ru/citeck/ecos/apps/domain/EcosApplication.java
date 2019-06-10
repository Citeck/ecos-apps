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

    private long version;
    private String key;
    private String name;
    private boolean deployed;

    @JsonIgnore
    @ManyToMany
    @JoinTable(
        name = "ecos_application_modules",
        joinColumns = {@JoinColumn(name = "app_id", referencedColumnName = "id")},
        inverseJoinColumns = {@JoinColumn(name = "module_id", referencedColumnName = "id")})
    @BatchSize(size = 20)
    private Set<EcosAppModule> modules = new HashSet<>();

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDeployed() {
        return deployed;
    }

    public void setDeployed(boolean deployed) {
        this.deployed = deployed;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Set<EcosAppModule> getModules() {
        return modules;
    }

    public void setModules(Set<EcosAppModule> modules) {
        this.modules = modules;
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
            ", version=" + version +
            ", key='" + key + '\'' +
            ", name='" + name + '\'' +
            ", deployed=" + deployed +
            ", modules=" + modules +
            '}';
    }
}
