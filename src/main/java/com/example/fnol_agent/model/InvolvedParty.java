package com.example.fnol_agent.model;

import com.example.fnol_agent.service.PhoneType;
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

    private PhoneType primaryPhoneType;

    private String secondaryPhone;

    private PhoneType secondaryPhoneType;

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
