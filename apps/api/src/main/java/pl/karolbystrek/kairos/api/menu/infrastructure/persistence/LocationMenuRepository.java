package pl.karolbystrek.kairos.api.menu.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.karolbystrek.kairos.api.menu.domain.LocationMenu;

import java.util.UUID;

public interface LocationMenuRepository extends JpaRepository<LocationMenu, UUID> {
}
