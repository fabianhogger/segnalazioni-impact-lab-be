package it.milano.segnalazioni.adapter.out.llm;

import it.milano.segnalazioni.application.port.Classifier;
import it.milano.segnalazioni.domain.Category;
import it.milano.segnalazioni.domain.Classification;
import it.milano.segnalazioni.domain.LocationHint;
import it.milano.segnalazioni.domain.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keyword fallback used when no Anthropic API key is configured.
 *
 * <p>It exists so the service boots, the tests run and the walkthrough in
 * {@code docs/municipality-handoff.md} can be demonstrated without credentials or spend.
 * It is not a product: it reports low confidence on purpose, which routes nearly
 * everything to the human fallback. Do not let it reach production — the startup log
 * says so too.
 */
public class HeuristicClassifier implements Classifier {

    private static final Logger log = LoggerFactory.getLogger(HeuristicClassifier.class);

    // The case-insensitive flag deliberately covers only the street-type word. Applying it
    // to the whole pattern lets [A-Z] match lowercase, and "incendio in corso" parses as a
    // street named "in un cassonetto...".
    private static final Pattern STREET = Pattern.compile(
            "\\b(?i:via|viale|corso|piazza|piazzale|largo|vicolo|bastioni|ripa|alzaia)\\s+"
                    + "([A-Z][\\p{L}'.\\-]*(?: [A-Z][\\p{L}'.\\-]*){0,3})");
    private static final Pattern CIVIC = Pattern.compile("\\b(?:n\\.?|civico)?\\s*(\\d{1,4}(?:/[A-Za-z])?)\\b");

    // Station names are proper nouns or line codes, so the words after the keyword must start
    // with a capital or a digit: that stops "stazione M3 Lodi e' rotta" swallowing the verb.
    private static final Pattern STATION = Pattern.compile(
            "\\b(?i:stazione|fermata|capolinea|metropolitana|metro)\\s+"
                    + "(?:[A-Z0-9][\\p{L}0-9'.\\-]*)(?: [A-Z0-9][\\p{L}0-9'.\\-]*){0,2}"
                    + "|\\bM[1-5]\\b");

    private static final List<String> EMERGENCY_WORDS = List.of(
            "incendio", "fiamme", "fuga di gas", "esplosione", "crollo", "crollato",
            "ferito", "ferita", "sangue", "folgorazione", "cavo scoperto", "allagamento");

    private static final Map<Category, List<String>> KEYWORDS = new LinkedHashMap<>();

    static {
        KEYWORDS.put(Category.RIFIUTI, List.of("rifiut", "cassonett", "immondizia", "discarica",
                "ingombrant", "siringh", "spazzatura", "raccolta"));
        KEYWORDS.put(Category.STRADE, List.of("buca", "buche", "asfalto", "marciapiede", "tombino",
                "segnaletica", "dissest", "voragine"));
        KEYWORDS.put(Category.ILLUMINAZIONE_PUBBLICA, List.of("lampione", "illuminazione", "luce spenta",
                "lampioni"));
        KEYWORDS.put(Category.GUASTO_ELETTRICO, List.of("blackout", "black out", "corrente", "senza luce",
                "contatore", "cabina elettrica"));
        // "stazione", "banchina" and "scala mobile" are transit-specific; bare "ascensore" is not,
        // so a broken lift in a block of flats is not mistaken for an ATM asset.
        KEYWORDS.put(Category.TRASPORTO_PUBBLICO, List.of("tram", "metro", "autobus", "bus ", "atm",
                "bikemi", "fermata", "linea ", "stazione", "capolinea", "banchina", "scala mobile"));
        KEYWORDS.put(Category.VERDE_PUBBLICO, List.of("albero", "alberi", "ramo", "potatur", "parco",
                "aiuola", "siepe"));
        KEYWORDS.put(Category.VEICOLI_ABBANDONATI, List.of("auto abbandonat", "veicolo abbandonat",
                "carcassa", "relitto"));
        KEYWORDS.put(Category.RUMORE, List.of("rumore", "schiamazz", "musica alta", "molest"));
        KEYWORDS.put(Category.AFFITTI_IRREGOLARI, List.of("affitto in nero", "subaffitt", "affitto irregolare",
                "sfratto", "canone"));
        KEYWORDS.put(Category.PERSONE_SENZA_DIMORA, List.of("senzatetto", "senza dimora", "dorme per strada",
                "clochard"));
        KEYWORDS.put(Category.AMBIENTE, List.of("inquinament", "sversament", "amianto", "puzza", "odore"));
        KEYWORDS.put(Category.ARREDO_URBANO, List.of("panchina", "giochi", "altalena", "cestino", "fontanella",
                "vandal"));
        KEYWORDS.put(Category.PULIZIA_STRADE, List.of("sporc", "pulizia", "deiezioni", "foglie"));
        KEYWORDS.put(Category.CIMITERI, List.of("cimiter", "loculo", "tomba"));
        KEYWORDS.put(Category.SICUREZZA_NON_URGENTE, List.of("sosta selvaggia", "parcheggiat", "degrado",
                "vigili"));
    }

    public HeuristicClassifier() {
        log.warn("No ANTHROPIC_API_KEY configured — falling back to keyword classification. "
                + "This is a development stand-in and must not be used in production.");
    }

    @Override
    public Classification classify(String text, boolean hasPhotos) {
        String lower = text.toLowerCase(Locale.ITALIAN);

        boolean emergency = EMERGENCY_WORDS.stream().anyMatch(lower::contains);

        Category category = Category.ALTRO;
        double confidence = 0.2;
        for (Map.Entry<Category, List<String>> e : KEYWORDS.entrySet()) {
            List<String> matched = e.getValue().stream().filter(lower::contains).toList();
            if (!matched.isEmpty()) {
                category = e.getKey();
                // A multi-word phrase such as "affitto in nero" is far more diagnostic than a
                // single stem such as "parco", so it is worth more.
                boolean distinctive = matched.stream().anyMatch(k -> k.strip().contains(" "));
                confidence = Math.min(0.85,
                        0.45 + 0.15 * matched.size() + (distinctive ? 0.15 : 0.0));
                break;
            }
        }

        // ATM identifies its assets by station, not by street address, so a named stop is a
        // sufficient location for a transit report. Demanding a street here would bounce
        // exactly the reports ATM is able to act on.
        LocationHint location = extractLocation(text);
        if (category == Category.TRASPORTO_PUBBLICO && !location.isPreciseEnoughForDispatch()) {
            Matcher station = STATION.matcher(text);
            if (station.find()) {
                location = new LocationHint(null, null, station.group().replaceAll("\\s+", " ").strip(), null);
            }
        }

        List<String> missing = new ArrayList<>();
        if (!location.isPreciseEnoughForDispatch() && location.landmark() == null) {
            missing.add("In quale via o piazza si trova il problema?");
        }

        String title = text.length() <= 70 ? text.strip() : text.strip().substring(0, 67) + "...";

        return new Classification(
                emergency ? Category.EMERGENZA : category,
                confidence,
                emergency ? Severity.EMERGENZA : Severity.ROUTINE,
                title.replace('\n', ' '),
                text.strip(),
                location,
                missing,
                false);
    }

    private LocationHint extractLocation(String text) {
        Matcher street = STREET.matcher(text);
        if (!street.find()) {
            return new LocationHint(null, null, null, null);
        }
        String name = street.group().replaceAll("\\s+", " ").strip();
        String tail = text.substring(street.end());
        Matcher civic = CIVIC.matcher(tail.length() > 12 ? tail.substring(0, 12) : tail);
        String number = civic.find() ? civic.group(1) : null;
        return new LocationHint(name, number, null, null);
    }
}
