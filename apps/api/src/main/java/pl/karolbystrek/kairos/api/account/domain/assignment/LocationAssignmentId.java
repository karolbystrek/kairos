package pl.karolbystrek.kairos.api.account.domain.assignment;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class LocationAssignmentId implements Serializable {

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    public UUID accountId() {
        return accountId;
    }

    public UUID locationId() {
        return locationId;
    }
}
