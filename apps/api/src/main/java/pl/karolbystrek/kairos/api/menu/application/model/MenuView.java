package pl.karolbystrek.kairos.api.menu.application.model;

import pl.karolbystrek.kairos.api.menu.domain.LocationMenu;

import java.time.Instant;
import java.util.List;

public record MenuView(List<String> items, Instant updatedAt) {
    public static MenuView from(LocationMenu menu) {
        return new MenuView(menu.getItems(), menu.getUpdatedAt());
    }
}
