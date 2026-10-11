package pl.karolbystrek.kairos.api.menu.api.model;

import pl.karolbystrek.kairos.api.menu.application.model.MenuView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MenuResponse(
        UUID id,
        String name,
        List<String> items,
        Instant createdAt,
        Instant updatedAt
) {
    public static MenuResponse from(MenuView menu) {
        return new MenuResponse(menu.id(), menu.name(), menu.items(), menu.createdAt(), menu.updatedAt());
    }
}
