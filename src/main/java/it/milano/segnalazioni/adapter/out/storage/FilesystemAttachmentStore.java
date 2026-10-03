package it.milano.segnalazioni.adapter.out.storage;

import it.milano.segnalazioni.application.port.AttachmentStore;
import it.milano.segnalazioni.domain.Attachment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Photos on local disk, metadata in memory. Adequate for the MVP and for a single
 * instance; the port exists so that swapping in S3 or MinIO is one class.
 *
 * <p>Note for the production conversation: citizen photos of public space routinely
 * contain faces and number plates. {@code docs/municipality-handoff.md} raises
 * retention and redaction as something the Comune has to decide, not us.
 */
@Component
public class FilesystemAttachmentStore implements AttachmentStore {

    private final Path root;
    private final Map<String, Attachment> index = new ConcurrentHashMap<>();

    public FilesystemAttachmentStore(@Value("${segnalazioni.attachments.path:./data/attachments}") String path)
            throws IOException {
        this.root = Path.of(path);
        Files.createDirectories(root);
    }

    @Override
    public Attachment store(String filename, String contentType, byte[] content) throws IOException {
        String id = UUID.randomUUID().toString();
        String safeName = sanitise(filename);
        Files.write(root.resolve(id), content);
        Attachment attachment = new Attachment(id, safeName, contentType, content.length);
        index.put(id, attachment);
        return attachment;
    }

    @Override
    public Optional<Attachment> metadata(String id) {
        return Optional.ofNullable(index.get(id));
    }

    @Override
    public Optional<InputStream> open(String id) throws IOException {
        Path file = root.resolve(id);
        if (!index.containsKey(id) || !Files.exists(file)) {
            return Optional.empty();
        }
        return Optional.of(Files.newInputStream(file));
    }

    private String sanitise(String filename) {
        if (filename == null || filename.isBlank()) return "foto.jpg";
        String base = Path.of(filename).getFileName().toString();
        return base.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
