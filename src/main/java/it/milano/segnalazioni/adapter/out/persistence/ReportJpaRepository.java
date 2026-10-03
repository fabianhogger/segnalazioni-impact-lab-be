package it.milano.segnalazioni.adapter.out.persistence;

import it.milano.segnalazioni.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportJpaRepository extends JpaRepository<Report, String> {
}
