package pl.karolbystrek.kairos.api.order.api.model;

import java.util.UUID;

public record LocationResponse(
	UUID id,
	String name
) {
}
