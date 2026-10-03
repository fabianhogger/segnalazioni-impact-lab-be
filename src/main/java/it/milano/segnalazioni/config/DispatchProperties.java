package it.milano.segnalazioni.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "segnalazioni.dispatch")
public class DispatchProperties {

    private Email email = new Email();

    public Email getEmail() { return email; }
    public void setEmail(Email email) { this.email = email; }

    public static class Email {
        /**
         * Nothing is actually sent while this is true. It defaults to true so that a fresh
         * checkout, a test run or a demo cannot deliver mail to a real public body.
         */
        private boolean dryRun = true;

        /** With dry-run off, only these recipients may be mailed. */
        private List<String> allowedRecipients = List.of();

        private String from = "segnalazioni@example.org";

        private String messageIdDomain = "segnalazioni.example.org";

        public boolean isDryRun() { return dryRun; }
        public void setDryRun(boolean v) { this.dryRun = v; }
        public List<String> getAllowedRecipients() { return allowedRecipients; }
        public void setAllowedRecipients(List<String> v) { this.allowedRecipients = v; }
        public String getFrom() { return from; }
        public void setFrom(String v) { this.from = v; }
        public String getMessageIdDomain() { return messageIdDomain; }
        public void setMessageIdDomain(String v) { this.messageIdDomain = v; }
    }
}
