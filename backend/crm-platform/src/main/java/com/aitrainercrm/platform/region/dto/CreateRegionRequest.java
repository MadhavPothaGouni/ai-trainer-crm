package com.aitrainercrm.platform.region.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** {@code parentRegionId} null means a new root region - a region tree can have more than one root
 * (e.g. separate "Americas" and "EMEA" trees that never share a common parent), there's no implicit
 * single top node. */
public record CreateRegionRequest(
        @NotBlank @Size(max = 150) String name, UUID parentRegionId, @Size(max = 2000) String description) {
}
