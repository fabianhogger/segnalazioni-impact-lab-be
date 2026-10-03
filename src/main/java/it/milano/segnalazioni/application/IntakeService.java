package it.milano.segnalazioni.application;

import it.milano.segnalazioni.application.port.AttachmentStore;
import it.milano.segnalazioni.application.port.Classifier;
import it.milano.segnalazioni.application.port.ReportRepository;
import it.milano.segnalazioni.domain.Agency;
import it.milano.segnalazioni.domain.Attachment;
import it.milano.segnalazioni.domain.Classification;
import it.milano.segnalazioni.domain.DispatchChannel;
import it.milano.segnalazioni.domain.DispatchOutcome;
import it.milano.segnalazioni.domain.Report;
import it.milano.segnalazioni.domain.ReportStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The one pipeline the whole service exists to run: receive, analyse, extract, forward.
 */
@Service
public class IntakeService {

    private static final Logger log = LoggerFactory.getLogger(IntakeService.class);

    private final Classifier classifier;
    private final RoutingService routingService;
    private final DispatchService dispatchService;
    private final ReportRepository reports;
    private final AttachmentStore attachments;
    private final String emergencyNumber;

    public IntakeService(Classifier classifier,
                         RoutingService routingService,
                         DispatchService dispatchService,
                         ReportRepository reports,
                         AttachmentStore attachments,
                         @Value("${segnalazioni.emergency-number:112}") String emergencyNumber) {
        this.classifier = classifier;
        this.routingService = routingService;
        this.dispatchService = dispatchService;
        this.reports = reports;
        this.attachments = attachments;
        this.emergencyNumber = emergencyNumber;
    }

    public record IntakeResult(Report report, Classification classification, DispatchOutcome outcome) {}

    public IntakeResult submit(String text,
                               String citizenContact,
                               Double latitude,
                               Double longitude,
                               List<String> attachmentIds) {

        Report report = reports.save(Report.intake(text, citizenContact, latitude, longitude));
        List<Attachment> photos = resolve(attachmentIds);
        report.setAttachmentIdsCsv(String.join(",", attachmentIds));

        Classification classification = classifier.classify(text, !photos.isEmpty());
        report.applyClassification(classification);
        reports.save(report);

        // Life safety overrides everything, including a confident category. We never put a
        // queue, a mailbox or a model between someone in danger and the emergency number.
        if (classification.isEmergency()) {
            DispatchOutcome outcome = emergencyOutcome(classification);
            report.applyOutcome(outcome);
            reports.save(report);
            log.warn("Report {} classified as emergency; redirected to {}", report.getId(), emergencyNumber);
            return new IntakeResult(report, classification, outcome);
        }

        // An incomplete report filed anyway is a report the body will close as unactionable,
        // so we stop and ask rather than burn the citizen's one submission.
        if (!classification.missingInformationOrEmpty().isEmpty()) {
            DispatchOutcome outcome = needsInfoOutcome(classification);
            report.applyOutcome(outcome);
            reports.save(report);
            return new IntakeResult(report, classification, outcome);
        }

        Agency agency = routingService.route(classification);
        DispatchOutcome outcome;
        try {
            outcome = dispatchService.dispatch(report, classification, agency, photos);
        } catch (RuntimeException e) {
            log.error("Dispatch of report {} to {} failed", report.getId(), agency.id(), e);
            report.fail(e.getMessage());
            reports.save(report);
            throw e;
        }
        report.applyOutcome(outcome);
        reports.save(report);
        return new IntakeResult(report, classification, outcome);
    }

    private List<Attachment> resolve(List<String> ids) {
        return ids.stream()
                .map(attachments::metadata)
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
    }

    private DispatchOutcome emergencyOutcome(Classification c) {
        return new DispatchOutcome(
                ReportStatus.REDIRECTED_TO_EMERGENCY,
                null,
                DispatchChannel.PHONE,
                null,
                "Quanto descrivi sembra un'emergenza. Chiama subito il " + emergencyNumber
                        + ". Questo servizio non inoltra segnalazioni urgenti e nessuno le sta leggendo in questo momento.",
                DispatchOutcome.CitizenAction.CALL_EMERGENCY,
                "tel:" + emergencyNumber,
                null,
                List.of(),
                java.util.Map.of("category", String.valueOf(c.category()))
        );
    }

    private DispatchOutcome needsInfoOutcome(Classification c) {
        String questions = String.join("\n• ", c.missingInformationOrEmpty());
        return new DispatchOutcome(
                ReportStatus.NEEDS_INFO,
                null,
                null,
                null,
                "Per inoltrare la segnalazione servono ancora queste informazioni:\n• " + questions,
                DispatchOutcome.CitizenAction.ANSWER_QUESTIONS,
                null,
                null,
                List.of(),
                java.util.Map.of()
        );
    }
}
