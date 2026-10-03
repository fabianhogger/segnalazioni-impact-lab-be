package it.milano.segnalazioni.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * The case record. One row per citizen report, from intake to whatever the outcome was.
 *
 * <p>It is deliberately a flat, boring table: the value of this service to the Comune is
 * the audit trail — what was reported, how it was classified, where it was sent, and
 * whether a human had to finish the job. That trail is the evidence base for the
 * conversation in {@code docs/municipality-handoff.md}.
 */
@Entity
@Table(name = "reports")
public class Report {

    @Id
    @Column(length = 36)
    private String id;

    private Instant createdAt;
    private Instant updatedAt;

    @Lob
    @Column(nullable = false)
    private String rawText;

    private String citizenContact;
    private Double latitude;
    private Double longitude;

    @Enumerated(EnumType.STRING)
    private ReportStatus status;

    @Enumerated(EnumType.STRING)
    private Category category;

    @Enumerated(EnumType.STRING)
    private Severity severity;

    private Double confidence;
    private String title;

    @Lob
    private String description;

    private String locationText;
    private String agencyId;

    @Enumerated(EnumType.STRING)
    private DispatchChannel channel;

    private String dispatchReference;

    @Lob
    private String attachmentIdsCsv;

    @Lob
    private String failureReason;

    protected Report() {
    }

    public static Report intake(String rawText, String citizenContact, Double lat, Double lon) {
        Report r = new Report();
        r.id = UUID.randomUUID().toString();
        r.createdAt = Instant.now();
        r.updatedAt = r.createdAt;
        r.rawText = rawText;
        r.citizenContact = citizenContact;
        r.latitude = lat;
        r.longitude = lon;
        r.status = ReportStatus.RECEIVED;
        return r;
    }

    public void applyClassification(Classification c) {
        this.category = c.category();
        this.severity = c.severity();
        this.confidence = c.confidence();
        this.title = c.title();
        this.description = c.description();
        this.locationText = c.location() == null ? null : c.location().asText();
        this.status = ReportStatus.CLASSIFIED;
        this.updatedAt = Instant.now();
    }

    public void applyOutcome(DispatchOutcome outcome) {
        this.status = outcome.status();
        this.agencyId = outcome.agencyId();
        this.channel = outcome.channel();
        this.dispatchReference = outcome.reference();
        this.updatedAt = Instant.now();
    }

    public void fail(String reason) {
        this.status = ReportStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public void setAttachmentIdsCsv(String csv) {
        this.attachmentIdsCsv = csv;
    }

    public String getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getRawText() { return rawText; }
    public String getCitizenContact() { return citizenContact; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public ReportStatus getStatus() { return status; }
    public Category getCategory() { return category; }
    public Severity getSeverity() { return severity; }
    public Double getConfidence() { return confidence; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getLocationText() { return locationText; }
    public String getAgencyId() { return agencyId; }
    public DispatchChannel getChannel() { return channel; }
    public String getDispatchReference() { return dispatchReference; }
    public String getAttachmentIdsCsv() { return attachmentIdsCsv; }
    public String getFailureReason() { return failureReason; }
}
