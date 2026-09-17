/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.stream.StreamSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Regression coverage for the security-testing-findings#266 fix: newly modeled webhooks must
 * default to API key authorization rather than the previously shipped, fully permissive 'None'.
 */
class WebhookElementTemplateDefaultsTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @ParameterizedTest
  @ValueSource(
      strings = {
        "webhook-connector-start-event.json",
        "webhook-connector-start-message.json",
        "webhook-connector-intermediate.json",
        "webhook-connector-boundary.json",
        "webhook-connector-receive.json"
      })
  void authorizationTypeDefaultsToApiKey(String templateFileName) throws Exception {
    JsonNode template = MAPPER.readTree(new File("element-templates/" + templateFileName));

    JsonNode authTypeProperty =
        StreamSupport.stream(template.get("properties").spliterator(), false)
            .filter(property -> "inbound.auth.type".equals(property.path("id").asText(null)))
            .findFirst()
            .orElseThrow(
                () -> new AssertionError("No 'inbound.auth.type' property in " + templateFileName));

    assertThat(authTypeProperty.get("value").asText()).isEqualTo("APIKEY");
  }
}
