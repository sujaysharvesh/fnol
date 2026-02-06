package com.example.fnol_agent.service;


import com.example.fnol_agent.model.ClaimType;
import com.example.fnol_agent.model.FNOLDocument;
import com.example.fnol_agent.model.RoutingDecision;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for routing claims based on business rules
 */
@Service
public class ClaimRoutingService {

    private static final BigDecimal FAST_TRACK_THRESHOLD = new BigDecimal("25000");

    /**
     * Determine routing decision for a claim
     */
    public RoutingDecision determineRoute(FNOLDocument document) {
        List<String> missingFields = document.getMissingFields();

        // Rule 1: If any mandatory field is missing → Manual review
        if (!missingFields.isEmpty()) {
            return RoutingDecision.MANUAL_REVIEW;
        }

        // Rule 2: If description contains fraud indicators → Investigation Flag
        if (document.getIncidentInformation() != null &&
                document.getIncidentInformation().hasFraudIndicators()) {
            return RoutingDecision.INVESTIGATION;
        }

        // Rule 3: If claim type = injury → Specialist Queue
        if (ClaimType.INJURY == document.getClaimType()) {
            return RoutingDecision.SPECIALIST_QUEUE;
        }

        // Rule 4: If estimated damage < $25,000 → Fast-track
        if (document.getAssetDetails() != null &&
                document.getAssetDetails().isUnderFastTrackThreshold(FAST_TRACK_THRESHOLD)) {
            return RoutingDecision.FAST_TRACK;
        }

        // Default: Standard processing
        return RoutingDecision.STANDARD_PROCESSING;
    }

    /**
     * Generate reasoning for routing decision
     */
    public String generateReasoning(FNOLDocument document, RoutingDecision decision) {
        List<String> reasons = new ArrayList<>();
        List<String> missingFields = document.getMissingFields();

        switch (decision) {
            case FAST_TRACK:
                BigDecimal damage = document.getAssetDetails().getEstimatedDamage();
                reasons.add(String.format(
                        "Estimated damage of $%,.2f is below the fast-track threshold of $%,.2f",
                        damage, FAST_TRACK_THRESHOLD
                ));
                reasons.add("All mandatory fields are present");
                reasons.add("No fraud indicators detected");
                break;

            case MANUAL_REVIEW:
                reasons.add("Missing mandatory fields: " + String.join(", ", missingFields));
                reasons.add("Manual review required to complete claim information");
                break;

            case INVESTIGATION:
                reasons.add("Fraud indicators detected in incident description");
                if (document.getIncidentInformation() != null) {
                    String desc = document.getIncidentInformation().getDescription();
                    if (desc != null) {
                        String lowerDesc = desc.toLowerCase();
                        if (lowerDesc.contains("fraud")) reasons.add("- Contains keyword: 'fraud'");
                        if (lowerDesc.contains("staged")) reasons.add("- Contains keyword: 'staged'");
                        if (lowerDesc.contains("inconsistent")) reasons.add("- Contains keyword: 'inconsistent'");
                        if (lowerDesc.contains("suspicious")) reasons.add("- Contains keyword: 'suspicious'");
                    }
                }
                reasons.add("Requires investigation before processing");
                break;

            case SPECIALIST_QUEUE:
                reasons.add("Claim type is INJURY - requires specialist handling");
                reasons.add("Routing to medical claims specialist queue");
                break;

            case STANDARD_PROCESSING:
                reasons.add("Claim meets all standard processing criteria");
                if (document.getAssetDetails() != null && document.getAssetDetails().getEstimatedDamage() != null) {
                    reasons.add(String.format(
                            "Estimated damage: $%,.2f (above fast-track threshold)",
                            document.getAssetDetails().getEstimatedDamage()
                    ));
                }
                break;
        }

        return String.join(". ", reasons) + ".";
    }

    /**
     * Get warnings for the claim
     */
    public List<String> generateWarnings(FNOLDocument document) {
        List<String> warnings = new ArrayList<>();

        // Check for incomplete but not missing fields
        if (document.getIncidentInformation() != null &&
                document.getIncidentInformation().getIncidentTime() == null) {
            warnings.add("Incident time not provided - may affect investigation");
        }

//        if (document.getInvolvedParties() != null) {
//            long partiesWithoutContact = document.getInvolvedParties().stream()
//                    .filter(p -> (p.getContactPhone() == null || p.getContactPhone().isBlank()) &&
//                            (p.getContactEmail() == null || p.getContactEmail().isBlank()))
//                    .count();
//            if (partiesWithoutContact > 0) {
//                warnings.add(String.format(
//                        "%d involved party/parties missing contact information",
//                        partiesWithoutContact
//                ));
//            }
//        }

        if (document.getAttachments() == null || document.getAttachments().isEmpty()) {
            warnings.add("No attachments/supporting documents provided");
        }

        // Check for high damage amount
        if (document.getAssetDetails() != null &&
                document.getAssetDetails().getEstimatedDamage() != null) {
            BigDecimal damage = document.getAssetDetails().getEstimatedDamage();
            if (damage.compareTo(new BigDecimal("100000")) > 0) {
                warnings.add("High damage amount - may require additional approval");
            }
        }

        return warnings;
    }
}
