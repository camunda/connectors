/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.kafka.inbound.KafkaConnectorProperties;
import io.camunda.connector.kafka.inbound.KafkaPropertyTransformer;
import io.camunda.connector.kafka.outbound.KafkaConnectorFunction;
import io.camunda.connector.kafka.outbound.model.KafkaConnectorRequest;
import io.camunda.connector.runtime.test.inbound.InboundConnectorContextBuilder;
import io.camunda.connector.runtime.test.inbound.InboundConnectorDefinitionBuilder;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.types.Password;
import org.apache.kafka.common.security.JaasContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class KafkaCredentialBindingTest {

  private static final String CREDENTIAL_INPUT = "kafkaConnectionConfiguration";
  private static final Map<String, Object> CREDENTIAL =
      Map.of(
          "bootstrapServers", "credential-broker:9093",
          "username", "credential-user",
          "password", "credential-password");

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void bindsCredentialWithoutInlineConnection(boolean inbound) throws Exception {
    var input = input();
    input.put(CREDENTIAL_INPUT, CREDENTIAL);
    input.remove("authenticationType");

    var connection = bind(inbound, input);

    assertThat(connection.authentication())
        .isEqualTo(new KafkaAuthentication("credential-user", "credential-password"));
    assertThat(connection.topic())
        .isEqualTo(new KafkaTopic("credential-broker:9093", "task-topic"));
    assertThat(connection.properties())
        .containsEntry(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, "credential-broker:9093")
        .containsEntry(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL")
        .containsEntry(SaslConfigs.SASL_MECHANISM, "PLAIN");
    assertThat(connection.properties().getProperty(SaslConfigs.SASL_JAAS_CONFIG))
        .contains("credential-user", "credential-password");
    if (inbound) {
      assertThat(connection.properties())
          .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "task-group")
          .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
          .containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    }
  }

  @ParameterizedTest
  @MethodSource("jaasOptionValues")
  void preservesJaasOptionValues(boolean inbound, boolean reusable, String value) throws Exception {
    var input = input();
    var username = "user-" + value;
    var password = "password-" + value;
    if (reusable) {
      input.put(
          CREDENTIAL_INPUT,
          Map.of(
              "bootstrapServers", "credential-broker:9093",
              "username", username,
              "password", password));
    } else {
      input.put("authentication", Map.of("username", username, "password", password));
      input.put(
          "topic", Map.of("bootstrapServers", "inline-broker:9093", "topicName", "task-topic"));
    }

    var connection = bind(inbound, input);
    var entries =
        JaasContext.loadClientContext(
                Map.of(
                    SaslConfigs.SASL_JAAS_CONFIG,
                    new Password(
                        connection.properties().getProperty(SaslConfigs.SASL_JAAS_CONFIG))))
            .configurationEntries();

    assertThat(entries).hasSize(1);
    assertThat(entries.getFirst().getLoginModuleName())
        .isEqualTo("org.apache.kafka.common.security.plain.PlainLoginModule");
    assertThat(entries.getFirst().getOptions())
        .isEqualTo(Map.of("username", username, "password", password));
  }

  static Stream<Arguments> jaasOptionValues() {
    return Stream.of(false, true)
        .flatMap(
            inbound ->
                Stream.of(false, true)
                    .flatMap(
                        reusable ->
                            Stream.of(
                                    "apostrophe's",
                                    "double\"quote",
                                    "back\\slash",
                                    "trailing\\",
                                    "literal\\n",
                                    "' injected='option",
                                    "line\nbreak\rnext\tcolumn")
                                .map(value -> Arguments.of(inbound, reusable, value))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void credentialWinsOverConflictingInlineConnection(boolean inbound) throws Exception {
    var input = input();
    input.put(CREDENTIAL_INPUT, CREDENTIAL);
    input.put("authentication", Map.of("username", "inline-user", "password", "inline-password"));
    input.put("topic", Map.of("bootstrapServers", "inline-broker:9092", "topicName", "task-topic"));

    var connection = bind(inbound, input);

    assertThat(connection.authentication().username()).isEqualTo("credential-user");
    assertThat(connection.topic().bootstrapServers()).isEqualTo("credential-broker:9093");
    assertThat(connection.properties().getProperty(SaslConfigs.SASL_JAAS_CONFIG))
        .contains("credential-password")
        .doesNotContain("inline-password");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void ignoresHiddenBlankAndPartialInlineFields(boolean inbound) throws Exception {
    var input = input();
    input.put(CREDENTIAL_INPUT, CREDENTIAL);
    input.put("authentication", Map.of("username", "leftover", "password", ""));
    input.put("topic", Map.of("bootstrapServers", "", "topicName", "task-topic"));
    input.put("authenticationType", "credentials");

    assertThat(bind(inbound, input).topic().bootstrapServers()).isEqualTo("credential-broker:9093");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void preservesInlineConnectionAndLegacySecrets(boolean inbound) throws Exception {
    var input = input();
    input.put(
        "authentication",
        Map.of("username", "{{secrets.USER}}", "password", "{{secrets.PASSWORD}}"));
    input.put("topic", Map.of("bootstrapServers", "{{secrets.BROKER}}", "topicName", "task-topic"));
    input.put("configuration", Map.of("unrelated", "legacy-process-data"));

    var connection = bind(inbound, input);

    assertThat(connection.authentication())
        .isEqualTo(new KafkaAuthentication("inline-user", "inline-password"));
    assertThat(connection.topic().bootstrapServers()).isEqualTo("inline-broker:9092");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void preservesUnauthenticatedInlineConnection(boolean inbound) throws Exception {
    var input = input();
    input.put("topic", Map.of("bootstrapServers", "plain-broker:9092", "topicName", "task-topic"));

    var connection = bind(inbound, input);

    assertThat(connection.authentication()).isNull();
    assertThat(connection.properties())
        .doesNotContainKeys(
            SaslConfigs.SASL_JAAS_CONFIG, CommonClientConfigs.SECURITY_PROTOCOL_CONFIG);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void preservesAdvancedConnectionOverrides(boolean inbound) throws Exception {
    var input = input();
    input.put(CREDENTIAL_INPUT, CREDENTIAL);
    input.put(
        "additionalProperties",
        Map.of(
            "bootstrap.servers", "custom-broker:9094",
            "security.protocol", "SASL_PLAINTEXT",
            "sasl.mechanism", "SCRAM-SHA-512",
            "sasl.jaas.config", "custom-jaas",
            "client.id", "custom-client"));

    var connection = bind(inbound, input);

    assertThat(connection.topic().bootstrapServers()).isEqualTo("credential-broker:9093");
    assertThat(connection.properties())
        .containsEntry("bootstrap.servers", "custom-broker:9094")
        .containsEntry("security.protocol", "SASL_PLAINTEXT")
        .containsEntry("sasl.mechanism", "SCRAM-SHA-512")
        .containsEntry("sasl.jaas.config", "custom-jaas")
        .containsEntry("client.id", "custom-client");
  }

  @ParameterizedTest
  @MethodSource("invalidCredentials")
  void invalidCredentialDoesNotFallBack(boolean inbound, Map<String, Object> credential) {
    var input = input();
    input.put(CREDENTIAL_INPUT, credential);
    input.put("authentication", Map.of("username", "inline-user", "password", "inline-password"));
    input.put("topic", Map.of("bootstrapServers", "inline-broker:9092", "topicName", "task-topic"));

    assertThatThrownBy(() -> bind(inbound, input))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining(CREDENTIAL_INPUT);
  }

  static Stream<Arguments> invalidCredentials() {
    return Stream.of(false, true)
        .flatMap(
            inbound ->
                Stream.of("bootstrapServers", "username", "password")
                    .flatMap(
                        field ->
                            Stream.of("", " ", "missing")
                                .map(
                                    value -> {
                                      var credential = new HashMap<>(CREDENTIAL);
                                      if (value.equals("missing")) {
                                        credential.remove(field);
                                      } else {
                                        credential.put(field, value);
                                      }
                                      return Arguments.of(inbound, credential);
                                    })));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void requiresAnEffectiveBootstrapServer(boolean inbound) {
    assertThatThrownBy(() -> bind(inbound, input()))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining(
            "No bootstrap servers provided by the credential or the element template");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void credentialDoesNotSupplyTopicName(boolean inbound) {
    var input = input();
    input.put(CREDENTIAL_INPUT, CREDENTIAL);
    input.put("topic", Map.of("bootstrapServers", ""));

    assertThatThrownBy(() -> bind(inbound, input))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("topic.topicName");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void missingTopicFailsValidationRatherThanDereferencingNull(boolean inbound) {
    var input = input();
    input.put(CREDENTIAL_INPUT, CREDENTIAL);
    input.remove("topic");

    assertThatThrownBy(() -> bind(inbound, input)).isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void inlineInboundStillRequiresAuthenticationType() {
    var input = input();
    input.put("topic", Map.of("bootstrapServers", "inline-broker:9092", "topicName", "task-topic"));
    input.remove("authenticationType");

    assertThatThrownBy(() -> bind(true, input))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("An authentication type or a Kafka connection credential");
  }

  @Test
  void fetchesOnlyTheKafkaSpecificCredentialInput() {
    assertThat(KafkaConnectorFunction.class.getAnnotation(OutboundConnector.class).inputVariables())
        .contains(CREDENTIAL_INPUT, "topic", "authentication")
        .doesNotContain("configuration");
  }

  private static Map<String, Object> input() {
    var input = new HashMap<String, Object>();
    input.put("topic", Map.of("topicName", "task-topic"));
    input.put("message", Map.of("key", "task-key", "value", "task-value"));
    input.put("authenticationType", "custom");
    input.put("autoOffsetReset", "earliest");
    input.put("groupId", "task-group");
    return input;
  }

  private static Connection bind(boolean inbound, Map<String, Object> input)
      throws JsonProcessingException {
    String json = ConnectorsObjectMapperSupplier.getCopy().writeValueAsString(input);
    if (inbound) {
      var context =
          InboundConnectorContextBuilder.create()
              .properties(json)
              .secret("USER", "inline-user")
              .secret("PASSWORD", "inline-password")
              .secret("BROKER", "inline-broker:9092")
              .definition(InboundConnectorDefinitionBuilder.create().build())
              .validation(new DefaultValidationProvider())
              .build();
      var request = context.bindProperties(KafkaConnectorProperties.class);
      return new Connection(
          request.authentication(),
          request.topic(),
          KafkaPropertyTransformer.getKafkaProperties(request, context));
    }
    var context =
        OutboundConnectorContextBuilder.create()
            .variables(json)
            .secret("USER", "inline-user")
            .secret("PASSWORD", "inline-password")
            .secret("BROKER", "inline-broker:9092")
            .validation(new DefaultValidationProvider())
            .build();
    var request = context.bindVariables(KafkaConnectorRequest.class);
    return new Connection(
        request.authentication(),
        request.topic(),
        KafkaPropertiesUtil.assembleKafkaClientProperties(request));
  }

  private record Connection(
      KafkaAuthentication authentication, KafkaTopic topic, Properties properties) {}
}
