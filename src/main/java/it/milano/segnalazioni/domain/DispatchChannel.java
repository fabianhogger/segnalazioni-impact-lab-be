package it.milano.segnalazioni.domain;

/**
 * How a report physically reaches a body. This is the extension seam of the whole
 * service: today only {@link #EMAIL} can be transmitted by the machine, everything
 * else is handed back to the citizen to send. When a body opens an API, its entry in
 * {@code agencies.yml} changes channel to {@link #API} and a new adapter picks it up —
 * no other code changes.
 */
public enum DispatchChannel {
    /** Machine-to-machine submission. No Milan body offers this yet. */
    API,
    /** A monitored mailbox or PEC address. Transmitted by the service. */
    EMAIL,
    /** A phone number. The citizen calls; the service prepares what to say. */
    PHONE,
    /** A web form, usually behind SPID/CIE. The citizen submits; the service pre-fills. */
    WEB_FORM,
    /** A first-party mobile app, e.g. PULIamo. The citizen submits; the service pre-fills. */
    APP
}
