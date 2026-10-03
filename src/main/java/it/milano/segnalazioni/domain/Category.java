package it.milano.segnalazioni.domain;

/**
 * The report categories the service can recognise. Each one is mapped to exactly one
 * responsible body in {@code agencies.yml}; the mapping lives in configuration rather
 * than in code so that it can be corrected without a release when the Comune
 * reorganises a competence.
 */
public enum Category {
    STRADE,
    PULIZIA_STRADE,
    RIFIUTI,
    ARREDO_URBANO,
    VERDE_PUBBLICO,
    CIMITERI,
    VEICOLI_ABBANDONATI,
    ILLUMINAZIONE_PUBBLICA,
    GUASTO_ELETTRICO,
    TRASPORTO_PUBBLICO,
    RUMORE,
    AMBIENTE,
    SICUREZZA_NON_URGENTE,
    PERSONE_SENZA_DIMORA,
    AFFITTI_IRREGOLARI,
    EMERGENZA,
    ALTRO
}
