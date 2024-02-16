/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.outbound.model;

import static org.apache.kafka.clients.CommonClientConfigs.SESSION_TIMEOUT_MS_CONFIG;

import io.camunda.connector.kafka.model.KafkaAuthentication;
import io.camunda.connector.kafka.model.KafkaPropertiesUtil;
import io.camunda.connector.kafka.model.KafkaTopic;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class KafkaConnectorRequestTest {

  @Test
  void assembleKafkaClientProperties_AdditionalPropertiesAppended() {
    KafkaConnectorRequest originalReq = simpleConnectorRequest(Collections.emptyMap());
    final int initialPropertiesSize =
        KafkaPropertiesUtil.assembleKafkaClientProperties(originalReq).size();

    KafkaConnectorRequest newReq =
        simpleConnectorRequest(Map.of("my.custom.kafka.property", "iAmNewProperty"));
    final int newPropertiesSize = KafkaPropertiesUtil.assembleKafkaClientProperties(newReq).size();
    final Properties newProps = KafkaPropertiesUtil.assembleKafkaClientProperties(newReq);

    Assertions.assertThat(newPropertiesSize).isEqualTo(initialPropertiesSize + 1);
    Assertions.assertThat(newProps).containsEntry("my.custom.kafka.property", "iAmNewProperty");
  }

  @Test
  void assembleKafkaClientProperties_AdditionalPropertiesOverriddenByUser() {
    KafkaConnectorRequest originalReq = simpleConnectorRequest(Collections.emptyMap());

    final int initialPropertiesSize =
        KafkaPropertiesUtil.assembleKafkaClientProperties(originalReq).size();

    KafkaConnectorRequest newReq =
        simpleConnectorRequest(Map.of(SESSION_TIMEOUT_MS_CONFIG, "99999"));
    final int newPropertiesSize = KafkaPropertiesUtil.assembleKafkaClientProperties(newReq).size();
    final Properties newProps = KafkaPropertiesUtil.assembleKafkaClientProperties(newReq);

    Assertions.assertThat(newPropertiesSize).isEqualTo(initialPropertiesSize);
    Assertions.assertThat(newProps).containsEntry(SESSION_TIMEOUT_MS_CONFIG, "99999");
  }

  private KafkaConnectorRequest simpleConnectorRequest(final Map<String, Object> kafkaProps) {
    KafkaAuthentication auth = new KafkaAuthentication("user1", "pass1");
    KafkaTopic topic = new KafkaTopic("server1:1234,server2:1234", "my-topic");
    KafkaMessage msg = new KafkaMessage("myKey", "myValue");
    return new KafkaConnectorRequest(auth, topic, msg, null, kafkaProps, null);
  }
}
