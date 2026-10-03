package it.milano.segnalazioni.adapter.in.web;

import it.milano.segnalazioni.application.IntakeService;
import it.milano.segnalazioni.domain.Classification;
import it.milano.segnalazioni.domain.DispatchOutcome;

import java.util.List;
import java.util.Map;

/**
 * What the frontend gets back. Deliberately includes the classification: a citizen who
 * can see how their report was read can tell us when it was read wrong, and that
 * correction is the only cheap source of routing-quality data we have.
 */
public record ReportResponse(
        String reportId,
        String status,
        Analysis analysis,
        NextStep nextStep
) {
    public record Analysis(
            String category,
            String severity,
            Double confidence,
            String title,
            String description,
            String location,
            List<String> missingInformation,
            boolean containsThirdPartyPersonalData
    ) {}

    public record NextStep(
            String agencyId,
            String channel,
            String action,
            String message,
            String deeplink,
            String prefilledText,
            String reference,
            List<String> attachmentIds,
            Map<String, String> extra
    ) {}

    public static ReportResponse from(IntakeService.IntakeResult result) {
        Classification c = result.classification();
        DispatchOutcome o = result.outcome();
        return new ReportResponse(
                result.report().getId(),
                result.report().getStatus().name(),
                new Analysis(
                        String.valueOf(c.category()),
                        String.valueOf(c.severity()),
                        c.confidence(),
                        c.title(),
                        c.description(),
                        c.location() == null ? null : c.location().asText(),
                        c.missingInformationOrEmpty(),
                        Boolean.TRUE.equals(c.containsThirdPartyPersonalData())),
                new NextStep(
                        o.agencyId(),
                        o.channel() == null ? null : o.channel().name(),
                        o.action().name(),
                        o.citizenMessage(),
                        o.deeplink(),
                        o.prefilledText(),
                        o.reference(),
                        o.attachmentIds(),
                        o.extra()));
    }
}
