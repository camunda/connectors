package io.camunda.connector.runtime.instances;

import io.camunda.connector.runtime.instances.service.DefaultInstanceForwardingService;
import io.camunda.connector.runtime.instances.service.InstanceForwardingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InstanceForwardingConfiguration {

  @Value("${server.port:8080}")
  private int appPort;

  @Value("${camunda.connector.headless.serviceurl:#{null}}")
  private String headlessServiceUrl;

  @Value("${camunda.connector.hostname:${HOSTNAME}}")
  private String hostname;

  @Bean
  @ConditionalOnProperty(name = "camunda.connector.headless.serviceurl")
  public InstanceForwardingService instanceForwardingService() {
    return new DefaultInstanceForwardingService(appPort, headlessServiceUrl, hostname);
  }
}
