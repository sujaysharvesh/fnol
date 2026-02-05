package com.example.fnol_agent.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

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

    @JsonFormat(pattern = "HH:mm")
    private LocalTime incidentTime;

    private String location;

    private String description;

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
        if (description == null) {
            return false;
        }

        String lowerDesc = description.toLowerCase();
        return lowerDesc.contains("fraud") ||
                lowerDesc.contains("inconsistent") ||
                lowerDesc.contains("staged") ||
                lowerDesc.contains("suspicious") ||
                lowerDesc.contains("fake");
    }
}