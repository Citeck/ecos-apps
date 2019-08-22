package ru.citeck.ecos.apps.domain;

import javax.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "ecos_app_module")
public class EcosAppModule extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    private String uuid;
    private String type;

    public Long getId() {
        return id;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EcosAppModule module = (EcosAppModule) o;
        return Objects.equals(id, module.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "EcosAppModule{" +
            "id=" + id +
            ", uuid='" + uuid + '\'' +
            ", type='" + type + '\'' +
            '}';
    }
}
