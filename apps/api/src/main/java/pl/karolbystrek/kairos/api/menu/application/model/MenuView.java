package pl.karolbystrek.kairos.api.menu.application.model;

import pl.karolbystrek.kairos.api.menu.domain.Menu;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MenuView(
        UUID id,
        String name,
        List<String> items,
        Instant createdAt,
        Instant updatedAt
) {
    public static MenuView from(Menu menu) {
        return new MenuView(
            menu.getId(),
            menu.getName(),
            menu.getItems(),
            menu.getCreatedAt(),
            menu.getUpdatedAt()
        );
    }
}
