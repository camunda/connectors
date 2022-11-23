package io.camunda.connector.runtime;

import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.cloud.GcpSecretManagerSecretProvider;
import io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SaaSConfiguration {

    @Bean
    public SecretProvider getSecretProvider(ZeebeClientConfigurationProperties conf) {
        return new GcpSecretManagerSecretProvider(conf.getCloud().getClusterId());
    }

}
