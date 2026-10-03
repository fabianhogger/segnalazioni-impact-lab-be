package it.milano.segnalazioni.adapter.in.web;

import it.milano.segnalazioni.application.IntakeService;
import it.milano.segnalazioni.application.port.AttachmentStore;
import it.milano.segnalazioni.application.port.ReportRepository;
import it.milano.segnalazioni.config.AgencyRegistry;
import it.milano.segnalazioni.domain.Agency;
import it.milano.segnalazioni.domain.Attachment;
import it.milano.segnalazioni.domain.Report;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ReportController {

    private static final List<String> ALLOWED_IMAGE_TYPES =
            List.of("image/jpeg", "image/png", "image/webp", "image/heic");
    private static final long MAX_PHOTO_BYTES = 10L * 1024 * 1024;

    private final IntakeService intakeService;
    private final AttachmentStore attachmentStore;
    private final ReportRepository reports;
    private final AgencyRegistry agencies;

    public ReportController(IntakeService intakeService,
                            AttachmentStore attachmentStore,
                            ReportRepository reports,
                            AgencyRegistry agencies) {
        this.intakeService = intakeService;
        this.attachmentStore = attachmentStore;
        this.reports = reports;
        this.agencies = agencies;
    }

    /** Upload a photo first, then reference the returned id in the report. */
    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "file vuoto"));
        }
        if (file.getSize() > MAX_PHOTO_BYTES) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Map.of("error", "la foto supera 10 MB"));
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            return ResponseEntity.unprocessableEntity()
                    .body(Map.of("error", "formato non supportato", "supportati", ALLOWED_IMAGE_TYPES));
        }
        Attachment stored = attachmentStore.store(
                file.getOriginalFilename(), contentType, file.getBytes());
        return ResponseEntity.ok(stored);
    }

    @PostMapping("/reports")
    public ResponseEntity<ReportResponse> submit(@Valid @RequestBody ReportRequest request) {
        IntakeService.IntakeResult result = intakeService.submit(
                request.text(),
                request.contact(),
                request.latitude(),
                request.longitude(),
                request.attachmentIdsOrEmpty());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReportResponse.from(result));
    }

    @GetMapping("/reports/{id}")
    public ResponseEntity<Report> get(@PathVariable String id) {
        return reports.findById(id).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/reports")
    public List<Report> recent(@RequestParam(defaultValue = "20") int limit) {
        return reports.findRecent(Math.min(limit, 100));
    }

    /** The competence table as the service currently understands it. */
    @GetMapping("/agencies")
    public Collection<Agency> agencies() {
        return agencies.all();
    }
}
