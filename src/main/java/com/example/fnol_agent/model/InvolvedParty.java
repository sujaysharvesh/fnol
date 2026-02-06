package com.example.fnol_agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an involved party in the claim
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvolvedParty {

    private String name;

    private String role; // CLAIMANT, THIRD_PARTY, WITNESS

    private String primaryPhone;

    private AssetDetails.PhoneType primaryPhoneType;

    private String secondaryPhone;

    private AssetDetails.PhoneType secondaryPhoneType;

    private String primaryMailId;

    private String secondaryMailId;

    /**
     * Check if party has minimum required information
     */
    public boolean isValid() {
        return name != null && !name.isBlank() &&
                role != null && !role.isBlank() &&
                (primaryPhone != null && !primaryPhone.isBlank() && primaryPhoneType != null);
    }
}
