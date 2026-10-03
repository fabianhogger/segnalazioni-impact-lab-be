package it.milano.segnalazioni.adapter.out.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import it.milano.segnalazioni.application.port.Classifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the classifier. Both beans are declared here, in order, rather than annotated
 * as components: {@code @ConditionalOnMissingBean} is only deterministic when the beans
 * it compares are declared in a single configuration class, and getting this wrong
 * would silently run the keyword stand-in in production.
 */
@Configuration
public class ClassifierConfig {

    @Bean
    @ConditionalOnProperty(name = "ANTHROPIC_API_KEY")
    public AnthropicClient anthropicClient() {
        return AnthropicOkHttpClient.fromEnv();
    }

    @Bean
    @ConditionalOnBean(AnthropicClient.class)
    public ClaudeClassifier claudeClassifier(
            AnthropicClient client,
            @Value("${segnalazioni.claude.model:claude-opus-5-5}") String model,
            @Value("${segnalazioni.claude.effort:low}") String effort) {
        return new ClaudeClassifier(client, model, effort);
    }

    /** Only when there is no Claude client to use. See {@link HeuristicClassifier}. */
    @Bean
    @ConditionalOnMissingBean(Classifier.class)
    public HeuristicClassifier heuristicClassifier() {
        return new HeuristicClassifier();
    }
}
