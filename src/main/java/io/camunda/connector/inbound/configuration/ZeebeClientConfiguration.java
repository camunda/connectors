package io.camunda.connector.inbound.configuration;

import io.camunda.zeebe.client.ZeebeClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "zeebe.client")
public class ZeebeClientConfiguration {

  private String address;

  public void setAddress(final String address) {
    this.address = address;
  }

  public String getAddress() {
    return address;
  }

  @Bean
  public ZeebeClient createZeebeClient() {
    return ZeebeClient.newClientBuilder()
        .gatewayAddress(address)
        .usePlaintext()
        .build();
  }
}
