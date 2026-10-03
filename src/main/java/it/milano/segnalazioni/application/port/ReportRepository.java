package it.milano.segnalazioni.application.port;

import it.milano.segnalazioni.domain.Report;

import java.util.List;
import java.util.Optional;

public interface ReportRepository {
    Report save(Report report);
    Optional<Report> findById(String id);
    List<Report> findRecent(int limit);
}
