package com.example.fnol_agent.model;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Asset details extracted from FNOL document
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetDetails {

    private String assetType;

    private String assetId;

    private BigDecimal estimatedDamage;

    private String description;

    /**
     * Check if all mandatory asset fields are present
     */
    public boolean isComplete() {
        return assetType != null && !assetType.isBlank() &&
                assetId != null && !assetId.isBlank() &&
                estimatedDamage != null;
    }

    /**
     * Check if damage is under fast-track threshold
     */
    public boolean isUnderFastTrackThreshold(BigDecimal threshold) {
        return estimatedDamage != null &&
                estimatedDamage.compareTo(threshold) < 25000;
    }

    public enum PhoneType {
        HOME,
        BUS,
        CELL,

    }
}
