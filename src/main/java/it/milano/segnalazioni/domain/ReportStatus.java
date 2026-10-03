package it.milano.segnalazioni.domain;

public enum ReportStatus {
    RECEIVED,
    CLASSIFIED,
    /** Needs something from the citizen before it can go anywhere. */
    NEEDS_INFO,
    /** Routed to a channel the service cannot transmit; the citizen must send it. */
    AWAITING_CITIZEN_ACTION,
    /** Transmitted by the service and acknowledged by the transport. */
    SENT,
    /** Short-circuited: the citizen was told to call the emergency number. */
    REDIRECTED_TO_EMERGENCY,
    FAILED
}
