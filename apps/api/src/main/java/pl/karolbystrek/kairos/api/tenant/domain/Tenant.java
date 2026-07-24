package pl.karolbystrek.kairos.api.tenant.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "tenants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tenant {

    @Id
    private UUID id;

    public static Tenant create() {
        var tenant = new Tenant();
        tenant.id = UUID.randomUUID();
        return tenant;
    }
}
