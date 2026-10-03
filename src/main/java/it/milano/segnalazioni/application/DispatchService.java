package it.milano.segnalazioni.application;

import it.milano.segnalazioni.application.port.AgencyAdapter;
import it.milano.segnalazioni.domain.Agency;
import it.milano.segnalazioni.domain.Attachment;
import it.milano.segnalazioni.domain.Classification;
import it.milano.segnalazioni.domain.DispatchOutcome;
import it.milano.segnalazioni.domain.Report;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Picks the adapter that can speak the body's channel and hands the report over.
 * Adapters are discovered from the Spring context, so adding one is a matter of
 * adding a bean.
 */
@Service
public class DispatchService {

    private final List<AgencyAdapter> adapters;

    public DispatchService(List<AgencyAdapter> adapters) {
        this.adapters = adapters;
    }

    public DispatchOutcome dispatch(Report report,
                                    Classification classification,
                                    Agency agency,
                                    List<Attachment> attachments) {
        AgencyAdapter adapter = adapters.stream()
                .filter(a -> a.supports(agency.channel()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No adapter registered for channel " + agency.channel()
                                + " required by agency " + agency.id()));
        return adapter.dispatch(report, classification, agency, attachments);
    }
}
