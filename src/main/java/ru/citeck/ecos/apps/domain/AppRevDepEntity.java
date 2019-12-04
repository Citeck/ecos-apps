package ru.citeck.ecos.apps.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "ecos_app_rev_deps")
public class AppRevDepEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_app_rev_deps_id_gen")
    @SequenceGenerator(name = "ecos_app_rev_deps_id_gen")
    @Getter @Setter private Long id;

    @Getter @Setter private String version;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "app_rev_id")
    @Getter @Setter private EcosAppRevEntity source;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "dep_id")
    @Getter @Setter private EcosAppEntity target;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AppRevDepEntity that = (AppRevDepEntity) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
