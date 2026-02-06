package com.example.fnol_agent.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Incident Information extracted from FNOL document
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentInformation {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate incidentDate;

    private String incidentTime;

    private String location;

    private String description;


    private static final List<String> FRAUD_KEYWORDS = List.of(
            "fraud",
            "inconsistent",
            "staged",
            "fake"
    );

    /**
     * Check if all mandatory incident fields are present
     */
    public boolean isComplete() {
        return incidentDate != null &&
                location != null && !location.isBlank() &&
                description != null && !description.isBlank();
    }

    /**
     * Check if description contains fraud indicators
     */
    public boolean hasFraudIndicators() {
        if (description == null || description.isBlank()) {
            return false;
        }

        String text = description.toLowerCase();

        return FRAUD_KEYWORDS.stream().anyMatch(text::contains);
    }
}