package it.milano.segnalazioni;

import it.milano.segnalazioni.application.IntakeService;
import it.milano.segnalazioni.application.port.Classifier;
import it.milano.segnalazioni.config.AgencyRegistry;
import it.milano.segnalazioni.domain.Category;
import it.milano.segnalazioni.domain.Classification;
import it.milano.segnalazioni.domain.DispatchChannel;
import it.milano.segnalazioni.domain.DispatchOutcome;
import it.milano.segnalazioni.domain.LocationHint;
import it.milano.segnalazioni.domain.ReportStatus;
import it.milano.segnalazioni.domain.Severity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The classifier is stubbed on purpose: these tests are about routing and dispatch
 * behaviour, which must be deterministic. Model quality is a separate concern and
 * belongs in an eval, not in the unit suite.
 */
@SpringBootTest
class RoutingPipelineTest {

    @Autowired
    IntakeService intakeService;

    @Autowired
    AgencyRegistry registry;

    @MockitoBean
    Classifier classifier;

    private static Classification classification(Category category, Severity severity,
                                                 double confidence, List<String> missing) {
        return new Classification(category, confidence, severity,
                "Titolo di prova",
                "Descrizione neutra del problema.",
                new LocationHint("Via Paolo Sarpi", "12", null, 1),
                missing, false);
    }

    private void stub(Classification c) {
        when(classifier.classify(anyString(), anyBoolean())).thenReturn(c);
    }

    @Test
    void waste_reports_are_routed_to_amsa_as_an_assisted_phone_handoff() {
        stub(classification(Category.RIFIUTI, Severity.ROUTINE, 0.93, List.of()));

        var result = intakeService.submit("Cassonetti strapieni in Via Paolo Sarpi 12",
                null, null, null, List.of());

        assertThat(result.outcome().agencyId()).isEqualTo("amsa");
        assertThat(result.outcome().channel()).isEqualTo(DispatchChannel.PHONE);
        assertThat(result.outcome().action()).isEqualTo(DispatchOutcome.CitizenAction.CALL);
        assertThat(result.outcome().deeplink()).isEqualTo("tel:800332299");
        assertThat(result.outcome().prefilledText()).contains("Via Paolo Sarpi 12");
        assertThat(result.report().getStatus()).isEqualTo(ReportStatus.AWAITING_CITIZEN_ACTION);
    }

    @Test
    void irregular_rentals_are_transmitted_by_the_service_over_email() {
        stub(classification(Category.AFFITTI_IRREGOLARI, Severity.ROUTINE, 0.88, List.of()));

        var result = intakeService.submit("Mi chiedono affitto in nero per un bilocale in Via Padova",
                "cittadino@example.org", null, null, List.of());

        assertThat(result.outcome().agencyId()).isEqualTo("comune-sos-affitti");
        assertThat(result.outcome().channel()).isEqualTo(DispatchChannel.EMAIL);
        assertThat(result.outcome().action()).isEqualTo(DispatchOutcome.CitizenAction.NONE);
        assertThat(result.report().getStatus()).isEqualTo(ReportStatus.SENT);
        // Dry run is on by default, and the reference says so rather than faking a Message-ID.
        assertThat(result.outcome().reference()).startsWith("dry-run:");
    }

    @Test
    void an_emergency_is_never_queued_and_never_reaches_an_agency() {
        stub(classification(Category.STRADE, Severity.EMERGENZA, 0.95, List.of()));

        var result = intakeService.submit("Cavo elettrico scoperto, una persona e' a terra",
                null, null, null, List.of());

        assertThat(result.report().getStatus()).isEqualTo(ReportStatus.REDIRECTED_TO_EMERGENCY);
        assertThat(result.outcome().agencyId()).isNull();
        assertThat(result.outcome().action()).isEqualTo(DispatchOutcome.CitizenAction.CALL_EMERGENCY);
        assertThat(result.outcome().deeplink()).isEqualTo("tel:112");
    }

    @Test
    void an_incomplete_report_stops_and_asks_rather_than_being_filed() {
        stub(new Classification(Category.STRADE, 0.9, Severity.ROUTINE, "Buca", "C'e' una buca.",
                new LocationHint(null, null, "vicino alla scuola", null),
                List.of("In quale via si trova la buca?"), false));

        var result = intakeService.submit("C'e' una buca vicino alla scuola",
                null, null, null, List.of());

        assertThat(result.report().getStatus()).isEqualTo(ReportStatus.NEEDS_INFO);
        assertThat(result.outcome().action()).isEqualTo(DispatchOutcome.CitizenAction.ANSWER_QUESTIONS);
        assertThat(result.outcome().citizenMessage()).contains("In quale via");
    }

    @Test
    void a_low_confidence_classification_goes_to_a_human_rather_than_a_guessed_agency() {
        stub(classification(Category.CIMITERI, Severity.ROUTINE, 0.31, List.of()));

        var result = intakeService.submit("Non so bene a chi scrivere, c'e' una cosa strana",
                null, null, null, List.of());

        assertThat(result.outcome().agencyId()).isEqualTo(registry.lowConfidence().id());
        assertThat(result.outcome().agencyId()).isNotEqualTo("comune-portale");
    }

    @Test
    void every_category_except_emergency_resolves_to_a_reachable_agency() {
        for (Category category : Category.values()) {
            if (category == Category.EMERGENZA) continue;
            var agency = registry.forCategory(category).orElseGet(registry::fallback);
            assertThat(agency).as("category %s", category).isNotNull();
            assertThat(agency.target()).as("target for %s", category).isNotBlank();
        }
    }
}
