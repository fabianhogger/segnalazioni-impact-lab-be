package it.milano.segnalazioni.adapter.in.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReportRequest(
        @NotBlank(message = "il testo della segnalazione e' obbligatorio")
        @Size(max = 5000, message = "il testo non puo' superare 5000 caratteri")
        String text,

        @Size(max = 200)
        String contact,

        @DecimalMin("45.3") @DecimalMax("45.6")
        Double latitude,

        @DecimalMin("9.0") @DecimalMax("9.4")
        Double longitude,

        List<String> attachmentIds
) {
    public List<String> attachmentIdsOrEmpty() {
        return attachmentIds == null ? List.of() : attachmentIds;
    }
}
