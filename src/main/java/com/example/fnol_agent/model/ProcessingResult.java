package com.example.fnol_agent.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Result of FNOL document processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProcessingResult {

    private Map<String, Object> extractedFields;

    private List<String> missingFields;

    private String recommendedRoute;

    private String reasoning;

    private String status; // SUCCESS, PARTIAL, FAILED

    private List<String> warnings;

    private List<String> errors;
}