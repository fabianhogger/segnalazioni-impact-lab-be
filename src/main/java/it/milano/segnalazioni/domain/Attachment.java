package it.milano.segnalazioni.domain;

/**
 * A photo supplied by the citizen, already persisted by the attachment store.
 *
 * @param id          opaque identifier, also the storage key
 * @param filename    original filename, sanitised
 * @param contentType detected MIME type
 * @param sizeBytes   size on disk
 */
public record Attachment(String id, String filename, String contentType, long sizeBytes) {
}
