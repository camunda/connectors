/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.integration;

import static org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.kafka.inbound.KafkaConnectorProperties;
import io.camunda.connector.kafka.inbound.KafkaExecutable;
import io.camunda.connector.kafka.model.KafkaAuthentication;
import io.camunda.connector.kafka.model.KafkaTopic;
import io.camunda.connector.kafka.model.schema.NoSchemaStrategy;
import io.camunda.connector.runtime.test.inbound.InboundConnectorContextBuilder;
import io.camunda.connector.runtime.test.inbound.InboundConnectorDefinitionBuilder;
import io.camunda.connector.test.utils.DockerImages;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Checks the inbound connector health against a real broker when the consumer loses its group
 * membership: the classic consumer keeps reporting the last generation in groupMetadata(), so the
 * health must not rely on it alone.
 */
@SlowTest
class KafkaConsumerGroupHealthIntegrationTest {

  private static final String TOPIC = "group-health-topic-" + UUID.randomUUID();

  // Own broker, as the test pauses it
  private static final KafkaContainer kafkaContainer =
      new KafkaContainer(DockerImageName.parse(DockerImages.get("kafka"))).withKraft();

  @BeforeAll
  static void init() throws Exception {
    kafkaContainer.start();
    try (var admin = AdminClient.create(Map.of(BOOTSTRAP_SERVERS_CONFIG, bootstrapServers()))) {
      admin.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1))).all().get();
    }
  }

  @AfterAll
  static void cleanup() {
    kafkaContainer.stop();
  }

  @Test
  void reportsDownWhenGroupMembershipIsLostAndUpWhenRegained() throws Exception {
    // Given a consumer in its group, with short timeouts to keep the test fast
    var context =
        spy(
            InboundConnectorContextBuilder.create()
                .properties(
                    new KafkaConnectorProperties(
                        KafkaConnectorProperties.AuthenticationType.custom,
                        new KafkaAuthentication(null, null),
                        new KafkaTopic(bootstrapServers(), TOPIC),
                        "group-health-" + UUID.randomUUID(),
                        Map.of(
                            ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG,
                            "6000",
                            ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG,
                            "1000",
                            ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG,
                            "3000",
                            ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG,
                            "10000",
                            // heartbeat-rate window, 60 s by default
                            ConsumerConfig.METRICS_SAMPLE_WINDOW_MS_CONFIG,
                            "3000"),
                        null,
                        KafkaConnectorProperties.AutoOffsetReset.EARLIEST,
                        new NoSchemaStrategy()))
                .definition(InboundConnectorDefinitionBuilder.create().build())
                .build());
    List<Health.Status> statuses = new CopyOnWriteArrayList<>();
    doAnswer(
            invocation -> {
              Activity activity = invocation.getArgument(0);
              if (activity.healthChange() != null) {
                statuses.add(activity.healthChange().getStatus());
              }
              return null;
            })
        .when(context)
        .log(any(Activity.class));
    var executable = new KafkaExecutable();
    executable.activate(context);
    try {
      await()
          .atMost(Duration.ofSeconds(60))
          .untilAsserted(() -> assertThat(statuses).contains(Health.Status.UP));

      // When the broker stops answering
      signalBroker("STOP");
      try {
        // Then the consumer is reported DOWN, although groupMetadata() keeps its generation
        await()
            .atMost(Duration.ofSeconds(90))
            .untilAsserted(() ->
                assertThat(statuses)
                    .containsSubsequence(
                        Health.Status.UP, Health.Status.UNKNOWN, Health.Status.DOWN));
      } finally {
        signalBroker("CONT");
      }

      // And UP again once it is back in its group
      await()
          .atMost(Duration.ofSeconds(90))
          .untilAsserted(() ->
              assertThat(statuses)
                  .containsSubsequence(Health.Status.UP, Health.Status.DOWN, Health.Status.UP)
                  .last()
                  .isEqualTo(Health.Status.UP));
    } finally {
      executable.deactivate();
    }
  }

  // Freezes or resumes the broker JVM: its connections stay open but nothing is answered
  private static void signalBroker(String signal) throws Exception {
    var result =
        kafkaContainer.execInContainer(
            "sh",
            "-c",
            // [.] keeps the pattern from matching this shell's own command line
            "n=0; for p in /proc/[0-9]*; do pid=${p#/proc/}; [ \"$pid\" != \"$$\" ]"
                + " && grep -q 'kafka[.]Kafka' $p/cmdline 2>/dev/null && kill -"
                + signal
                + " $pid && n=$((n+1)); done; [ $n -gt 0 ]");
    assertThat(result.getExitCode()).isZero();
  }

  private static String bootstrapServers() {
    return "localhost:" + kafkaContainer.getFirstMappedPort();
  }
}
