package com.example.fnol_agent.controller;


import com.example.fnol_agent.model.ProcessingResult;
import com.example.fnol_agent.service.FNOLProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for FNOL document processing
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/fnol")
@RequiredArgsConstructor
@Tag(name = "FNOL Processing", description = "Endpoints for First Notice of Loss document processing")
public class FNOLController {

    private final FNOLProcessingService processingService;

    @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Process FNOL Document",
            description = "Upload and process a First Notice of Loss document (PDF or TXT format). " +
                    "Extracts key fields, identifies missing information, and routes the claim."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Document processed successfully",
            content = @Content(schema = @Schema(implementation = ProcessingResult.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "Invalid file or bad request"
    )
    @ApiResponse(
            responseCode = "500",
            description = "Internal server error during processing"
    )
    public ResponseEntity<ProcessingResult> processDocument(
            @Parameter(description = "FNOL document file (PDF or TXT)", required = true)
            @RequestParam("file") MultipartFile file) throws IOException {

        // Validate file
        if (file.isEmpty()) {
            ProcessingResult errorResult = ProcessingResult.builder()
                    .status("FAILED")
                    .errors(java.util.List.of("File is empty"))
                    .build();
            return ResponseEntity.badRequest().body(errorResult);
        }

        String filename = file.getOriginalFilename();
        if (filename == null ||
                (!filename.toLowerCase().endsWith(".pdf") && !filename.toLowerCase().endsWith(".txt"))) {
            ProcessingResult errorResult = ProcessingResult.builder()
                    .status("FAILED")
                    .errors(java.util.List.of("Invalid file type. Only PDF and TXT files are supported."))
                    .build();
            return ResponseEntity.badRequest().body(errorResult);
        }

        // Process document
        ProcessingResult result = processingService.processDocument(file);

        // Determine HTTP status based on processing result
        HttpStatus status = "FAILED".equals(result.getStatus())
                ? HttpStatus.INTERNAL_SERVER_ERROR
                : HttpStatus.OK;

        return ResponseEntity.status(status).body(result);
    }

    @PostMapping(value = "/test", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Process FNOL Document",
            description = "Upload and process a First Notice of Loss document (PDF or TXT format). " +
                    "Extracts key fields, identifies missing information, and routes the claim."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Document processed successfully",
            content = @Content(schema = @Schema(implementation = ProcessingResult.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "Invalid file or bad request"
    )
    @ApiResponse(
            responseCode = "500",
            description = "Internal server error during processing"
    )
    public Map<String, String> test(
            @Parameter(description = "FNOL document file (PDF or TXT)", required = true)
            @RequestParam("file") MultipartFile file) throws IOException {

        Map<String, String> formData = new HashMap<>();

        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();

            if (acroForm != null) {
                log.info("PDF has fillable form fields");

                for (PDField field : acroForm.getFields()) {
                    String fieldName = field.getFullyQualifiedName();
                    String fieldValue = field.getValueAsString();

                    if (fieldValue != null && !fieldValue.trim().isEmpty()) {
                        formData.put(fieldName, fieldValue.trim());
                        log.debug("Form field - {}: {}", fieldName, fieldValue);
                    }
                }

                log.info("Extracted {} form fields", formData.size());
            } else {
                log.info("PDF does not have fillable form fields");
            }
        }

        return formData;
    }


}

