package ru.citeck.ecos.apps.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "ecos_app_module_type_meta")
public class AppModuleTypeMetaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_app_module_type_meta_id_gen")
    @SequenceGenerator(name = "ecos_app_module_type_meta_id_gen")
    @Getter @Setter private Long id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "app_id")
    @Getter @Setter private EcosAppEntity app;

    @Column(name = "module_type")
    @Getter @Setter private String moduleType;

    @Column(name = "last_consumed")
    @Getter @Setter private long lastConsumed;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractAuditingEntity that = (AbstractAuditingEntity) o;
        return Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    @Override
    public String toString() {
        return getClass() + " Entity{id=" + getId() + '}';
    }
}
