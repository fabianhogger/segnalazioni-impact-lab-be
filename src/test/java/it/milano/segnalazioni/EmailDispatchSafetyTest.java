package it.milano.segnalazioni;

import it.milano.segnalazioni.adapter.out.agency.EmailAgencyAdapter;
import it.milano.segnalazioni.adapter.out.agency.ReportFormatter;
import it.milano.segnalazioni.application.port.AttachmentStore;
import it.milano.segnalazioni.config.DispatchProperties;
import it.milano.segnalazioni.domain.Agency;
import it.milano.segnalazioni.domain.Category;
import it.milano.segnalazioni.domain.Classification;
import it.milano.segnalazioni.domain.DispatchChannel;
import it.milano.segnalazioni.domain.LocationHint;
import it.milano.segnalazioni.domain.Report;
import it.milano.segnalazioni.domain.Severity;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The guard rails on the one channel that can actually reach a public mailbox.
 * If either of these tests is ever deleted, the service can mail the Comune by accident.
 */
class EmailDispatchSafetyTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final AttachmentStore attachments = mock(AttachmentStore.class);
    private final ReportFormatter formatter = new ReportFormatter();

    private static final Agency REAL_BODY = new Agency(
            "comune-sos-affitti", "SOS Affitti", DispatchChannel.EMAIL,
            "SOSaffitti.segnalazioni@comune.milano.it",
            List.of(Category.AFFITTI_IRREGOLARI), null, null, null, false, Map.of());

    private static final Classification CLASSIFICATION = new Classification(
            Category.AFFITTI_IRREGOLARI, 0.9, Severity.ROUTINE, "Affitto irregolare",
            "Descrizione.", new LocationHint("Via Padova", null, null, null), List.of(), false);

    @Test
    void dry_run_sends_nothing_at_all() {
        DispatchProperties props = new DispatchProperties();
        props.getEmail().setDryRun(true);

        var adapter = new EmailAgencyAdapter(mailSender, formatter, attachments, props);
        var outcome = adapter.dispatch(Report.intake("testo", null, null, null),
                CLASSIFICATION, REAL_BODY, List.of());

        assertThat(outcome.reference()).startsWith("dry-run:");
        verifyNoInteractions(mailSender);
    }

    @Test
    void a_recipient_outside_the_allowlist_is_refused_not_mailed() {
        DispatchProperties props = new DispatchProperties();
        props.getEmail().setDryRun(false);
        props.getEmail().setAllowedRecipients(List.of("someone-else@example.org"));

        var adapter = new EmailAgencyAdapter(mailSender, formatter, attachments, props);

        assertThatThrownBy(() -> adapter.dispatch(Report.intake("testo", null, null, null),
                CLASSIFICATION, REAL_BODY, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allowed-recipients");

        verifyNoInteractions(mailSender);
    }
}
