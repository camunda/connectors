/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Asserts what the Groovy scripts in {@code bin/} inject into the generated A2A inbound element
 * templates, so a change that breaks a transform fails the unit tests.
 */
class A2aElementTemplateTransformsTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "agenticai-a2a-client-polling-inbound-connector-intermediate.json",
        "agenticai-a2a-client-polling-inbound-connector-receive.json"
      })
  void pollingTemplate(String file) throws IOException {
    assertTransformed(
        file,
        "data.clientResponse",
        "=if result.kind = \"task\" then\n  is defined(result.status.state) and not(list contains([\"submitted\", \"working\"], result.status.state))\nelse\n  true",
        "=internal_clientResponse.pollingData.id",
        "=pollingData.id");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "agenticai-a2a-client-webhook-inbound-connector-intermediate.json",
        "agenticai-a2a-client-webhook-inbound-connector-receive.json"
      })
  void webhookTemplate(String file) throws IOException {
    assertTransformed(
        file,
        "inbound.clientResponse",
        "=is defined(request.body.status.state) and not(list contains([\"submitted\", \"working\"], request.body.status.state))",
        "=internal_clientResponse.result.id + (if is defined(internal_clientResponse.pushNotificationData.token) then (\"-\" + internal_clientResponse.pushNotificationData.token) else \"\")",
        "=request.body.id + (if is defined(request.headers.`x-a2a-notification-token`) then (\"-\" + request.headers.`x-a2a-notification-token`) else \"\")");
  }

  private static void assertTransformed(
      String file,
      String clientResponseId,
      String activationCondition,
      String correlationKeyProcess,
      String correlationKeyPayload)
      throws IOException {
    final var properties = properties(file);
    final var ids = properties.stream().map(p -> p.path("id").asText(null)).toList();

    final var inputBinding = property(properties, "internal_clientResponse").get("binding");
    assertThat(inputBinding.get("type").asText()).isEqualTo("zeebe:input");
    assertThat(inputBinding.get("name").asText()).isEqualTo("internal_clientResponse");

    final var clientResponse = property(properties, clientResponseId);
    assertThat(clientResponse.get("type").asText()).isEqualTo("Hidden");
    assertThat(clientResponse.get("value").asText()).isEqualTo("=internal_clientResponse");
    assertThat(clientResponse.get("binding").get("type").asText()).isEqualTo("zeebe:property");
    assertThat(clientResponse.get("binding").get("name").asText()).isEqualTo(clientResponseId);
    assertThat(ids).containsOnlyOnce(clientResponseId);

    assertThat(property(properties, "activationCondition").get("value").asText())
        .isEqualTo(activationCondition);

    for (var entry :
        List.of(
            List.of("correlationKeyProcess", correlationKeyProcess),
            List.of("correlationKeyPayload", correlationKeyPayload))) {
      final var correlationKey = property(properties, entry.getFirst());
      assertThat(correlationKey.get("type").asText()).isEqualTo("Hidden");
      assertThat(correlationKey.get("value").asText()).isEqualTo(entry.getLast());
      assertThat(correlationKey.has("feel")).isFalse();
      assertThat(correlationKey.has("constraints")).isFalse();
    }
  }

  private static JsonNode property(List<JsonNode> properties, String id) {
    return properties.stream()
        .filter(p -> p.path("id").asText().equals(id))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing property: " + id));
  }

  private static List<JsonNode> properties(String file) throws IOException {
    final var result = new ArrayList<JsonNode>();
    ConnectorsObjectMapperSupplier.getCopy()
        .readTree(Path.of("element-templates", file).toFile())
        .get("properties")
        .forEach(result::add);
    return result;
  }
}
