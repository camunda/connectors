package io.camunda.connector.inbound.feel;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeelConfiguration {

    /**
     * Provides a {@link FeelEngineWrapper} unless already present in the Spring Context
     * (as also used by other applications - as soon as we switch to use the one from util
     */
    @Bean
    //@ConditionalOnMissingBean(FeelEngineWrapper.class)
    public FeelEngineWrapper feelEngine() {
        return new FeelEngineWrapper();
    }
}
