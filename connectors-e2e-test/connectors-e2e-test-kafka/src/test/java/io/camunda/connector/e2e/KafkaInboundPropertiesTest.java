/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.feel.FeelExpressionEvaluator;
import io.camunda.connector.feel.jackson.FeelContextAwareObjectReader;
import io.camunda.connector.feel.jackson.JacksonModuleFeelFunction;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.kafka.inbound.KafkaConnectorProperties;
import io.camunda.connector.kafka.inbound.KafkaPropertyTransformer;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KafkaInboundPropertiesTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void bindsWholeCredentialExpressionThroughFeelReader(boolean staleInlineValues) throws Exception {
    String expression = "=camunda.vars.env.kafkaCredential";
    var credential =
        Map.of(
            "bootstrapServers", "credential-broker:9093",
            "username", "credential-user",
            "password", "credential-password");
    var evaluator = mock(FeelExpressionEvaluator.class);
    when(evaluator.evaluate(eq(expression), any(Object[].class))).thenReturn(credential);
    var objectMapper =
        ConnectorsObjectMapperSupplier.getCopy().registerModule(new JacksonModuleFeelFunction());
    var incomingProperties = new HashMap<String, Object>();
    incomingProperties.put("kafkaConnectionConfiguration", expression);
    incomingProperties.put("topic", Map.of("topicName", "orders"));
    incomingProperties.put("groupId", "credential-consumer");
    incomingProperties.put("autoOffsetReset", "earliest");
    if (staleInlineValues) {
      incomingProperties.put("authenticationType", "credentials");
      incomingProperties.put(
          "authentication", Map.of("username", "stale-user", "password", "stale-password"));
      incomingProperties.put(
          "topic", Map.of("bootstrapServers", "stale-broker:9092", "topicName", "orders"));
    }

    JsonNode propertiesJson = objectMapper.valueToTree(incomingProperties);
    var properties =
        FeelContextAwareObjectReader.of(objectMapper)
            .withEvaluator(evaluator)
            .readValue(propertiesJson, KafkaConnectorProperties.class);

    verify(evaluator).evaluate(eq(expression), any(Object[].class));
    assertThat(properties.kafkaConnectionConfiguration().bootstrapServers())
        .isEqualTo("credential-broker:9093");
    assertThat(properties.authentication().username()).isEqualTo("credential-user");
    assertThat(properties.authentication().password()).isEqualTo("credential-password");
    assertThat(properties.topic().bootstrapServers()).isEqualTo("credential-broker:9093");
    assertThat(properties.topic().topicName()).isEqualTo("orders");
    var consumerProperties = KafkaPropertyTransformer.getKafkaProperties(properties, null);
    assertThat(consumerProperties)
        .containsEntry("bootstrap.servers", "credential-broker:9093")
        .containsEntry("security.protocol", "SASL_SSL")
        .containsEntry("sasl.mechanism", "PLAIN")
        .containsEntry("group.id", "credential-consumer")
        .containsEntry("auto.offset.reset", "earliest");
    assertThat(consumerProperties.getProperty("sasl.jaas.config"))
        .contains("credential-user", "credential-password")
        .doesNotContain("stale-user", "stale-password");
  }
}
