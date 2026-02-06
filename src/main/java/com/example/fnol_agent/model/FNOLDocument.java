package com.example.fnol_agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Complete FNOL Document representation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FNOLDocument {

    private PolicyInformation policyInformation;

    private IncidentInformation incidentInformation;

    @Builder.Default
    private List<InvolvedParty> involvedParties = new ArrayList<>();

    private AssetDetails assetDetails;

    private String claimType; // PROPERTY, LIABILITY, INJURY, COMPREHENSIVE

    @Builder.Default
    private List<String> attachments = new ArrayList<>();

    private BigDecimal initialEstimate;

    /**
     * Get list of all missing mandatory fields
     */
    public List<String> getMissingFields() {
        List<String> missing = new ArrayList<>();

        // Policy Information
        if (policyInformation == null) {
            missing.add("policyInformation");
        } else {
            if (policyInformation.getPolicyNumber() == null || policyInformation.getPolicyNumber().isBlank()) {
                missing.add("policyInformation.policyNumber");
            }
            if (policyInformation.getPolicyholderName() == null || policyInformation.getPolicyholderName().isBlank()) {
                missing.add("policyInformation.policyholderName");
            }
        }

        // Incident Information
        if (incidentInformation == null) {
            missing.add("incidentInformation");
        } else {
            if (incidentInformation.getIncidentDate() == null) {
                missing.add("incidentInformation.incidentDate");
            }
            if (incidentInformation.getLocation() == null || incidentInformation.getLocation().isBlank()) {
                missing.add("incidentInformation.location");
            }
            if (incidentInformation.getDescription() == null || incidentInformation.getDescription().isBlank()) {
                missing.add("incidentInformation.description");
            }
        }

        // Involved Parties - at least claimant required
        if (involvedParties == null || involvedParties.isEmpty()) {
            missing.add("involvedParties.claimant");
        } else {
            boolean hasClaimant = involvedParties.stream()
                    .anyMatch(p -> "CLAIMANT".equalsIgnoreCase(p.getRole()));
            if (!hasClaimant) {
                missing.add("involvedParties.claimant");
            }
        }

        // Asset Details
        if (assetDetails == null) {
            missing.add("assetDetails");
        } else {
            if (assetDetails.getAssetType() == null || assetDetails.getAssetType().isBlank()) {
                missing.add("assetDetails.assetType");
            }
            if (assetDetails.getAssetId() == null || assetDetails.getAssetId().isBlank()) {
                missing.add("assetDetails.assetId");
            }
            if (assetDetails.getEstimatedDamage() == null) {
                missing.add("assetDetails.estimatedDamage");
            }
        }

        // Other mandatory fields
        if (claimType == null || claimType.isBlank()) {
            missing.add("claimType");
        }

        if (initialEstimate == null) {
            missing.add("initialEstimate");
        }

        return missing;
    }
}