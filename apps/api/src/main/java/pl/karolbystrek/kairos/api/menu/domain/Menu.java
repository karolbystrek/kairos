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
@Table(name = "menus")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Menu {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 240)
    private String normalizedName;

    @ElementCollection
    @CollectionTable(name = "menu_items", joinColumns = @JoinColumn(name = "menu_id"))
    @OrderColumn(name = "position")
    @Column(name = "name", nullable = false, length = 60)
    private List<String> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Menu create(
        @NonNull UUID tenantId,
        @NonNull MenuDefinition definition,
        @NonNull Instant now
    ) {
        var menu = new Menu();
        menu.id = UUID.randomUUID();
        menu.tenantId = tenantId;
        menu.createdAt = now;
        menu.define(definition, now);
        return menu;
    }

    public void update(@NonNull MenuDefinition definition, @NonNull Instant now) {
        if (name.equals(definition.name()) && items.equals(definition.items())) {
            return;
        }
        define(definition, now);
    }

    public List<String> getItems() {
        return List.copyOf(items);
    }

    private void define(MenuDefinition definition, Instant now) {
        name = definition.name();
        normalizedName = definition.normalizedName();
        items.clear();
        items.addAll(definition.items());
        updatedAt = now;
    }
}
