package ru.citeck.ecos.apps.domain.module.repo;

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

    @ManyToOne
    @JoinColumn(name = "source_id")
    @Getter @Setter private EcosModuleEntity source;

    @ManyToOne
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
        return Objects.equals(source, that.source)
            && Objects.equals(target, that.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
