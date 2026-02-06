package com.example.fnol_agent.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Policy Information extracted from FNOL document
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyInformation {

    private String policyNumber;

    private String policyholderName;

    private String agencyCustomerId;

    // i am not sure about what should be place here so i'm going to place Data of loss
    private LocalDate effectiveDate;

    /**
     * Check if all mandatory policy fields are present
     */
    public boolean isComplete() {
        return policyNumber != null && !policyNumber.isBlank() &&
                policyholderName != null && !policyholderName.isBlank() &&
                effectiveDate != null;
    }
}