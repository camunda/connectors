/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class KafkaConnectionConfigurationTest {

  private static final KafkaConnectionConfiguration VALID =
      new KafkaConnectionConfiguration("broker.example.com:9093", "the-login", "the-secret");

  @Test
  void exposesTheVersionedConfigurationId() {
    var annotation = KafkaConnectionConfiguration.class.getAnnotation(Configuration.class);

    assertThat(annotation.id()).isEqualTo("io.camunda.connectors:kafka-connection:1");
    assertThat(annotation.version()).isEqualTo(1);
  }

  @Test
  void deserializesAndConvertsAuthentication() throws Exception {
    var configuration =
        ConnectorsObjectMapperSupplier.getCopy()
            .readValue(
                """
                {"bootstrapServers":"broker.example.com:9093","username":"the-login","password":"the-secret"}
                """,
                KafkaConnectionConfiguration.class);

    assertThat(configuration).isEqualTo(VALID);
    assertThat(configuration.toAuthentication())
        .isEqualTo(new KafkaAuthentication("the-login", "the-secret"));
  }

  @Test
  void redactsEveryField() {
    assertThat(VALID.toString())
        .doesNotContain(VALID.bootstrapServers(), VALID.username(), VALID.password())
        .contains("[REDACTED]");
  }

  @Test
  void acceptsACompleteConfiguration() {
    assertThatNoException().isThrownBy(() -> new DefaultValidationProvider().validate(VALID));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t\n"})
  void requiresEveryField(String blank) {
    var validator = new DefaultValidationProvider();
    assertThatThrownBy(
            () ->
                validator.validate(
                    new KafkaConnectionConfiguration(blank, VALID.username(), VALID.password())))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("bootstrapServers");
    assertThatThrownBy(
            () ->
                validator.validate(
                    new KafkaConnectionConfiguration(
                        VALID.bootstrapServers(), blank, VALID.password())))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("username");
    assertThatThrownBy(
            () ->
                validator.validate(
                    new KafkaConnectionConfiguration(
                        VALID.bootstrapServers(), VALID.username(), blank)))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("password");
  }
}
