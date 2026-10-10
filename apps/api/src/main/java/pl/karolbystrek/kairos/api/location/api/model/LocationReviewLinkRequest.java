package pl.karolbystrek.kairos.api.location.api.model;

import jakarta.validation.constraints.Size;

public record LocationReviewLinkRequest(@Size(max = 2048) String googleReviewUrl) {
}
