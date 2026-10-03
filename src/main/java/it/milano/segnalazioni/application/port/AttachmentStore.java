package it.milano.segnalazioni.application.port;

import it.milano.segnalazioni.domain.Attachment;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public interface AttachmentStore {

    Attachment store(String filename, String contentType, byte[] content) throws IOException;

    Optional<Attachment> metadata(String id);

    Optional<InputStream> open(String id) throws IOException;
}
