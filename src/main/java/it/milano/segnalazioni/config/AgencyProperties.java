package it.milano.segnalazioni.config;

import it.milano.segnalazioni.domain.Agency;
import it.milano.segnalazioni.domain.Category;
import it.milano.segnalazioni.domain.DispatchChannel;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Binds {@code agencies.yml}. The competence table is configuration, not code.
 */
@ConfigurationProperties(prefix = "routing")
public class AgencyProperties {

    /** Body used when no category maps anywhere, typically the 020202 information line. */
    private String fallbackAgencyId = "comune-020202";

    /** Body used when the classifier is not confident enough to route. */
    private String lowConfidenceAgencyId = "comune-020202";

    private List<AgencyEntry> agencies = List.of();

    public String getFallbackAgencyId() { return fallbackAgencyId; }
    public void setFallbackAgencyId(String v) { this.fallbackAgencyId = v; }

    public String getLowConfidenceAgencyId() { return lowConfidenceAgencyId; }
    public void setLowConfidenceAgencyId(String v) { this.lowConfidenceAgencyId = v; }

    public List<AgencyEntry> getAgencies() { return agencies; }
    public void setAgencies(List<AgencyEntry> v) { this.agencies = v; }

    public static class AgencyEntry {
        private String id;
        private String displayName;
        private DispatchChannel channel;
        private String target;
        private List<Category> categories = List.of();
        private String instructions;
        private String hours;
        private Integer slaDays;
        private boolean requiresPhoto;
        private Map<String, String> metadata = Map.of();

        public Agency toDomain() {
            return new Agency(id, displayName, channel, target, categories,
                    instructions, hours, slaDays, requiresPhoto, metadata);
        }

        public String getId() { return id; }
        public void setId(String v) { this.id = v; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String v) { this.displayName = v; }
        public DispatchChannel getChannel() { return channel; }
        public void setChannel(DispatchChannel v) { this.channel = v; }
        public String getTarget() { return target; }
        public void setTarget(String v) { this.target = v; }
        public List<Category> getCategories() { return categories; }
        public void setCategories(List<Category> v) { this.categories = v; }
        public String getInstructions() { return instructions; }
        public void setInstructions(String v) { this.instructions = v; }
        public String getHours() { return hours; }
        public void setHours(String v) { this.hours = v; }
        public Integer getSlaDays() { return slaDays; }
        public void setSlaDays(Integer v) { this.slaDays = v; }
        public boolean isRequiresPhoto() { return requiresPhoto; }
        public void setRequiresPhoto(boolean v) { this.requiresPhoto = v; }
        public Map<String, String> getMetadata() { return metadata; }
        public void setMetadata(Map<String, String> v) { this.metadata = v; }
    }
}
