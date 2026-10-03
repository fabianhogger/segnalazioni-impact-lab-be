package it.milano.segnalazioni.domain;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Whatever the model could pull out of the free text. Every field is nullable:
 * citizens routinely write "the big pothole near the school" and nothing else,
 * and the service must degrade to asking rather than inventing an address.
 */
public record LocationHint(
        @JsonPropertyDescription("Street name as written by the citizen, normalised, e.g. 'Via Paolo Sarpi'. Null if absent.")
        String street,

        @JsonPropertyDescription("Civic number if stated, e.g. '12/A'. Null if absent.")
        String civicNumber,

        @JsonPropertyDescription("A landmark, shop, metro stop or intersection that identifies the spot when there is no address.")
        String landmark,

        @JsonPropertyDescription("Milan Municipio 1-9 if it can be inferred with confidence, otherwise null.")
        Integer municipio
) {
    public boolean isPreciseEnoughForDispatch() {
        return street != null && !street.isBlank();
    }

    public String asText() {
        StringBuilder sb = new StringBuilder();
        if (street != null && !street.isBlank()) {
            sb.append(street);
            if (civicNumber != null && !civicNumber.isBlank()) {
                sb.append(' ').append(civicNumber);
            }
        }
        if (landmark != null && !landmark.isBlank()) {
            if (!sb.isEmpty()) sb.append(" — ");
            sb.append(landmark);
        }
        if (sb.isEmpty()) sb.append("posizione non specificata");
        if (municipio != null) sb.append(" (Municipio ").append(municipio).append(')');
        return sb.toString();
    }
}
