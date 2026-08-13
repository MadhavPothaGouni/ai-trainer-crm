package com.aitrainercrm.platform.region.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** parentRegionId IS editable here, unlike SavedView's entityType - reparenting an existing region
 * (e.g. folding "US-Central" under "North America" instead of the top level) is a normal, expected
 * edit, not a "retire and recreate" situation. RegionService#assertNoCycle re-validates the new
 * parent on every update, not just at creation. */
public record UpdateRegionRequest(
        @NotBlank @Size(max = 150) String name, UUID parentRegionId, @Size(max = 2000) String description) {
}
