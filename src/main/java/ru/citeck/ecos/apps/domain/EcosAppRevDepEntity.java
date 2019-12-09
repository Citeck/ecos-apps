package ru.citeck.ecos.apps.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "ecos_app_rev_dep")
public class EcosAppRevDepEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_app_rev_dep_id_gen")
    @SequenceGenerator(name = "ecos_app_rev_dep_id_gen")
    @Getter @Setter private Long id;

    @Getter @Setter private String version;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "source_app_rev_id")
    @Getter @Setter private EcosAppRevEntity source;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "target_app_id")
    @Getter @Setter private EcosAppEntity target;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EcosAppRevDepEntity that = (EcosAppRevDepEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
