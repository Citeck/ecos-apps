package ru.citeck.ecos.apps.domain;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "ecos_module_dep")
public class EcosModuleDepEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ecos_module_dep_id_gen")
    @SequenceGenerator(name = "ecos_module_dep_id_gen")
    @Getter @Setter private Long id;

    @OneToOne
    @JoinColumn(name = "source_id")
    @Getter @Setter private EcosModuleEntity source;

    @OneToOne
    @JoinColumn(name = "target_id")
    @Getter @Setter private EcosModuleEntity target;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EcosModuleDepEntity that = (EcosModuleDepEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
