/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class KafkaElementTemplateTest {

  @ParameterizedTest
  @MethodSource("templates")
  void embedsCredentialWithoutHidingOperationProperties(Path path) throws Exception {
    var template = ConnectorsObjectMapperSupplier.getCopy().readTree(path.toFile());
    boolean inbound = path.getFileName().toString().contains("inbound");
    var properties = template.path("properties");
    List<String> ids = new ArrayList<>();
    properties.forEach(property -> ids.add(property.path("id").asText()));
    var chooser = property(properties, "kafkaConnectionConfiguration");

    assertThat(template.path("engines").path("camunda").asText()).isEqualTo("^8.10");
    assertThat(template.path("version").asInt()).isEqualTo(inbound ? 8 : 7);
    assertThat(chooser.path("type").asText()).isEqualTo("Configuration");
    assertThat(chooser.path("optional").asBoolean()).isTrue();
    assertThat(chooser.path("binding").path("name").asText())
        .isEqualTo("kafkaConnectionConfiguration");
    assertThat(chooser.path("binding").path("type").asText())
        .isEqualTo(inbound ? "zeebe:property" : "zeebe:input");

    for (String fallback :
        List.of("authentication.username", "authentication.password", "topic.bootstrapServers")) {
      assertThat(ids.indexOf("kafkaConnectionConfiguration")).isLessThan(ids.indexOf(fallback));
      var condition = property(properties, fallback).path("condition");
      assertThat(condition.path("property").asText()).isEqualTo("kafkaConnectionConfiguration");
      assertThat(condition.path("isEmpty").asBoolean()).isTrue();
    }
    assertThat(
            property(properties, "topic.bootstrapServers")
                .path("constraints")
                .path("notEmpty")
                .asBoolean())
        .isTrue();
    assertThat(property(properties, "topic.topicName").has("condition")).isFalse();
    assertThat(
            property(properties, "topic.topicName")
                .path("constraints")
                .path("notEmpty")
                .asBoolean())
        .isTrue();
    assertThat(property(properties, "additionalProperties").has("condition")).isFalse();
    if (inbound) {
      assertThat(property(properties, "authenticationType").path("value").asText())
          .isEqualTo("credentials");
      assertThat(
              property(properties, "authenticationType")
                  .path("condition")
                  .path("isEmpty")
                  .asBoolean())
          .isTrue();
      assertThat(property(properties, "groupId").has("condition")).isFalse();
      assertThat(property(properties, "offsets").has("condition")).isFalse();
    } else {
      assertThat(property(properties, "message.value").has("condition")).isFalse();
    }

    var configurations = template.path("configurationTemplates");
    assertThat(configurations).hasSize(1);
    var configuration = configurations.get(0);
    assertThat(configuration.path("id").asText())
        .isEqualTo("io.camunda.connectors:kafka-connection:1");
    assertThat(chooser.path("configurationTemplate")).isEqualTo(configuration.path("id"));
    assertThat(configuration.path("version").asInt()).isEqualTo(1);
    assertThat(configuration.path("kind").asText()).isEqualTo("CREDENTIAL");
    assertThat(configuration.path("properties")).hasSize(3);
    for (JsonNode field : configuration.path("properties")) {
      assertThat(field.has("feel")).isFalse();
      assertThat(field.path("type").asText()).isEqualTo("String");
      assertThat(field.path("binding").path("type").asText()).isEqualTo("property");
      assertThat(field.path("constraints").path("notEmpty").asBoolean()).isTrue();
      assertThat(field.path("secret").asBoolean())
          .isEqualTo(!field.path("id").asText().equals("bootstrapServers"));
    }
  }

  private static Stream<Path> templates() {
    return Stream.of(
            "kafka-outbound-connector",
            "kafka-inbound-connector-boundary",
            "kafka-inbound-connector-intermediate",
            "kafka-inbound-connector-receive",
            "kafka-inbound-connector-start-message")
        .flatMap(
            name ->
                Stream.of(
                    Path.of("element-templates", name + ".json"),
                    Path.of("element-templates", "hybrid", name + "-hybrid.json")));
  }

  private static JsonNode property(JsonNode properties, String id) {
    for (JsonNode property : properties) {
      if (property.path("id").asText().equals(id)) {
        return property;
      }
    }
    throw new AssertionError("Missing property " + id);
  }
}
