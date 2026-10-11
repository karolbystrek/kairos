package pl.karolbystrek.kairos.api.menu.api.model;

import pl.karolbystrek.kairos.api.menu.application.model.MenuView;

import java.util.Optional;

public record LocationMenuResponse(MenuResponse menu) {
    public static LocationMenuResponse from(Optional<MenuView> menu) {
        return new LocationMenuResponse(menu.map(MenuResponse::from).orElse(null));
    }
}
