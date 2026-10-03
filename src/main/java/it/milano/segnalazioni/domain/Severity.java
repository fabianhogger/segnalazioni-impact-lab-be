package it.milano.segnalazioni.domain;

public enum Severity {
    /** Ordinary maintenance backlog: a pothole, a full bin, a broken bench. */
    ROUTINE,
    /** Degrades quickly or blocks people, but nobody is in danger right now. */
    URGENT,
    /** Immediate danger to life, health or property. Never queued: the citizen is told to call 112. */
    EMERGENZA
}
