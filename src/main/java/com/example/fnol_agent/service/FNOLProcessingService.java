package com.example.fnol_agent.service;


import com.example.fnol_agent.model.FNOLDocument;
import com.example.fnol_agent.model.ProcessingResult;
import com.example.fnol_agent.model.RoutingDecision;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Main service for processing FNOL documents
 */
@Service
@RequiredArgsConstructor
public class FNOLProcessingService {

    private final DocumentExtractionService extractionService;
    private final ClaimRoutingService routingService;
    private final ObjectMapper objectMapper;

    /**
     * Process a FNOL document file
     */
    public ProcessingResult processDocument(MultipartFile file) {
        try {
            // Extract text from document
            String text = extractText(file);

            // Extract FNOL information
            FNOLDocument document = extractionService.extractFNOLDocument(text);

            // Determine routing
            RoutingDecision routing = routingService.determineRoute(document);
            String reasoning = routingService.generateReasoning(document, routing);

            // Get missing fields and warnings
            List<String> missingFields = document.getMissingFields();
            List<String> warnings = routingService.generateWarnings(document);

            // Build extracted fields map
            Map<String, Object> extractedFields = buildExtractedFieldsMap(document);

            // Determine status
            String status = determineStatus(missingFields);

            return ProcessingResult.builder()
                    .extractedFields(extractedFields)
                    .missingFields(missingFields)
                    .recommendedRoute(routing.name())
                    .reasoning(reasoning)
                    .status(status)
                    .warnings(warnings.isEmpty() ? null : warnings)
                    .build();

        } catch (Exception e) {
            return ProcessingResult.builder()
                    .status("FAILED")
                    .errors(List.of("Error processing document: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Extract text from document based on file type
     */
    private String extractText(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();

        if (filename == null) {
            throw new IllegalArgumentException("File name is required");
        }

        if (filename.toLowerCase().endsWith(".pdf")) {
            return extractionService.extractTextFromPdf(file);
        } else if (filename.toLowerCase().endsWith(".txt")) {
            return extractionService.extractTextFromTxt(file);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported file type. Only PDF and TXT files are supported."
            );
        }
    }

    /**
     * Build extracted fields map for JSON output
     */
    private Map<String, Object> buildExtractedFieldsMap(FNOLDocument document) {
        Map<String, Object> fields = new LinkedHashMap<>();

        // Policy Information
        if (document.getPolicyInformation() != null) {
            fields.put("policyInformation", objectMapper.convertValue(
                    document.getPolicyInformation(), Map.class
            ));
        }

        // Incident Information
        if (document.getIncidentInformation() != null) {
            fields.put("incidentInformation", objectMapper.convertValue(
                    document.getIncidentInformation(), Map.class
            ));
        }

        // Involved Parties
        if (document.getInvolvedParties() != null && !document.getInvolvedParties().isEmpty()) {
            fields.put("involvedParties", document.getInvolvedParties().stream()
                    .map(party -> objectMapper.convertValue(party, Map.class))
                    .toList());
        }

        // Asset Details
        if (document.getAssetDetails() != null) {
            fields.put("assetDetails", objectMapper.convertValue(
                    document.getAssetDetails(), Map.class
            ));
        }

        // Other fields
        if (document.getClaimType() != null) {
            fields.put("claimType", document.getClaimType());
        }

        if (document.getInitialEstimate() != null) {
            fields.put("initialEstimate", document.getInitialEstimate());
        }

        if (document.getAttachments() != null && !document.getAttachments().isEmpty()) {
            fields.put("attachments", document.getAttachments());
        }

        return fields;
    }

    /**
     * Determine processing status
     */
    private String determineStatus(List<String> missingFields) {
        if (missingFields.isEmpty()) {
            return "SUCCESS";
        } else if (missingFields.size() <= 3) {
            return "PARTIAL";
        } else {
            return "INCOMPLETE";
        }
    }
}
