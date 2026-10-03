package it.milano.segnalazioni;

import it.milano.segnalazioni.adapter.out.llm.HeuristicClassifier;
import it.milano.segnalazioni.domain.Category;
import it.milano.segnalazioni.domain.Severity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicClassifierTest {

    private final HeuristicClassifier classifier = new HeuristicClassifier();

    @Test
    void it_extracts_a_street_and_civic_number() {
        var c = classifier.classify("Cassonetti strapieni in Via Paolo Sarpi 12 da tre giorni", false);
        assertThat(c.category()).isEqualTo(Category.RIFIUTI);
        assertThat(c.location().street()).isEqualTo("Via Paolo Sarpi");
        assertThat(c.location().civicNumber()).isEqualTo("12");
        assertThat(c.missingInformationOrEmpty()).isEmpty();
    }

    @Test
    void it_asks_for_a_street_when_none_was_given() {
        var c = classifier.classify("C'e' una buca enorme vicino alla scuola", false);
        assertThat(c.missingInformationOrEmpty()).isNotEmpty();
    }

    @Test
    void a_named_station_is_location_enough_for_a_transit_report() {
        var c = classifier.classify("L'ascensore della stazione M3 Lodi e' rotto da giorni", false);
        assertThat(c.category()).isEqualTo(Category.TRASPORTO_PUBBLICO);
        assertThat(c.missingInformationOrEmpty()).isEmpty();
        assertThat(c.location().landmark()).contains("Lodi");
    }

    @Test
    void a_station_name_does_not_excuse_a_missing_street_in_other_categories() {
        var c = classifier.classify("Cassonetti strapieni vicino alla stazione M3 Lodi", false);
        assertThat(c.category()).isEqualTo(Category.RIFIUTI);
        assertThat(c.missingInformationOrEmpty()).isNotEmpty();
    }

    @Test
    void it_flags_danger_words_as_an_emergency() {
        var c = classifier.classify("Incendio in corso al piano terra", false);
        assertThat(c.severity()).isEqualTo(Severity.EMERGENZA);
        assertThat(c.isEmergency()).isTrue();
    }

    @Test
    void text_it_cannot_place_stays_below_the_routing_threshold_and_reaches_a_human() {
        var c = classifier.classify("Volevo segnalare una cosa strana che ho visto ieri", false);
        assertThat(c.category()).isEqualTo(Category.ALTRO);
        assertThat(c.confidence()).isLessThan(0.6);
    }

    @Test
    void a_distinctive_phrase_clears_the_routing_threshold() {
        var c = classifier.classify("Mi chiedono un affitto in nero in Via Padova 40", false);
        assertThat(c.category()).isEqualTo(Category.AFFITTI_IRREGOLARI);
        assertThat(c.confidence()).isGreaterThanOrEqualTo(0.6);
    }
}
