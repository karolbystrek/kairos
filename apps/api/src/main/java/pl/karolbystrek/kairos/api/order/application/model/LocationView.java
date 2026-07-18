package pl.karolbystrek.kairos.api.order.application.model;

import java.util.UUID;

public record LocationView(
	UUID id,
	String name
) {
}
