package com.example.fnol_agent.service;


import com.example.fnol_agent.model.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for extracting information from FNOL documents
 */
@Service
public class DocumentExtractionService {

    private static final Pattern POLICY_NUMBER_PATTERN = Pattern.compile(
            "(?:policy\\s*(?:number|no\\.?|#)\\s*:?\\s*)([A-Z0-9-]+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern POLICYHOLDER_PATTERN = Pattern.compile(
            "(?:policyholder\\s*(?:name)?\\s*:?\\s*)([A-Za-z\\s.]+?)(?:\\n|,|\\||Policy)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DATE_PATTERN = Pattern.compile(
            "\\b(\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}|\\d{4}[-/]\\d{1,2}[-/]\\d{1,2})\\b"
    );

    private static final Pattern TIME_PATTERN = Pattern.compile(
            "\\b(\\d{1,2}:\\d{2}(?:\\s*(?:AM|PM))?)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?:[$€£])\\s*([\\d,]+(?:\\.\\d{2})?)"
    );

    private static final Pattern VIN_PATTERN = Pattern.compile(
            "(?:VIN|Vehicle\\s*Identification\\s*Number)\\s*:?\\s*([A-HJ-NPR-Z0-9]{17})",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Extract text from PDF file
     */
    public String extractTextFromPdf(MultipartFile file) throws IOException {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /**
     * Extract text from plain text file
     */
    public String extractTextFromTxt(MultipartFile file) throws IOException {
        return new String(file.getBytes());
    }

    /**
     * Extract FNOL document from text content
     */
    public FNOLDocument extractFNOLDocument(String text) {
        return FNOLDocument.builder()
                .policyInformation(extractPolicyInformation(text))
                .incidentInformation(extractIncidentInformation(text))
                .involvedParties(extractInvolvedParties(text))
                .assetDetails(extractAssetDetails(text))
                .claimType(extractClaimType(text))
                .attachments(new ArrayList<>())
                .initialEstimate(extractInitialEstimate(text))
                .build();
    }

    /**
     * Extract policy information
     */
    private PolicyInformation extractPolicyInformation(String text) {
        PolicyInformation.PolicyInformationBuilder builder = PolicyInformation.builder();

        // Extract policy number
        Matcher policyMatcher = POLICY_NUMBER_PATTERN.matcher(text);
        if (policyMatcher.find()) {
            builder.policyNumber(policyMatcher.group(1).trim());
        }

        // Extract policyholder name
        Matcher holderMatcher = POLICYHOLDER_PATTERN.matcher(text);
        if (holderMatcher.find()) {
            builder.policyholderName(holderMatcher.group(1).trim());
        }

        // Extract effective dates
        List<LocalDate> dates = extractDates(text);
        if (dates.size() >= 2) {
            builder.effectiveStartDate(dates.get(0));
            builder.effectiveEndDate(dates.get(1));
        }

        return builder.build();
    }

    /**
     * Extract incident information
     */
    private IncidentInformation extractIncidentInformation(String text) {
        IncidentInformation.IncidentInformationBuilder builder = IncidentInformation.builder();

        // Extract incident date
        if (text.contains("Incident Date") || text.contains("Date of Loss")) {
            String section = extractSection(text, "(?:Incident Date|Date of Loss)", 200);
            List<LocalDate> dates = extractDates(section);
            if (!dates.isEmpty()) {
                builder.incidentDate(dates.get(0));
            }
        }

        // Extract incident time
        Matcher timeMatcher = TIME_PATTERN.matcher(text);
        if (timeMatcher.find()) {
            try {
                String timeStr = timeMatcher.group(1).trim();
                LocalTime time = parseTime(timeStr);
                builder.incidentTime(time);
            } catch (Exception e) {
                // Time parsing failed, continue
            }
        }

        // Extract location
        String location = extractFieldValue(text, "(?:Location|Address|Scene)");
        builder.location(location);

        // Extract description
        String description = extractDescription(text);
        builder.description(description);

        return builder.build();
    }

    /**
     * Extract involved parties
     */
    private List<InvolvedParty> extractInvolvedParties(String text) {
        List<InvolvedParty> parties = new ArrayList<>();

        // Extract claimant
        String claimantName = extractFieldValue(text, "(?:Claimant|Insured)");
        if (claimantName != null && !claimantName.isBlank()) {
            parties.add(InvolvedParty.builder()
                    .name(claimantName)
                    .role("CLAIMANT")
                    .contactPhone(extractPhone(text))
                    .contactEmail(extractEmail(text))
                    .build());
        }

        // Extract third party if mentioned
        String thirdPartyName = extractFieldValue(text, "Third Party");
        if (thirdPartyName != null && !thirdPartyName.isBlank()) {
            parties.add(InvolvedParty.builder()
                    .name(thirdPartyName)
                    .role("THIRD_PARTY")
                    .build());
        }

        return parties;
    }

    /**
     * Extract asset details
     */
    private AssetDetails extractAssetDetails(String text) {
        AssetDetails.AssetDetailsBuilder builder = AssetDetails.builder();

        // Determine asset type
        String assetType = "VEHICLE"; // Default
        if (text.toLowerCase().contains("property damage")) {
            assetType = "PROPERTY";
        }
        builder.assetType(assetType);

        // Extract VIN
        Matcher vinMatcher = VIN_PATTERN.matcher(text);
        if (vinMatcher.find()) {
            builder.assetId(vinMatcher.group(1));
        }

        // Extract vehicle details
        builder.make(extractFieldValue(text, "Make"));
        builder.model(extractFieldValue(text, "Model"));
        builder.year(extractFieldValue(text, "Year"));

        // Extract estimated damage
        BigDecimal damage = extractAmount(text, "(?:Estimated Damage|Damage Estimate)");
        builder.estimatedDamage(damage);

        return builder.build();
    }

    /**
     * Extract claim type
     */
    private String extractClaimType(String text) {
        String lowerText = text.toLowerCase();

        if (lowerText.contains("injury") || lowerText.contains("bodily harm")) {
            return "INJURY";
        } else if (lowerText.contains("property damage")) {
            return "PROPERTY";
        } else if (lowerText.contains("liability")) {
            return "LIABILITY";
        } else if (lowerText.contains("comprehensive") || lowerText.contains("collision")) {
            return "COMPREHENSIVE";
        }

        return "PROPERTY"; // Default
    }

    /**
     * Extract initial estimate
     */
    private BigDecimal extractInitialEstimate(String text) {
        return extractAmount(text, "(?:Initial Estimate|Estimate|Claim Amount)");
    }

    /**
     * Extract dates from text
     */
    private List<LocalDate> extractDates(String text) {
        List<LocalDate> dates = new ArrayList<>();
        Matcher matcher = DATE_PATTERN.matcher(text);

        while (matcher.find()) {
            String dateStr = matcher.group(1);
            try {
                LocalDate date = parseDate(dateStr);
                if (date != null) {
                    dates.add(date);
                }
            } catch (Exception e) {
                // Continue on parse error
            }
        }

        return dates;
    }

    /**
     * Parse date string
     */
    private LocalDate parseDate(String dateStr) {
        DateTimeFormatter[] formatters = {
                DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                DateTimeFormatter.ofPattern("MM-dd-yyyy"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                DateTimeFormatter.ofPattern("MM/dd/yy"),
                DateTimeFormatter.ofPattern("MM-dd-yy")
        };

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (DateTimeParseException e) {
                // Try next formatter
            }
        }

        return null;
    }

    /**
     * Parse time string
     */
    private LocalTime parseTime(String timeStr) {
        timeStr = timeStr.replaceAll("\\s+", "").toUpperCase();

        DateTimeFormatter[] formatters = {
                DateTimeFormatter.ofPattern("HH:mm"),
                DateTimeFormatter.ofPattern("h:mma"),
                DateTimeFormatter.ofPattern("hh:mma")
        };

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalTime.parse(timeStr, formatter);
            } catch (DateTimeParseException e) {
                // Try next formatter
            }
        }

        return null;
    }

    /**
     * Extract monetary amount
     */
    private BigDecimal extractAmount(String text, String fieldPattern) {
        Pattern pattern = Pattern.compile(fieldPattern + "\\s*:?\\s*" + AMOUNT_PATTERN.pattern(),
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            String amountStr = matcher.group(matcher.groupCount()).replace(",", "");
            try {
                return new BigDecimal(amountStr);
            } catch (NumberFormatException e) {
                // Continue
            }
        }

        // Try to find any amount
        Matcher amountMatcher = AMOUNT_PATTERN.matcher(text);
        if (amountMatcher.find()) {
            String amountStr = amountMatcher.group(1).replace(",", "");
            try {
                return new BigDecimal(amountStr);
            } catch (NumberFormatException e) {
                // Continue
            }
        }

        return null;
    }

    /**
     * Extract field value
     */
    private String extractFieldValue(String text, String fieldName) {
        Pattern pattern = Pattern.compile(
                fieldName + "\\s*:?\\s*([A-Za-z0-9\\s.,'-]+?)(?:\\n|\\||$)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return null;
    }

    /**
     * Extract description
     */
    private String extractDescription(String text) {
        Pattern pattern = Pattern.compile(
                "(?:Description|Incident Description|Details)\\s*:?\\s*(.+?)(?:\\n\\n|Claimant|Third Party|$)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return null;
    }

    /**
     * Extract section of text
     */
    private String extractSection(String text, String startPattern, int length) {
        Pattern pattern = Pattern.compile(startPattern, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            int start = matcher.start();
            int end = Math.min(start + length, text.length());
            return text.substring(start, end);
        }

        return text;
    }

    /**
     * Extract phone number
     */
    private String extractPhone(String text) {
        Pattern pattern = Pattern.compile("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group();
        }

        return null;
    }

    /**
     * Extract email
     */
    private String extractEmail(String text) {
        Pattern pattern = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group();
        }

        return null;
    }
}