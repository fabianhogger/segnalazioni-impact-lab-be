package it.milano.segnalazioni.domain;

import java.util.List;
import java.util.Map;

/**
 * What happened when the service tried to deliver a report, and — when it could not —
 * everything the citizen needs in order to deliver it themselves.
 *
 * @param status      the resulting report status
 * @param agencyId    the body the report was routed to
 * @param channel     the channel that was used or recommended
 * @param reference   transport-level proof of delivery (SMTP Message-ID, API ticket id); null when nothing was sent
 * @param citizenMessage Italian text to show the citizen
 * @param action      what the citizen is expected to do next, if anything
 * @param deeplink    a {@code tel:} or {@code https:} URI the frontend can turn into a button
 * @param prefilledText text the citizen can paste into the body's own form, or read out on the phone
 * @param attachmentIds photos that should accompany the submission
 */
public record DispatchOutcome(
        ReportStatus status,
        String agencyId,
        DispatchChannel channel,
        String reference,
        String citizenMessage,
        CitizenAction action,
        String deeplink,
        String prefilledText,
        List<String> attachmentIds,
        Map<String, String> extra
) {
    public enum CitizenAction {
        /** The service delivered it. The citizen does nothing. */
        NONE,
        /** The citizen must call a number. */
        CALL,
        /** The citizen must submit a web form, usually with SPID/CIE. */
        SUBMIT_FORM,
        /** The citizen must use the body's own app. */
        USE_APP,
        /** The citizen must answer a question before anything can be filed. */
        ANSWER_QUESTIONS,
        /** Life safety: call 112 now. */
        CALL_EMERGENCY
    }
}
