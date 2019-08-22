package ru.citeck.ecos.apps.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.sql.Blob;
import java.util.Objects;

@Entity
@Table(name = "ecos_app_module_version")
public class EcosAppModuleVersion extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Getter private Long id;

    @Getter @Setter private long version;

    @JsonIgnore
    @Column(name="module_id")
    @Getter @Setter private EcosApplication module;

    @Getter @Setter private String key;
    @Getter @Setter private String name;
    @Getter @Setter private Blob data;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EcosAppModuleVersion module = (EcosAppModuleVersion) o;
        return Objects.equals(id, module.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "EcosAppModuleVersion{" +
            "id=" + id +
            ", version=" + version +
            ", key='" + key + '\'' +
            ", name='" + name + '\'' +
            '}';
    }
}
