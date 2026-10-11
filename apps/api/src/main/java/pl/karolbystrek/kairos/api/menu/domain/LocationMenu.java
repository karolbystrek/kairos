package pl.karolbystrek.kairos.api.menu.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "location_menus")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocationMenu {

    @Id
    @Column(name = "location_id")
    private UUID locationId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "menu_id", nullable = false)
    private UUID menuId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static LocationMenu assign(
        @NonNull UUID locationId,
        @NonNull Menu menu,
        @NonNull Instant now
    ) {
        var locationMenu = new LocationMenu();
        locationMenu.locationId = locationId;
        locationMenu.tenantId = menu.getTenantId();
        locationMenu.menuId = menu.getId();
        locationMenu.updatedAt = now;
        return locationMenu;
    }

    public void change(@NonNull Menu menu, @NonNull Instant now) {
        if (!tenantId.equals(menu.getTenantId())) {
            throw new IllegalArgumentException("A location can use only its own tenant's menu");
        }
        if (menuId.equals(menu.getId())) {
            return;
        }
        menuId = menu.getId();
        updatedAt = now;
    }
}
