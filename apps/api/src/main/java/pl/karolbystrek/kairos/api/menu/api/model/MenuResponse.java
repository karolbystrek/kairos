package pl.karolbystrek.kairos.api.menu.api.model;

import pl.karolbystrek.kairos.api.menu.application.model.MenuView;

import java.time.Instant;
import java.util.List;

public record MenuResponse(List<String> items, Instant updatedAt) {
    public static MenuResponse from(MenuView menu) {
        return new MenuResponse(menu.items(), menu.updatedAt());
    }
}
