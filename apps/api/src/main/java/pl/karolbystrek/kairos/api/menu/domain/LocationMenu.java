package pl.karolbystrek.kairos.api.menu.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    @ElementCollection
    @CollectionTable(name = "menu_items", joinColumns = @JoinColumn(name = "location_id"))
    @OrderColumn(name = "position")
    @Column(name = "name", nullable = false, length = 60)
    private List<String> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static LocationMenu create(
        @NonNull UUID locationId,
        @NonNull UUID tenantId,
        @NonNull MenuDefinition definition,
        @NonNull Instant now
    ) {
        var menu = new LocationMenu();
        menu.locationId = locationId;
        menu.tenantId = tenantId;
        menu.createdAt = now;
        menu.define(definition, now);
        return menu;
    }

    public void update(@NonNull MenuDefinition definition, @NonNull Instant now) {
        if (items.equals(definition.items())) {
            return;
        }
        define(definition, now);
    }

    public List<String> getItems() {
        return List.copyOf(items);
    }

    private void define(MenuDefinition definition, Instant now) {
        items.clear();
        items.addAll(definition.items());
        updatedAt = now;
    }
}
