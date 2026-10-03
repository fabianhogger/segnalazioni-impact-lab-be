package it.milano.segnalazioni.application;

import it.milano.segnalazioni.config.AgencyRegistry;
import it.milano.segnalazioni.domain.Agency;
import it.milano.segnalazioni.domain.Classification;
import org.springframework.stereotype.Service;

/**
 * Chooses which body a classified report belongs to. Kept separate from classification
 * so that the competence table can be argued about with the Comune without anyone
 * touching the model prompt.
 */
@Service
public class RoutingService {

    private final AgencyRegistry registry;

    public RoutingService(AgencyRegistry registry) {
        this.registry = registry;
    }

    public Agency route(Classification classification) {
        if (!classification.isConfident()) {
            return registry.lowConfidence();
        }
        return registry.forCategory(classification.category()).orElseGet(registry::fallback);
    }
}
