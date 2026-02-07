package com.example.fnol_agent.service;

import com.example.fnol_agent.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class TxtExtractionService {

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

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b"
    );

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"
    );

    /**
     * Extract FNOL document from text content
     */
    public FNOLDocument extractTxtFNOLDocument(MultipartFile file) throws IOException {
        String text = new String(file.getBytes());

        FNOLDocument document = FNOLDocument.builder()
                .policyInformation(extractPolicyInformation(text))
                .incidentInformation(extractIncidentInformation(text))
                .involvedParties(extractInvolvedParties(text))
                .assetDetails(extractAssetDetails(text))
                .claimType(extractClaimType(text))
                .attachments(extractAttachments(text))
                .initialEstimate(extractInitialEstimate(text))
                .build();

        return document;
    }

    /**
     * Extract policy information
     */
    private PolicyInformation extractPolicyInformation(String text) {
        PolicyInformation.PolicyInformationBuilder builder = PolicyInformation.builder();

        // Extract policy number
        Matcher policyMatcher = POLICY_NUMBER_PATTERN.matcher(text);
        if (policyMatcher.find()) {
            String policyNumber = policyMatcher.group(1).trim();
            builder.policyNumber(policyNumber);
        }

        // Extract policyholder name
        Matcher holderMatcher = POLICYHOLDER_PATTERN.matcher(text);
        if (holderMatcher.find()) {
            String policyholderName = holderMatcher.group(1).trim();
            builder.policyholderName(policyholderName);
        }

        // Extract agency customer ID (if present)
        String agencyId = extractFieldValue(text, "(?:Agency Customer ID|Customer ID)");
        if (agencyId != null && !agencyId.isBlank()) {
            builder.agencyCustomerId(agencyId);
        }

        // Extract effective dates
        String effectiveDatesSection = extractSection(text, "(?:Effective Date|Effective Dates)", 200);
        List<LocalDate> dates = extractDates(effectiveDatesSection);

        if (!dates.isEmpty()) {
            builder.effectiveDate(dates.get(0));
        } else {
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
        String timeSection = extractSection(text, "(?:Time|Incident Time)", 100);
        Matcher timeMatcher = TIME_PATTERN.matcher(timeSection);
        if (timeMatcher.find()) {
            String timeStr = timeMatcher.group(1).trim();
            builder.incidentTime(timeStr);
        }

        // Extract location - build comprehensive location string
        String location = buildLossLocation(text);
        builder.location(location);

        // Extract description
        String description = extractDescription(text);
        builder.description(description);

        return builder.build();
    }

    /**
     * Build loss location from multiple potential fields
     */
    private String buildLossLocation(String text) {
        List<String> locationParts = new ArrayList<>();

        // Try to extract structured location
        String street = extractFieldValue(text, "(?:Street|Address)");
        String city = extractFieldValue(text, "(?:City|Location)");
        String state = extractFieldValue(text, "(?:State)");
        String zip = extractFieldValue(text, "(?:Zip|ZIP|Postal Code)");

        // Build location from parts
        if (street != null && !street.isBlank()) {
            locationParts.add(street);
        }
        if (city != null && !city.isBlank()) {
            locationParts.add(city);
        }
        if (state != null && !state.isBlank()) {
            locationParts.add(state);
        }
        if (zip != null && !zip.isBlank()) {
            locationParts.add(zip);
        }

        if (!locationParts.isEmpty()) {
            return String.join(", ", locationParts);
        }

        // Fallback to general location extraction
        String location = extractFieldValue(text, "(?:Location|Address|Scene)");
        return location != null ? location : "";
    }

    /**
     * Extract involved parties
     */
    private List<InvolvedParty> extractInvolvedParties(String text) {
        List<InvolvedParty> parties = new ArrayList<>();

        // Extract claimant/insured
        String claimantName = extractFieldValue(text, "(?:Claimant|Insured|Policyholder)(?:\\s+(?:Name|Information))?");
        if (claimantName != null && !claimantName.isBlank()) {

            // Extract phones
            String claimantSection = extractSection(text, "(?:Claimant|Insured)", 300);
            List<String> phones = extractPhones(claimantSection);
            String primaryPhone = phones.size() > 0 ? phones.get(0) : null;
            String secondaryPhone = phones.size() > 1 ? phones.get(1) : null;

            // Extract emails
            List<String> emails = extractEmails(claimantSection);
            String primaryEmail = emails.size() > 0 ? emails.get(0) : null;
            String secondaryEmail = emails.size() > 1 ? emails.get(1) : null;

            // Determine phone types (default to CELL for primary)
            AssetDetails.PhoneType primaryPhoneType = primaryPhone != null ? AssetDetails.PhoneType.CELL : null;
            AssetDetails.PhoneType secondaryPhoneType = secondaryPhone != null ? AssetDetails.PhoneType.HOME : null;

            InvolvedParty claimant = InvolvedParty.builder()
                    .name(claimantName)
                    .role("CLAIMANT")
                    .primaryPhone(primaryPhone)
                    .primaryPhoneType(primaryPhoneType)
                    .secondaryPhone(secondaryPhone)
                    .secondaryPhoneType(secondaryPhoneType)
                    .primaryMailId(primaryEmail)
                    .secondaryMailId(secondaryEmail)
                    .build();

            parties.add(claimant);
            log.debug("Added claimant: {}", claimantName);
        }

        // Extract third party information
        buildThirdParties(text, parties);

        log.info("Extracted {} involved parties", parties.size());
        return parties;
    }

    /**
     * Build third party information (owner and driver if different)
     */
    private void buildThirdParties(String text, List<InvolvedParty> parties) {

        // Extract third party owner
        String ownerSection = extractSection(text, "(?:Third Party|Other (?:Vehicle|Driver)|Owner)", 400);
        String ownerName = extractFieldValue(ownerSection, "(?:Name|Third Party)");

        if (ownerName != null && !ownerName.isBlank()) {
            List<String> ownerPhones = extractPhones(ownerSection);
            List<String> ownerEmails = extractEmails(ownerSection);

            InvolvedParty owner = InvolvedParty.builder()
                    .name(ownerName)
                    .role("THIRD_PARTY_OWNER")
                    .primaryPhone(ownerPhones.size() > 0 ? ownerPhones.get(0) : null)
                    .primaryPhoneType(ownerPhones.size() > 0 ? AssetDetails.PhoneType.CELL : null)
                    .secondaryPhone(ownerPhones.size() > 1 ? ownerPhones.get(1) : null)
                    .secondaryPhoneType(ownerPhones.size() > 1 ? AssetDetails.PhoneType.HOME : null)
                    .primaryMailId(ownerEmails.size() > 0 ? ownerEmails.get(0) : null)
                    .secondaryMailId(ownerEmails.size() > 1 ? ownerEmails.get(1) : null)
                    .build();

            parties.add(owner);
        }

        // Check if driver is different from owner
        String driverSection = extractSection(text, "(?:Driver)", 300);
        String driverName = extractFieldValue(driverSection, "(?:Driver)");

        if (driverName != null && !driverName.isBlank() &&
                (ownerName == null || !driverName.equals(ownerName))) {

            List<String> driverPhones = extractPhones(driverSection);
            List<String> driverEmails = extractEmails(driverSection);

            InvolvedParty driver = InvolvedParty.builder()
                    .name(driverName)
                    .role("THIRD_PARTY_DRIVER")
                    .primaryPhone(driverPhones.size() > 0 ? driverPhones.get(0) : null)
                    .primaryPhoneType(driverPhones.size() > 0 ? AssetDetails.PhoneType.CELL : null)
                    .secondaryPhone(driverPhones.size() > 1 ? driverPhones.get(1) : null)
                    .secondaryPhoneType(driverPhones.size() > 1 ? AssetDetails.PhoneType.HOME : null)
                    .primaryMailId(driverEmails.size() > 0 ? driverEmails.get(0) : null)
                    .secondaryMailId(driverEmails.size() > 1 ? driverEmails.get(1) : null)
                    .build();

            parties.add(driver);
            log.debug("Added third party driver: {}", driverName);
        }
    }

    /**
     * Extract asset details
     */
    private AssetDetails extractAssetDetails(String text) {

        AssetDetails.AssetDetailsBuilder builder = AssetDetails.builder();

        // Determine asset type
        String assetType = "VEHICLE"; // Default
        if (text.toLowerCase().contains("property damage") &&
                !text.toLowerCase().contains("vehicle")) {
            assetType = "PROPERTY";
        }
        builder.assetType(assetType);

        // Extract VIN or asset ID
        Matcher vinMatcher = VIN_PATTERN.matcher(text);
        if (vinMatcher.find()) {
            String vin = vinMatcher.group(1);
            builder.assetId(vin);
        } else {
            // Try to extract plate number as asset ID
            String plateNumber = extractFieldValue(text, "(?:Plate Number|License Plate)");
            if (plateNumber != null && !plateNumber.isBlank()) {
                builder.assetId(plateNumber);
            }
        }

        // Extract estimated damage
        BigDecimal damage = extractAmount(text, "(?:Estimated Damage|Damage Estimate)");
        builder.estimatedDamage(damage);

        // Extract damage description
        String damageDesc = extractFieldValue(text, "(?:Describe Damage|Damage Description)");
        builder.description(damageDesc);
        return builder.build();
    }

    /**
     * Extract claim type
     */
    private ClaimType extractClaimType(String text) {

        String lowerText = text.toLowerCase();

        ClaimType claimType;
        if (lowerText.contains("injury") || lowerText.contains("bodily harm")) {
            claimType = ClaimType.INJURY;
        } else if (lowerText.contains("property damage") && !lowerText.contains("vehicle")) {
            claimType = ClaimType.PROPERTY;
        } else {
            claimType = ClaimType.VEHICLE;
        }
        return claimType;
    }

    /**
     * Extract attachments list
     */
    private List<String> extractAttachments(String text) {
        List<String> attachments = new ArrayList<>();

        String attachmentSection = extractSection(text, "(?:Attachments|Supporting Documents)", 200);
        if (attachmentSection != null) {
            Pattern attachmentPattern = Pattern.compile(
                    "(?:Photos?|Pictures?|Reports?|Documents?)(?:\\s+(?:of|\\()?)?([^,\\n]+)",
                    Pattern.CASE_INSENSITIVE
            );
            Matcher matcher = attachmentPattern.matcher(attachmentSection);

            while (matcher.find()) {
                String attachment = matcher.group(0).trim();
                if (!attachment.isEmpty()) {
                    attachments.add(attachment);
                }
            }
        }
        return attachments;
    }

    /**
     * Extract initial estimate
     */
    private BigDecimal extractInitialEstimate(String text) {
        BigDecimal estimate = extractAmount(text, "(?:Initial Estimate|Estimate|Claim Amount)");
        return estimate;
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
            }
        }
        return dates;
    }

    /**
     * Parse date string with multiple format support
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }

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
                return LocalDate.parse(dateStr.trim(), formatter);
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
        Pattern pattern = Pattern.compile(
                fieldPattern + "\\s*:?\\s*" + AMOUNT_PATTERN.pattern(),
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            String amountStr = matcher.group(matcher.groupCount()).replace(",", "");
            try {
                return new BigDecimal(amountStr);
            } catch (NumberFormatException e) {
            }
        }

        // Try to find any amount near the field
        String section = extractSection(text, fieldPattern, 100);
        Matcher amountMatcher = AMOUNT_PATTERN.matcher(section);
        if (amountMatcher.find()) {
            String amountStr = amountMatcher.group(1).replace(",", "");
            try {
                return new BigDecimal(amountStr);
            } catch (NumberFormatException e) {
            }
        }
        return null;
    }

    /**
     * Extract field value using pattern
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
                "(?:Description|Incident Description|Details)\\s*:?\\s*(.+?)(?:\\n\\n|Claimant|Third Party|Vehicle Details|Asset Details|$)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    /**
     * Extract section of text around a pattern
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
     * Extract all phone numbers from text
     */
    private List<String> extractPhones(String text) {
        List<String> phones = new ArrayList<>();
        Matcher matcher = PHONE_PATTERN.matcher(text);

        while (matcher.find()) {
            phones.add(matcher.group());
        }

        return phones;
    }

    /**
     * Extract all email addresses from text
     */
    private List<String> extractEmails(String text) {
        List<String> emails = new ArrayList<>();
        Matcher matcher = EMAIL_PATTERN.matcher(text);

        while (matcher.find()) {
            emails.add(matcher.group());
        }

        return emails;
    }

}