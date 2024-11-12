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

import static org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.mockito.Mockito.when;

import io.camunda.connector.runtime.inbound.importer.ProcessDefinitionSearch;
import io.camunda.connector.runtime.inbound.operate.OperateClient;
import io.camunda.connector.runtime.inbound.state.ProcessStateStore;
import io.camunda.zeebe.client.ZeebeClient;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

public class BaseKafkaTest {

  static final String TEST_GROUP_ID = "test-group";
  static final String HEADER_KEY_VALUE = "{\"headerKey\":\"headerValue\"}";
  static final String ADDITIONAL_PROPERTIES_KEY_VALUE =
      "{\"additionPropertyKey\":\"propertyValue\"}";
  static final String MESSAGE_KEY_JSON = "{\"foo\":\"keyJsonValue\"}";
  static final String MESSAGE_KEY_STRING = "keyStringValue";
  static final String MESSAGE_VALUE = "{\"key\":\"value\"}";
  static final Object MESSAGE_KEY_JSON_AS_OBJECT = Map.of("foo", "keyJsonValue");
  static final Object MESSAGE_VALUE_AS_OBJECT = Map.of("key", "value");
  static final Map<String, String> MESSAGE_HEADERS_AS_OBJECT = Map.of("headerKey", "headerValue");
  static final String TOPIC = "test-topic-" + UUID.randomUUID();

  @TempDir File tempDir;

  @Autowired ZeebeClient zeebeClient;

  @MockBean ProcessDefinitionSearch processDefinitionSearch;

  @Autowired ProcessStateStore processStateStore;

  @Autowired OperateClient camundaOperateClient;

  static KafkaContainer kafkaContainer;

  private static final String kafkaDockerImage = "confluentinc/cp-kafka:6.2.1";

  @BeforeAll
  static void setup() {
    kafkaContainer = new KafkaContainer(DockerImageName.parse(kafkaDockerImage));
    kafkaContainer.start();
    createTopics(TOPIC);
  }

  @AfterAll
  static void tearDown() {
    if (kafkaContainer != null) {
      kafkaContainer.stop();
    }
  }

  @BeforeEach
  void beforeEach() {
    when(processDefinitionSearch.query()).thenReturn(Collections.emptyList());
  }

  private static void createTopics(String... topics) {
    var newTopics =
        Arrays.stream(topics)
            .map(topic -> new NewTopic(topic, 1, (short) 1))
            .collect(Collectors.toList());
    try (var admin = AdminClient.create(Map.of(BOOTSTRAP_SERVERS_CONFIG, getKafkaBrokers()))) {
      admin.createTopics(newTopics);
      admin.createPartitions(Map.of(TOPIC, NewPartitions.increaseTo(2)));
    }
  }

  static String getKafkaBrokers() {
    Integer mappedPort = kafkaContainer.getFirstMappedPort();
    return String.format("%s:%d", "localhost", mappedPort);
  }

  String getBootstrapServers() {
    return kafkaContainer.getBootstrapServers().replace("PLAINTEXT://", "");
  }
}
