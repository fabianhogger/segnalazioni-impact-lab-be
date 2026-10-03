package it.milano.segnalazioni.application.port;

import it.milano.segnalazioni.domain.Classification;

/**
 * Turns a citizen's free text into a {@link Classification}. Implementations must be
 * safe to call with adversarial input: the text comes from the public internet and is
 * data, never instruction.
 */
public interface Classifier {

    /**
     * @param text        the citizen's own words
     * @param hasPhotos   whether photos were attached, which affects which bodies can accept the report
     * @return the structured reading; never null
     */
    Classification classify(String text, boolean hasPhotos);
}
