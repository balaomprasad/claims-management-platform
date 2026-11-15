package com.claimsphere.claim.dto;

import com.claimsphere.claim.domain.ClaimPriority;
import jakarta.validation.constraints.NotNull;

public record ChangePriorityRequest(@NotNull ClaimPriority priority) {
}
