package it.milano.segnalazioni.adapter.out.agency;

import it.milano.segnalazioni.application.port.AgencyAdapter;
import it.milano.segnalazioni.application.port.AttachmentStore;
import it.milano.segnalazioni.config.DispatchProperties;
import it.milano.segnalazioni.domain.Agency;
import it.milano.segnalazioni.domain.Attachment;
import it.milano.segnalazioni.domain.Classification;
import it.milano.segnalazioni.domain.DispatchChannel;
import it.milano.segnalazioni.domain.DispatchOutcome;
import it.milano.segnalazioni.domain.Report;
import it.milano.segnalazioni.domain.ReportStatus;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The only channel this service can actually transmit over today: a monitored mailbox
 * or PEC address. Used for SOS Affitti, and for any body that gives us an address.
 *
 * <p>Two safety rails, because the recipients are real public inboxes:
 * <ul>
 *   <li><b>dry-run is the default.</b> Nothing leaves the process unless
 *       {@code segnalazioni.dispatch.email.dry-run} is explicitly false.</li>
 *   <li><b>an allowlist.</b> With dry-run off, a recipient that is not in
 *       {@code allowed-recipients} is refused rather than mailed.</li>
 * </ul>
 * Turning both off is a deliberate, reviewable act — see {@code docs/architecture.md}.
 */
@Component
public class EmailAgencyAdapter implements AgencyAdapter {

    private static final Logger log = LoggerFactory.getLogger(EmailAgencyAdapter.class);

    private final JavaMailSender mailSender;
    private final ReportFormatter formatter;
    private final AttachmentStore attachmentStore;
    private final DispatchProperties properties;

    public EmailAgencyAdapter(JavaMailSender mailSender,
                              ReportFormatter formatter,
                              AttachmentStore attachmentStore,
                              DispatchProperties properties) {
        this.mailSender = mailSender;
        this.formatter = formatter;
        this.attachmentStore = attachmentStore;
        this.properties = properties;
    }

    @Override
    public boolean supports(DispatchChannel channel) {
        return channel == DispatchChannel.EMAIL;
    }

    @Override
    public DispatchOutcome dispatch(Report report, Classification classification,
                                    Agency agency, List<Attachment> attachments) {
        String recipient = agency.target();
        DispatchProperties.Email cfg = properties.getEmail();

        if (cfg.isDryRun()) {
            log.info("DRY RUN — not sending report {} to {} <{}>. Subject: {}",
                    report.getId(), agency.displayName(), recipient,
                    formatter.subject(report, classification));
            return sent(agency, attachments, "dry-run:" + UUID.randomUUID(),
                    "La segnalazione e' stata preparata per " + agency.displayName()
                            + ". L'invio reale e' disattivato in questo ambiente.");
        }

        if (!cfg.getAllowedRecipients().contains(recipient)) {
            throw new IllegalStateException(
                    "Refusing to email " + recipient + ": not in segnalazioni.dispatch.email.allowed-recipients. "
                            + "Add it deliberately before sending to a real public mailbox.");
        }

        String messageId = "<" + UUID.randomUUID() + "@" + cfg.getMessageIdDomain() + ">";
        try {
            MimeMessage message = mailSender.createMimeMessage();
            message.setHeader("Message-ID", messageId);
            MimeMessageHelper helper = new MimeMessageHelper(message, !attachments.isEmpty(), "UTF-8");
            helper.setFrom(cfg.getFrom());
            helper.setTo(recipient);
            if (report.getCitizenContact() != null && report.getCitizenContact().contains("@")) {
                helper.setReplyTo(report.getCitizenContact());
            }
            helper.setSubject(formatter.subject(report, classification));
            helper.setText(formatter.body(report, classification, agency, attachments), false);

            for (Attachment a : attachments) {
                try (InputStream in = attachmentStore.open(a.id()).orElse(null)) {
                    if (in == null) continue;
                    byte[] bytes = in.readAllBytes();
                    helper.addAttachment(a.filename(),
                            () -> new java.io.ByteArrayInputStream(bytes), a.contentType());
                }
            }

            mailSender.send(message);
            log.info("Report {} emailed to {} <{}> with Message-ID {}",
                    report.getId(), agency.id(), recipient, messageId);
            return sent(agency, attachments, messageId,
                    "La segnalazione e' stata inviata a " + agency.displayName() + ".");

        } catch (IOException | jakarta.mail.MessagingException e) {
            throw new IllegalStateException("Could not email report " + report.getId()
                    + " to " + recipient, e);
        }
    }

    private DispatchOutcome sent(Agency agency, List<Attachment> attachments,
                                 String reference, String message) {
        String sla = agency.slaDays() == null ? ""
                : " L'ente dichiara di rispondere entro " + agency.slaDays() + " giorni.";
        return new DispatchOutcome(
                ReportStatus.SENT,
                agency.id(),
                DispatchChannel.EMAIL,
                reference,
                message + sla,
                DispatchOutcome.CitizenAction.NONE,
                null,
                null,
                attachments.stream().map(Attachment::id).toList(),
                Map.of("recipient", agency.target())
        );
    }
}
