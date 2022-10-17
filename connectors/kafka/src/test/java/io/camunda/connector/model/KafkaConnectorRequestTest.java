/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model;

import static org.apache.kafka.clients.CommonClientConfigs.SESSION_TIMEOUT_MS_CONFIG;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class KafkaConnectorRequestTest {

  @Test
  void assembleKafkaClientProperties_AdditionalPropertiesAppended() {
    KafkaConnectorRequest originalReq = simpleConnectorRequest(Collections.emptyMap());
    final int initialPropertiesSize = originalReq.assembleKafkaClientProperties().size();

    KafkaConnectorRequest newReq =
        simpleConnectorRequest(Map.of("my.custom.kafka.property", "iAmNewProperty"));
    final int newPropertiesSize = newReq.assembleKafkaClientProperties().size();
    final Properties newProps = newReq.assembleKafkaClientProperties();

    Assertions.assertThat(newPropertiesSize).isEqualTo(initialPropertiesSize + 1);
    Assertions.assertThat(newProps).containsEntry("my.custom.kafka.property", "iAmNewProperty");
  }

  @Test
  void assembleKafkaClientProperties_AdditionalPropertiesOverriddenByUser() {
    KafkaConnectorRequest originalReq = simpleConnectorRequest(Collections.emptyMap());
    final int initialPropertiesSize = originalReq.assembleKafkaClientProperties().size();

    KafkaConnectorRequest newReq =
        simpleConnectorRequest(Map.of(SESSION_TIMEOUT_MS_CONFIG, "99999"));
    final int newPropertiesSize = newReq.assembleKafkaClientProperties().size();
    final Properties newProps = newReq.assembleKafkaClientProperties();

    Assertions.assertThat(newPropertiesSize).isEqualTo(initialPropertiesSize);
    Assertions.assertThat(newProps).containsEntry(SESSION_TIMEOUT_MS_CONFIG, "99999");
  }

  private KafkaConnectorRequest simpleConnectorRequest(final Map<String, Object> kafkaProps) {
    KafkaAuthentication auth = new KafkaAuthentication();
    auth.setUsername("user1");
    auth.setPassword("pass1");

    KafkaTopic topic = new KafkaTopic();
    topic.setTopicName("my-topic");
    topic.setBootstrapServers("server1:1234,server2:1234");

    KafkaMessage msg = new KafkaMessage();
    msg.setKey("myKey");
    msg.setValue("myValue");

    KafkaConnectorRequest req = new KafkaConnectorRequest();
    req.setAuthentication(auth);
    req.setTopic(topic);
    req.setMessage(msg);

    req.setAdditionalProperties(kafkaProps);

    return req;
  }
}
