package it.milano.segnalazioni.domain;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * The structured reading of a citizen's free text. This is the schema handed to Claude
 * via structured outputs, so the field descriptions are part of the prompt — edit them
 * with the same care as the system prompt itself.
 */
@JsonClassDescription("Structured analysis of a citizen report about the city of Milan.")
public record Classification(
        @JsonPropertyDescription("The single category that best describes the problem being reported.")
        Category category,

        @JsonPropertyDescription("How confident you are in the category, from 0.0 to 1.0. Be honest: below 0.6 the report is routed to a human.")
        Double confidence,

        @JsonPropertyDescription("EMERGENZA only when there is immediate danger to life, health or property (fire, gas leak, injury, collapse, live wire). Otherwise ROUTINE or URGENT.")
        Severity severity,

        @JsonPropertyDescription("A short Italian title for the report, max 80 characters, no final full stop.")
        String title,

        @JsonPropertyDescription("A neutral, factual restatement of the problem in Italian, suitable for sending to a public body. Do not invent details the citizen did not write.")
        String description,

        @JsonPropertyDescription("Location details extracted from the text.")
        LocationHint location,

        @JsonPropertyDescription("Questions in Italian that must be put to the citizen before this can be filed, e.g. a missing street number. Empty list if the report is complete.")
        List<String> missingInformation,

        @JsonPropertyDescription("True if the text contains personal data about an identifiable third party (names, plate numbers, flat numbers, health details).")
        Boolean containsThirdPartyPersonalData
) {
    public boolean isEmergency() {
        return severity == Severity.EMERGENZA || category == Category.EMERGENZA;
    }

    public boolean isConfident() {
        return confidence != null && confidence >= 0.6;
    }

    public List<String> missingInformationOrEmpty() {
        return missingInformation == null ? List.of() : missingInformation;
    }
}
