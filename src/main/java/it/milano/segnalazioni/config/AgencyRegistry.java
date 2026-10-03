package it.milano.segnalazioni.config;

import it.milano.segnalazioni.domain.Agency;
import it.milano.segnalazioni.domain.Category;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory view of the competence table, validated at startup. A category mapped to
 * two bodies, or to none, is a configuration bug we want to hear about on boot rather
 * than in production at 02:00.
 */
@Component
public class AgencyRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgencyRegistry.class);

    private final AgencyProperties properties;
    private final Map<String, Agency> byId = new LinkedHashMap<>();
    private final Map<Category, Agency> byCategory = new EnumMap<>(Category.class);

    public AgencyRegistry(AgencyProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void load() {
        for (AgencyProperties.AgencyEntry entry : properties.getAgencies()) {
            Agency agency = entry.toDomain();
            if (byId.put(agency.id(), agency) != null) {
                throw new IllegalStateException("Duplicate agency id in agencies.yml: " + agency.id());
            }
            for (Category category : agency.categories()) {
                Agency previous = byCategory.put(category, agency);
                if (previous != null) {
                    throw new IllegalStateException(
                            "Category " + category + " is claimed by both " + previous.id()
                                    + " and " + agency.id() + " in agencies.yml");
                }
            }
        }
        if (!byId.containsKey(properties.getFallbackAgencyId())) {
            throw new IllegalStateException("routing.fallback-agency-id points at unknown agency "
                    + properties.getFallbackAgencyId());
        }
        for (Category category : Category.values()) {
            if (category != Category.EMERGENZA && !byCategory.containsKey(category)) {
                log.warn("Category {} has no agency in agencies.yml; it will fall back to {}",
                        category, properties.getFallbackAgencyId());
            }
        }
        log.info("Loaded {} agencies covering {} of {} categories",
                byId.size(), byCategory.size(), Category.values().length);
    }

    public Optional<Agency> forCategory(Category category) {
        return Optional.ofNullable(byCategory.get(category));
    }

    public Optional<Agency> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Agency fallback() {
        return byId.get(properties.getFallbackAgencyId());
    }

    public Agency lowConfidence() {
        return byId.getOrDefault(properties.getLowConfidenceAgencyId(), fallback());
    }

    public Collection<Agency> all() {
        return byId.values();
    }
}
