package pl.karolbystrek.kairos.api.account.application.port;

import pl.karolbystrek.kairos.api.account.application.model.StaffLocation;

import java.util.Optional;
import java.util.UUID;

public interface StaffLocationDirectory {

    Optional<StaffLocation> findById(UUID locationId);
}
