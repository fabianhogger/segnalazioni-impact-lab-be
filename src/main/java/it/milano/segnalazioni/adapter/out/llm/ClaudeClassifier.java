package it.milano.segnalazioni.adapter.out.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import it.milano.segnalazioni.application.port.Classifier;
import it.milano.segnalazioni.domain.Classification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

/**
 * Classification and extraction in one Claude call, using structured outputs so the
 * response is validated against {@link Classification} rather than parsed out of prose.
 *
 * <p>The system prompt is cached: it is long, it is identical on every request, and it
 * sits in front of the only part that varies.
 */
public class ClaudeClassifier implements Classifier {

    private static final Logger log = LoggerFactory.getLogger(ClaudeClassifier.class);

    private final AnthropicClient client;
    private final String model;
    private final OutputConfig.Effort effort;

    public ClaudeClassifier(AnthropicClient client,
                            @Value("${segnalazioni.claude.model:claude-opus-5-5}") String model,
                            @Value("${segnalazioni.claude.effort:low}") String effort) {
        this.client = client;
        this.model = model;
        // Effort is an open string enum in the SDK, not a Java enum, so it cannot be bound directly.
        this.effort = OutputConfig.Effort.of(effort.toLowerCase());
    }

    @Override
    public Classification classify(String text, boolean hasPhotos) {
        StructuredMessageCreateParams<Classification> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(4096L)
                // Opus 5.5 always thinks; effort is the only depth control. Classification is
                // a short, well-specified task, so LOW is the default. Raise it and measure
                // before assuming more thinking buys better routing.
                .outputConfig(OutputConfig.builder().effort(effort).build())
                .systemOfTextBlockParams(List.of(
                        TextBlockParam.builder()
                                .text(ClassifierPrompt.SYSTEM)
                                .cacheControl(CacheControlEphemeral.builder().build())
                                .build()))
                .addUserMessage(ClassifierPrompt.userMessage(text, hasPhotos))
                .outputConfig(Classification.class)
                .build();

        return client.messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .map(block -> block.text())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Claude returned no structured content for the classification request"));
    }
}
