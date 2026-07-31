/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.polling.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.http.base.model.auth.BearerAuthentication;
import io.camunda.connector.http.base.model.auth.RestAuthenticationConfiguration;
import org.junit.jupiter.api.Test;

/** Verifies the per-connector consumption of a bound authentication credential (configuration). */
class PollingRuntimePropertiesTest {

  @Test
  void usesCredentialAuthenticationWhenBound() {
    var properties = new PollingRuntimeProperties();
    properties.setAuthentication(new BearerAuthentication("inline-token"));
    properties.setAuthenticationConfiguration(
        new RestAuthenticationConfiguration(new BearerAuthentication("credential-token")));

    assertThat(properties.getAuthentication()).isInstanceOf(BearerAuthentication.class);
    assertThat(((BearerAuthentication) properties.getAuthentication()).token())
        .isEqualTo("credential-token");
  }

  @Test
  void fallsBackToInlineAuthenticationWhenNoCredential() {
    var properties = new PollingRuntimeProperties();
    properties.setAuthentication(new BearerAuthentication("inline-token"));

    assertThat(((BearerAuthentication) properties.getAuthentication()).token())
        .isEqualTo("inline-token");
  }
}
