package it.milano.segnalazioni.adapter.out.agency;

import it.milano.segnalazioni.application.port.AgencyAdapter;
import it.milano.segnalazioni.domain.Agency;
import it.milano.segnalazioni.domain.Attachment;
import it.milano.segnalazioni.domain.Classification;
import it.milano.segnalazioni.domain.DispatchChannel;
import it.milano.segnalazioni.domain.DispatchOutcome;
import it.milano.segnalazioni.domain.Report;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The seam, deliberately left empty.
 *
 * <p>No body in Milan currently exposes a reporting API, so there is nothing to call.
 * This class exists so that the shape of the eventual integration is visible and
 * argued about now rather than improvised later: when the Comune — or AMSA, or ATM —
 * publishes an endpoint, the work is to fill in {@link #dispatch}, point that body's
 * {@code channel} at {@code API} in {@code agencies.yml}, and delete this comment.
 * No other file in the service needs to change.
 *
 * <p>The request and response shape this service would like to speak is specified in
 * {@code docs/municipality-handoff.md} — that document is what you hand to the body
 * that is going to build it.
 */
@Component
public class ApiAgencyAdapter implements AgencyAdapter {

    @Override
    public boolean supports(DispatchChannel channel) {
        return channel == DispatchChannel.API;
    }

    @Override
    public DispatchOutcome dispatch(Report report, Classification classification,
                                    Agency agency, List<Attachment> attachments) {
        throw new UnsupportedOperationException(
                "Agency " + agency.id() + " is configured with channel=API, but no API client is "
                        + "implemented. Implement it here, or set the channel back to the one the "
                        + "body actually offers today.");
    }
}
