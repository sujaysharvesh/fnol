package com.example.fnol_agent.service;


import com.example.fnol_agent.model.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Slf4j
@Service
public class PdfExtractionService {

    /**
     * Extract text from PDF file
     */
    public FNOLDocument extractPdfFNOLDocument(MultipartFile file) throws IOException {
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

        FNOLDocument document = buildPdfFNOLDocument(formData);
        return document;
    }


    public FNOLDocument buildPdfFNOLDocument(Map<String, String> form) {
        return FNOLDocument.builder()
                .policyInformation(extractPolicyInformation(form))
                .incidentInformation(extractIncidentInformation(form))
                .involvedParties(extractInvolvedParties(form))
                .assetDetails(extractAssetDetails(form))
                .claimType(extractClaimType(form))
                .attachments(new ArrayList<>())
                .initialEstimate(extractInitialEstimate(form))
                .build();
    }

    /**
     * Extract policy information
     */
    private PolicyInformation extractPolicyInformation(Map<String, String> form) {

        PolicyInformation policyInformation = PolicyInformation.builder()
                .policyNumber(getFormValue(form, "Text7"))
                .policyholderName(getFormValue(form, "NAME OF INSURED First Middle Last"))
                .agencyCustomerId(getFormValue(form, "AGENCY CUSTOMER ID"))
                .effectiveDate(parseDate(getFormValue(form, "Text3")))
                .build();


        return policyInformation;
    }

    /**
     * Extract incident information
     */
    private IncidentInformation extractIncidentInformation(Map<String, String> form) {

        IncidentInformation incidentInformation = IncidentInformation.builder()
                .incidentDate(parseDate(getFormValue(form, "Text3")))
                .incidentTime(parseTimeAsString(form, "Text4"))
                .location(buildLossLocation(form))
                .description(getFormValue(form, "DESCRIPTION OF ACCIDENT ACORD 101 Additional Remarks Schedule may be attached if more space is required"))
                .build();

        return incidentInformation;
    }



    /**
     * Extract involved parties
     */
    private List<InvolvedParty> extractInvolvedParties(Map<String, String> form) {
        List<InvolvedParty> parties = new ArrayList<>();

        InvolvedParty claimant = InvolvedParty.builder()
                .name(getFormValue(form, "NAME OF INSURED First Middle Last"))
                .role("CLAIMANT")
                .primaryPhone(getFormValue(form, "PHONE  CELL HOME BUS PRIMARY"))
                .primaryPhoneType(getPhoneType(form, "Check Box10", "Check Box11", "Check Box12"))
                .secondaryPhone(getFormValue(form, "PHONE  SECONDARY CELL HOME BUS"))
                .secondaryPhoneType(getPhoneType(form, "Check Box13", "Check Box14", "Check Box15"))
                .primaryMailId(getFormValue(form, "PRIMARY EMAIL ADDRESS"))
                .secondaryMailId(getFormValue(form,"SECONDARY EMAIL ADDRESS"))
                .build();

        parties.add(claimant);
        buildThirdParties(form, parties);

        return parties;
    }

    private List<InvolvedParty> buildThirdParties(Map<String, String> form, List<InvolvedParty> parties) {

        String ownerName = getFormValue(form, "Text48");
        String driverName = getFormValue(form, "Text81");

        boolean sameAsOwner =
                "Yes".equalsIgnoreCase(getFormValue(form, "Check Box55"));

        // --- OWNER ---
        if (ownerName != null && !ownerName.isBlank()) {
            parties.add(
                    InvolvedParty.builder()
                            .name(ownerName)
                            .role("THIRD_PARTY_OWNER")
                            .primaryPhone(getFormValue(form, "PHONE  CELL HOME BUS PRIMARY_5"))
                            .primaryPhoneType(getPhoneType(
                                    form,
                                    "Check Box49",
                                    "Check Box50",
                                    "Check Box51"
                            ))
                            .secondaryPhone(getFormValue(form, "PHONE  SECONDARY CELL HOME BUS_5"))
                            .secondaryPhoneType(getPhoneType(
                                    form,
                                    "Check Box52",
                                    "Check Box53",
                                    "Check Box54"
                            ))
                            .primaryMailId(getFormValue(form, "PRIMARY EMAIL ADDRESS_5"))
                            .secondaryMailId(getFormValue(form, "SECONDARY EMAIL ADDRESS_5"))
                            .build()
            );
        }

        // --- DRIVER (only if different) ---
        if (!sameAsOwner &&
                driverName != null &&
                !driverName.isBlank()) {

            parties.add(
                    InvolvedParty.builder()
                            .name(driverName)
                            .role("THIRD_PARTY_DRIVER")
                            .primaryPhone(getFormValue(form, "PHONE  CELL HOME BUS PRIMARY_6"))
                            .primaryPhoneType(getPhoneType(
                                    form,
                                    "Check Box56",
                                    "Check Box57",
                                    "Check Box58"
                            ))
                            .secondaryPhone(getFormValue(form, "DRIVER SECONDARY PHONE"))
                            .secondaryPhoneType(getPhoneType(
                                    form,
                                    "Check Box59",
                                    "Check Box60",
                                    "Check Box61"
                            ))
                            .primaryMailId(getFormValue(form, "PRIMARY EMAIL ADDRESS_6"))
                            .secondaryMailId(getFormValue(form, "SECONDARY EMAIL ADDRESS_6"))
                            .build()
            );
        }

        return parties;
    }


    public AssetDetails.PhoneType getPhoneType(Map<String, String> form, String home, String bus, String cell) {

        if ("Yes".equalsIgnoreCase(form.get(home))) {
            return AssetDetails.PhoneType.HOME;
        }
        if ("Yes".equalsIgnoreCase(form.get(bus))) {
            return AssetDetails.PhoneType.BUS;
        }
        if ("Yes".equalsIgnoreCase(form.get(cell))) {
            return AssetDetails.PhoneType.CELL;
        }

        return null;
    }


    /**
     * Extract asset details
     */
    private AssetDetails extractAssetDetails(Map<String, String> form) {

        AssetDetails assetDetails = AssetDetails.builder()
                .assetType("VEHICLE")
                .assetId(getFormValue(form, "PLATE NUMBER"))
                .estimatedDamage(BigDecimal.valueOf(Double.parseDouble(getFormValue(form, "Text45"))))
                .description(getFormValue(form, "DESCRIBE DAMAGE"))
                .build();

        return assetDetails;

    }

    /**
     * Extract claim type
     */
    private ClaimType extractClaimType(Map<String, String> form) {

        if (getFormValue(form, "NAME  ADDRESSRow1") != null) {
            return ClaimType.INJURY;
        }

        return "Yes".equalsIgnoreCase(getFormValue(form, "Check Box46"))
                ? ClaimType.PROPERTY
                : ClaimType.VEHICLE;
    }


    /**
     * Extract initial estimate
     */
    private BigDecimal extractInitialEstimate(Map<String, String> form) {
        return BigDecimal.valueOf(Double.parseDouble(getFormValue(form, "Text45")));
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
                return LocalDate.parse(dateStr.trim(), formatter);
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    /**
     * Parse time string
     */
    private String parseTimeAsString(Map<String, String> form, String key) {

        String timeValue = getFormValue(form, key); // e.g. "10:30"
        if (timeValue == null || timeValue.isBlank()) {
            return null;
        }

        if ("Yes".equalsIgnoreCase(getFormValue(form, "Check Box5"))) {
            return timeValue + " AM";
        }

        if ("Yes".equalsIgnoreCase(getFormValue(form, "Check Box6"))) {
            return timeValue + " PM";
        }

        return null;
    }

    public String buildLossLocation(Map<String, String> form) {
        String street = getFormValue(form, "STREET LOCATION OF LOSS");
        String cityStateZip = getFormValue(form,  "CITY STATE ZIP");
        String country = getFormValue(form,  "COUNTRY");
        String describeLocation = getFormValue(form, "DESCRIBE LOCATION OF LOSS IF NOT AT SPECIFIC STREET ADDRESS");

        List<String> parts = new ArrayList<>();

        if (street != null && cityStateZip != null && country != null) {
            parts.add(street);
            parts.add(cityStateZip);
            parts.add(country);
        }

        if (!parts.isEmpty()) {
            return String.join(", ", parts);
        }

        if (describeLocation != null && !describeLocation.isBlank()) {
            return describeLocation.trim();
        }

        return null;
    }


    private String getFormValue(Map<String, String> form, String key) {
        return form.getOrDefault(key, "");
    }



}
