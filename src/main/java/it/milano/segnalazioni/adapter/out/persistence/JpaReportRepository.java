package it.milano.segnalazioni.adapter.out.persistence;

import it.milano.segnalazioni.application.port.ReportRepository;
import it.milano.segnalazioni.domain.Report;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaReportRepository implements ReportRepository {

    private final ReportJpaRepository delegate;

    public JpaReportRepository(ReportJpaRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Report save(Report report) {
        return delegate.save(report);
    }

    @Override
    public Optional<Report> findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public List<Report> findRecent(int limit) {
        return delegate.findAll(
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
    }
}
