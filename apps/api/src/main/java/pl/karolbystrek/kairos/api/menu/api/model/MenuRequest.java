package pl.karolbystrek.kairos.api.menu.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MenuRequest(
        @NotBlank(message = "Menu name is required")
        @Size(max = 120, message = "Menu name must contain at most 120 characters")
        String name,

        @NotEmpty(message = "A menu must contain at least one item")
        @Size(max = 100, message = "A menu can contain at most 100 items")
        List<@NotNull(message = "Menu item name is required") String> items
) {
}
