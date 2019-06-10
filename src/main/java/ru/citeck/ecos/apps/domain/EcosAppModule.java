package ru.citeck.ecos.apps.domain;

import javax.persistence.*;
import java.sql.Blob;
import java.util.Objects;

@Entity
@Table(name = "ecos_app_module")
public class EcosAppModule extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    private long version;
    private String key;
    private String name;
    private String type;
    private String mimetype;
    private Blob data;

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

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMimetype() {
        return mimetype;
    }

    public void setMimetype(String mimetype) {
        this.mimetype = mimetype;
    }

    public Blob getData() {
        return data;
    }

    public void setData(Blob data) {
        this.data = data;
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
            ", version=" + version +
            ", key='" + key + '\'' +
            ", name='" + name + '\'' +
            ", type='" + type + '\'' +
            ", mimetype='" + mimetype + '\'' +
            '}';
    }
}
