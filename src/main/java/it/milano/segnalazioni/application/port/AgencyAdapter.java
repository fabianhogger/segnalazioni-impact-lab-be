package it.milano.segnalazioni.application.port;

import it.milano.segnalazioni.domain.Agency;
import it.milano.segnalazioni.domain.Attachment;
import it.milano.segnalazioni.domain.Classification;
import it.milano.segnalazioni.domain.DispatchChannel;
import it.milano.segnalazioni.domain.DispatchOutcome;
import it.milano.segnalazioni.domain.Report;

import java.util.List;

/**
 * Delivers a classified report to one body, or explains to the citizen how to deliver it.
 *
 * <p><b>This is the interface to implement when a Milan body opens an API.</b> Declare
 * {@code supports(DispatchChannel.API)}, register the bean, and flip that body's
 * {@code channel} in {@code agencies.yml}. Nothing else in the service changes.
 * {@code docs/adding-an-agency.md} walks through it.
 */
public interface AgencyAdapter {

    boolean supports(DispatchChannel channel);

    DispatchOutcome dispatch(Report report,
                             Classification classification,
                             Agency agency,
                             List<Attachment> attachments);
}
