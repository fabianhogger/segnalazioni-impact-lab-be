package it.milano.segnalazioni.adapter.out.agency;

import it.milano.segnalazioni.application.port.AgencyAdapter;
import it.milano.segnalazioni.domain.Agency;
import it.milano.segnalazioni.domain.Attachment;
import it.milano.segnalazioni.domain.Classification;
import it.milano.segnalazioni.domain.DispatchChannel;
import it.milano.segnalazioni.domain.DispatchOutcome;
import it.milano.segnalazioni.domain.Report;
import it.milano.segnalazioni.domain.ReportStatus;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * For every body that has no machine interface — the Comune portal behind SPID, AMSA's
 * toll-free number and PULIamo app, Unareti's emergency line, ATM's web form.
 *
 * <p>The service does the work it legitimately can: picks the right body, writes the
 * report in the form that body expects, and hands the citizen a one-tap action. The
 * citizen presses send. We do not automate a SPID session, replay a private app's
 * endpoints, or submit a form on someone else's behalf — those are the lines described
 * in {@code docs/architecture.md}, and they are the reason the handoff document exists.
 */
@Component
public class AssistedHandoffAdapter implements AgencyAdapter {

    private final ReportFormatter formatter;

    public AssistedHandoffAdapter(ReportFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public boolean supports(DispatchChannel channel) {
        return channel == DispatchChannel.PHONE
                || channel == DispatchChannel.WEB_FORM
                || channel == DispatchChannel.APP;
    }

    @Override
    public DispatchOutcome dispatch(Report report, Classification classification,
                                    Agency agency, List<Attachment> attachments) {

        DispatchOutcome.CitizenAction action = switch (agency.channel()) {
            case PHONE -> DispatchOutcome.CitizenAction.CALL;
            case WEB_FORM -> DispatchOutcome.CitizenAction.SUBMIT_FORM;
            case APP -> DispatchOutcome.CitizenAction.USE_APP;
            default -> throw new IllegalStateException("Unsupported channel " + agency.channel());
        };

        String deeplink = switch (agency.channel()) {
            case PHONE -> "tel:" + agency.target().replaceAll("[^0-9+]", "");
            case WEB_FORM, APP -> agency.target();
            default -> null;
        };

        StringBuilder message = new StringBuilder();
        message.append("Questa segnalazione e' di competenza di ").append(agency.displayName()).append(". ");
        message.append(switch (agency.channel()) {
            case PHONE -> "Chiama il " + agency.target() + ".";
            case WEB_FORM -> "Va inviata dal modulo online dell'ente.";
            case APP -> "Va inviata dall'app dell'ente.";
            default -> "";
        });
        if (agency.hours() != null) {
            message.append(" Orario: ").append(agency.hours()).append('.');
        }
        if (agency.instructions() != null) {
            message.append(' ').append(agency.instructions());
        }
        if (agency.requiresPhoto() && attachments.isEmpty()) {
            message.append(" Attenzione: questo ente richiede almeno una foto.");
        }
        if (agency.slaDays() != null) {
            message.append(" Tempo di risposta dichiarato: ").append(agency.slaDays()).append(" giorni.");
        }

        Map<String, String> extra = new HashMap<>(agency.metadataOrEmpty());
        extra.put("target", agency.target());

        return new DispatchOutcome(
                ReportStatus.AWAITING_CITIZEN_ACTION,
                agency.id(),
                agency.channel(),
                null,
                message.toString(),
                action,
                deeplink,
                formatter.compact(report, classification),
                attachments.stream().map(Attachment::id).toList(),
                Map.copyOf(extra)
        );
    }
}
