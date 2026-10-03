package it.milano.segnalazioni.domain;

import java.util.List;
import java.util.Map;

/**
 * A body that can receive reports. Instances come from {@code agencies.yml}, never
 * from code, so that the Comune can hand us a corrected competence table and we can
 * apply it without touching Java.
 *
 * @param id            stable slug used in URLs and logs, e.g. {@code amsa}
 * @param displayName   name shown to the citizen
 * @param channel       how a report reaches this body today
 * @param target        the address the channel needs: an email, a phone number or a form URL
 * @param categories    the categories this body is competent for
 * @param instructions  Italian text shown to the citizen when they must submit themselves
 * @param hours         human-readable service hours, shown alongside a phone number
 * @param slaDays       the body's own stated response time, if it publishes one
 * @param requiresPhoto whether the channel rejects reports without a photo
 * @param metadata      free-form extras an adapter may need (API base URL, form field ids…)
 */
public record Agency(
        String id,
        String displayName,
        DispatchChannel channel,
        String target,
        List<Category> categories,
        String instructions,
        String hours,
        Integer slaDays,
        boolean requiresPhoto,
        Map<String, String> metadata
) {
    public Map<String, String> metadataOrEmpty() {
        return metadata == null ? Map.of() : metadata;
    }
}
