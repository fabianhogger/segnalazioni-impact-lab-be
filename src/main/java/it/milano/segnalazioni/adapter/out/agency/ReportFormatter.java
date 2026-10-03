package it.milano.segnalazioni.adapter.out.agency;

import it.milano.segnalazioni.domain.Agency;
import it.milano.segnalazioni.domain.Attachment;
import it.milano.segnalazioni.domain.Classification;
import it.milano.segnalazioni.domain.Report;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Renders a report as the plain Italian text a public body actually reads — the same
 * body for an email, a form paste and a phone script, so the citizen and the clerk see
 * the same words whichever channel the report ends up taking.
 */
@Component
public class ReportFormatter {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Europe/Rome"));

    public String subject(Report report, Classification classification) {
        return "[Segnalazione %s] %s".formatted(
                report.getId().substring(0, 8), classification.title());
    }

    public String body(Report report, Classification classification, Agency agency,
                       List<Attachment> attachments) {
        StringBuilder sb = new StringBuilder();
        sb.append("Segnalazione inviata da un cittadino tramite il servizio Segnalazioni AI.\n\n");
        sb.append("Riferimento: ").append(report.getId()).append('\n');
        sb.append("Data e ora: ").append(TS.format(report.getCreatedAt())).append('\n');
        sb.append("Categoria: ").append(classification.category()).append('\n');
        sb.append("Urgenza dichiarata: ").append(classification.severity()).append("\n\n");

        sb.append("LUOGO\n");
        sb.append(classification.location() == null
                ? "non specificato" : classification.location().asText()).append('\n');
        if (report.getLatitude() != null && report.getLongitude() != null) {
            sb.append("Coordinate: ").append(report.getLatitude())
              .append(", ").append(report.getLongitude()).append('\n');
        }

        sb.append("\nDESCRIZIONE\n").append(classification.description()).append('\n');

        sb.append("\nTESTO ORIGINALE DEL CITTADINO\n").append(report.getRawText()).append('\n');

        if (!attachments.isEmpty()) {
            sb.append("\nALLEGATI (").append(attachments.size()).append(")\n");
            for (Attachment a : attachments) {
                sb.append("- ").append(a.filename())
                  .append(" (").append(a.contentType()).append(")\n");
            }
        }

        if (report.getCitizenContact() != null && !report.getCitizenContact().isBlank()) {
            sb.append("\nCONTATTO DEL SEGNALANTE\n").append(report.getCitizenContact()).append('\n');
        }

        sb.append("\n---\n");
        sb.append("Ente destinatario: ").append(agency.displayName()).append('\n');
        sb.append("Questa segnalazione e' stata classificata automaticamente. ");
        sb.append("In caso di competenza errata, rispondete a questo messaggio indicando ");
        sb.append("l'ente corretto: la correzione viene usata per migliorare lo smistamento.\n");
        return sb.toString();
    }

    /** The compact version a citizen reads out on the phone or pastes into a form. */
    public String compact(Report report, Classification classification) {
        StringBuilder sb = new StringBuilder();
        sb.append(classification.title()).append(".\n");
        sb.append("Luogo: ").append(classification.location() == null
                ? "non specificato" : classification.location().asText()).append(".\n");
        sb.append(classification.description()).append('\n');
        sb.append("Riferimento interno: ").append(report.getId().substring(0, 8)).append('\n');
        return sb.toString();
    }
}
