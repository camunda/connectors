package io.camunda.connector.inbound.configs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LocalContextBeanConfiguration {

    @Bean
    public ObjectMapper jacksonMapper() {
        return new ObjectMapper();
    }

}
