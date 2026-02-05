package com.example.fnol_agent.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveStartDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveEndDate;

    /**
     * Check if all mandatory policy fields are present
     */
    public boolean isComplete() {
        return policyNumber != null && !policyNumber.isBlank() &&
                policyholderName != null && !policyholderName.isBlank() &&
                effectiveStartDate != null &&
                effectiveEndDate != null;
    }
}