/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.inbound;

import static io.camunda.connector.kafka.inbound.KafkaPropertyTransformer.DEFAULT_KEY_DESERIALIZER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.failsafe.RetryPolicy;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.kafka.model.KafkaAuthentication;
import io.camunda.connector.kafka.model.KafkaConnectionConfiguration;
import io.camunda.connector.kafka.model.KafkaTopic;
import io.camunda.connector.kafka.model.schema.NoSchemaStrategy;
import io.camunda.connector.runtime.test.inbound.InboundConnectorContextBuilder;
import io.camunda.connector.runtime.test.inbound.InboundConnectorDefinitionBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class KafkaExecutableTest {

  private static final int MAX_ATTEMPTS = 3;
  private static final int INFINITE_RETRIES = -1;
  private InboundConnectorContextBuilder.TestInboundConnectorContext context;
  private InboundConnectorContextBuilder.TestInboundConnectorContext originalContext;
  private KafkaConnectorProperties kafkaConnectorProperties;
  private KafkaTopic kafkaTopic;
  private KafkaConsumer<Object, Object> mockConsumer;

  private static Stream<Arguments> provideStringsForGetOffsets() {
    return Stream.of(
        Arguments.of("=[1,2,3]", List.of(1L, 2L, 3L)),
        Arguments.of("[1,2,3]", List.of(1L, 2L, 3L)),
        Arguments.of("1", List.of(1L)),
        Arguments.of("1,2", Arrays.asList(1L, 2L)),
        Arguments.of("1,2,3,", Arrays.asList(1L, 2L, 3L)),
        Arguments.of("1,2,3,4,5", Arrays.asList(1L, 2L, 3L, 4L, 5L)),
        Arguments.of(Arrays.asList(10L, 12L), Arrays.asList(10L, 12L)));
  }

  @BeforeEach
  @SuppressWarnings("unchecked")
  public void setUp() {
    String topic = "my-topic";
    kafkaTopic = new KafkaTopic("localhost:9092", topic);
    KafkaAuthentication kafkaAuthentication = new KafkaAuthentication(null, null);
    kafkaConnectorProperties =
        new KafkaConnectorProperties(
            KafkaConnectorProperties.AuthenticationType.custom,
            kafkaAuthentication,
            kafkaTopic,
            null,
            null,
            null,
            KafkaConnectorProperties.AutoOffsetReset.NONE,
            new NoSchemaStrategy());

    context =
        InboundConnectorContextBuilder.create()
            .secret("test", "test")
            .properties(kafkaConnectorProperties)
            .definition(InboundConnectorDefinitionBuilder.create().build())
            .validation(new DefaultValidationProvider())
            .build();
    originalContext = context;
    mockConsumer = mock(KafkaConsumer.class);
  }

  @Test
  public void testActivateMainFunctionality() throws Exception {
    KafkaExecutable kafkaExecutable = getConsumerMock();

    // Return and stop looping
    when(mockConsumer.poll(any()))
        .then(invocationOnMock -> {
          kafkaExecutable.kafkaConnectorConsumer.shouldLoop = false;
          return new ConsumerRecords<>(new HashMap<>());
        });

    var groupMetadataMock = mock(ConsumerGroupMetadata.class);
    when(groupMetadataMock.groupId()).thenReturn("groupId");
    when(groupMetadataMock.groupInstanceId()).thenReturn(Optional.of("groupInstanceId"));
    when(groupMetadataMock.generationId()).thenReturn(1);
    when(mockConsumer.groupMetadata()).thenReturn(groupMetadataMock);

    // When
    kafkaExecutable.activate(context);

    // Then
    assertNotNull(kafkaExecutable.kafkaConnectorConsumer.future);
    kafkaExecutable.kafkaConnectorConsumer.future.get(3, TimeUnit.SECONDS);
    assertEquals(originalContext, context);
    // Create an ArgumentCaptor to capture the argument passed to subscribe
    ArgumentCaptor<Collection<String>> argumentCaptor = ArgumentCaptor.forClass(Collection.class);
    verify(mockConsumer, times(1)).subscribe(argumentCaptor.capture(), any());
    verify(mockConsumer, times(1)).poll(any());
  }

  @Test
  void activatesConsumerWithCredentialAndTaskTopic() throws Exception {
    var properties = new Properties();
    var executable =
        new KafkaExecutable(
            resolved -> {
              properties.putAll(resolved);
              return mockConsumer;
            },
            RetryPolicy.builder().withMaxAttempts(1).build());
    var credentialContext =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "kafkaConnectionConfiguration",
                    new KafkaConnectionConfiguration("credential:9093", "user", "password"),
                    "topic",
                    Map.of("topicName", "task-topic"),
                    "groupId",
                    "task-group",
                    "autoOffsetReset",
                    "earliest"))
            .definition(InboundConnectorDefinitionBuilder.create().build())
            .validation(new DefaultValidationProvider())
            .build();
    when(mockConsumer.poll(any()))
        .then(invocation -> {
          executable.kafkaConnectorConsumer.shouldLoop = false;
          return ConsumerRecords.empty();
        });
    var groupMetadata = mock(ConsumerGroupMetadata.class);
    when(groupMetadata.groupId()).thenReturn("task-group");
    when(groupMetadata.groupInstanceId()).thenReturn(Optional.empty());
    when(mockConsumer.groupMetadata()).thenReturn(groupMetadata);

    executable.activate(credentialContext);
    try {
      executable.kafkaConnectorConsumer.future.get(3, TimeUnit.SECONDS);

      assertThat(properties)
          .containsEntry("bootstrap.servers", "credential:9093")
          .containsEntry("security.protocol", "SASL_SSL")
          .containsEntry("sasl.mechanism", "PLAIN")
          .containsEntry("group.id", "task-group");
      assertThat(properties.getProperty("sasl.jaas.config")).contains("user", "password");
      verify(mockConsumer).subscribe(org.mockito.ArgumentMatchers.eq(List.of("task-topic")), any());
    } finally {
      executable.deactivate();
    }
  }

  @Test
  void rejectsInvalidCredentialBeforeActivatingConsumer() {
    var executable =
        new KafkaExecutable(
            properties -> {
              throw new AssertionError("Invalid credentials must fail before creating a consumer");
            },
            RetryPolicy.builder().withMaxAttempts(1).build());
    var credentialContext =
        InboundConnectorContextBuilder.create()
            .properties(
                Map.of(
                    "kafkaConnectionConfiguration",
                    Map.of("bootstrapServers", "credential:9093", "username", "user"),
                    "authentication",
                    Map.of("username", "inline-user", "password", "inline-pass"),
                    "topic",
                    Map.of("topicName", "task-topic", "bootstrapServers", "inline:9092"),
                    "autoOffsetReset",
                    "earliest"))
            .definition(InboundConnectorDefinitionBuilder.create().build())
            .validation(new DefaultValidationProvider())
            .build();

    Assertions.assertThrows(
        ConnectorInputException.class, () -> executable.activate(credentialContext));
    assertThat(executable.kafkaConnectorConsumer).isNull();
  }

  @Test
  void testActivateAndDeactivate() {
    // Given
    doNothing().when(mockConsumer).subscribe(anyCollection(), any());
    KafkaExecutable kafkaExecutable = getConsumerMock();

    // When
    kafkaExecutable.activate(context);
    kafkaExecutable.deactivate();

    // Then
    assertEquals(originalContext, context);
    assertFalse(kafkaExecutable.kafkaConnectorConsumer.shouldLoop);
  }

  @Test
  void testActivateAndDeactivate_consumerThrows() {
    // Given
    doNothing().when(mockConsumer).subscribe(anyCollection(), any());
    KafkaExecutable kafkaExecutable = getConsumerMock();
    var groupMetadataMock = mock(ConsumerGroupMetadata.class);
    when(groupMetadataMock.groupId()).thenReturn("groupId");
    when(groupMetadataMock.groupInstanceId()).thenReturn(Optional.of("groupInstanceId"));
    when(groupMetadataMock.generationId()).thenReturn(1);
    when(mockConsumer.groupMetadata()).thenReturn(groupMetadataMock);

    // When
    when(mockConsumer.poll(any())).thenThrow(new RuntimeException("Test exception"));
    kafkaExecutable.activate(context);
    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(() -> assertFalse(kafkaExecutable.kafkaConnectorConsumer.shouldLoop));

    // Then
    verify(mockConsumer, times(MAX_ATTEMPTS)).subscribe(anyCollection(), any());
    verify(mockConsumer, times(MAX_ATTEMPTS)).poll(any(Duration.class));
    verify(mockConsumer, times(MAX_ATTEMPTS)).close();
  }

  @Test
  void testConsumerReportedDownWhenLoopEndsWithError() {
    // Given
    doNothing().when(mockConsumer).subscribe(anyCollection(), any());
    KafkaExecutable kafkaExecutable = getConsumerMock();
    var groupMetadataMock = mock(ConsumerGroupMetadata.class);
    when(groupMetadataMock.groupId()).thenReturn("groupId");
    when(groupMetadataMock.groupInstanceId()).thenReturn(Optional.of("groupInstanceId"));
    when(groupMetadataMock.generationId()).thenReturn(1);
    when(mockConsumer.groupMetadata()).thenReturn(groupMetadataMock);
    var spyContext = spy(context);

    // When an Error escapes the loop, it is not retried
    when(mockConsumer.poll(any())).thenThrow(new StackOverflowError("Test error"));
    kafkaExecutable.activate(spyContext);
    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(() -> assertTrue(kafkaExecutable.kafkaConnectorConsumer.future.isDone()));

    // Then
    verify(mockConsumer, times(1)).poll(any(Duration.class));
    ArgumentCaptor<Activity> activities = ArgumentCaptor.forClass(Activity.class);
    verify(spyContext, atLeastOnce()).log(activities.capture());
    Health lastHealth =
        activities.getAllValues().stream()
            .map(Activity::healthChange)
            .filter(Objects::nonNull)
            .reduce((first, second) -> second)
            .orElseThrow();
    assertEquals(Health.Status.DOWN, lastHealth.getStatus());
  }

  @Test
  void testActivateAndDeactivate_consumerThrowsWithInfiniteRetries() {
    // Given
    KafkaExecutable kafkaExecutable = getThrowingConsumerMock();

    // When
    kafkaExecutable.activate(context);

    // then
    Assertions.assertTimeoutPreemptively(
        Duration.ofSeconds(15),
        () -> {
          kafkaExecutable.deactivate();
        },
        "Deactivate should not hang, it's likely an issue with the stopConsumer method");
  }

  @Test
  void testConsumerNotReportedUpBeforeGroupMembershipIsConfirmed() {
    // Given a consumer that has not joined its group yet
    var group = new AtomicReference<>(new GroupState(-1, false));
    stubGroupState(group);
    stubEmptyPolls();
    var spyContext = spy(contextWithAdditionalProperties(Map.of()));
    var healthChanges = recordHealthChanges(spyContext);
    KafkaExecutable kafkaExecutable = getConsumerMock();

    // When it joins, but no heartbeat has been answered yet
    kafkaExecutable.activate(spyContext);
    awaitStatuses(healthChanges, Health.Status.UNKNOWN);
    group.set(new GroupState(1, false));
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() ->
            assertThat(healthChanges.getLast().getDetails())
                .containsEntry("group-generation-id", 1));
    awaitStatuses(healthChanges, Health.Status.UNKNOWN);

    // Then it is UP only once heartbeat responses confirm the membership
    group.set(new GroupState(1, true));
    awaitStatuses(healthChanges, Health.Status.UNKNOWN, Health.Status.UP);
    kafkaExecutable.deactivate();
  }

  @Test
  void testConsumerReportedDownWhenGroupNotJoinedWithinMaxPollInterval() {
    // Given a consumer that never joins its group
    var group = new AtomicReference<>(new GroupState(-1, false));
    stubGroupState(group);
    stubEmptyPolls();
    var spyContext =
        spy(
            contextWithAdditionalProperties(
                Map.of(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "200")));
    var healthChanges = recordHealthChanges(spyContext);
    KafkaExecutable kafkaExecutable = getConsumerMock();

    // When
    kafkaExecutable.activate(spyContext);

    // Then it is reported DOWN once max.poll.interval.ms has elapsed
    awaitStatuses(healthChanges, Health.Status.UNKNOWN, Health.Status.DOWN);
    Health down = healthChanges.getLast();
    assertThat(down.getError().code()).isEqualTo("NOT_IN_CONSUMER_GROUP");
    assertThat(down.getError().message()).contains("'groupId'");
    assertThat(down.getDetails()).containsEntry("group-generation-id", -1);

    // And UP once it joins
    group.set(new GroupState(1, true));
    awaitStatuses(healthChanges, Health.Status.UNKNOWN, Health.Status.DOWN, Health.Status.UP);
    kafkaExecutable.deactivate();
  }

  @Test
  void testConsumerReportedDownWhenGroupMembershipLostAfterJoining() {
    // Given a consumer in its group
    var group = new AtomicReference<>(new GroupState(1, true));
    stubGroupState(group);
    stubEmptyPolls();
    var spyContext =
        spy(
            contextWithAdditionalProperties(
                Map.of(
                    ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG,
                    "200",
                    ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG,
                    "300")));
    var healthChanges = recordHealthChanges(spyContext);
    KafkaExecutable kafkaExecutable = getConsumerMock();
    kafkaExecutable.activate(spyContext);
    awaitStatuses(healthChanges, Health.Status.UNKNOWN, Health.Status.UP);

    // When the coordinator stops answering its heartbeats, while
    // groupMetadata() still returns the last generation
    group.set(new GroupState(1, false));

    // Then it is UNKNOWN, then DOWN once max.poll.interval.ms has elapsed
    awaitStatuses(
        healthChanges,
        Health.Status.UNKNOWN,
        Health.Status.UP,
        Health.Status.UNKNOWN,
        Health.Status.DOWN);

    // And UP again once heartbeats are answered
    group.set(new GroupState(1, true));
    awaitStatuses(
        healthChanges,
        Health.Status.UNKNOWN,
        Health.Status.UP,
        Health.Status.UNKNOWN,
        Health.Status.DOWN,
        Health.Status.UP);
    kafkaExecutable.deactivate();
  }

  @Test
  void testConsumerStaysUpBetweenHeartbeatResponses() throws InterruptedException {
    // Given a consumer in its group, whose heartbeats are answered less often than it polls
    var group = new AtomicReference<>(new GroupState(1, true));
    stubGroupState(group);
    stubEmptyPolls();
    var spyContext =
        spy(
            contextWithAdditionalProperties(
                Map.of(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "1000")));
    var healthChanges = recordHealthChanges(spyContext);
    KafkaExecutable kafkaExecutable = getConsumerMock();

    // When it runs for several heartbeat intervals
    kafkaExecutable.activate(spyContext);
    awaitStatuses(healthChanges, Health.Status.UNKNOWN, Health.Status.UP);
    Thread.sleep(5 * HEARTBEAT_RESPONSE_INTERVAL.toMillis());

    // Then it stays UP, whatever the metrics window
    assertThat(healthChanges)
        .extracting(Health::getStatus)
        .containsExactly(Health.Status.UNKNOWN, Health.Status.UP);
    kafkaExecutable.deactivate();
  }

  private static final Duration HEARTBEAT_RESPONSE_INTERVAL = Duration.ofMillis(100);

  // The generation from groupMetadata(), and whether the coordinator answers the heartbeats
  private record GroupState(int generation, boolean answering) {}

  // Every call returns an immutable snapshot, like the real consumer
  private void stubGroupState(AtomicReference<GroupState> group) {
    // The ConsumerGroupMetadata constructors are deprecated (it becomes an interface in Kafka 5)
    when(mockConsumer.groupMetadata())
        .thenAnswer(invocation -> {
          int generation = group.get().generation();
          return mock(
              ConsumerGroupMetadata.class,
              metadataCall ->
                  switch (metadataCall.getMethod().getName()) {
                    case "groupId" -> "groupId";
                    case "generationId" -> generation;
                    case "groupInstanceId" -> Optional.empty();
                    default -> null;
                  });
        });
    // heartbeat-total grows once per HEARTBEAT_RESPONSE_INTERVAL while the coordinator answers
    var responses = new AtomicLong();
    // Started on the first read, so the consumer always starts without any response
    var lastResponseAt = new AtomicReference<Long>();
    var heartbeatTotalName =
        new MetricName(
            KafkaConnectorConsumer.HEARTBEAT_TOTAL_METRIC,
            KafkaConnectorConsumer.COORDINATOR_METRIC_GROUP,
            "",
            Map.of());
    Metric heartbeatTotal =
        new Metric() {
          @Override
          public MetricName metricName() {
            return heartbeatTotalName;
          }

          @Override
          public Object metricValue() {
            long now = System.nanoTime();
            if (lastResponseAt.compareAndSet(null, now)) {
              return (double) responses.get();
            }
            if (group.get().answering()
                && now - lastResponseAt.get() >= HEARTBEAT_RESPONSE_INTERVAL.toNanos()) {
              lastResponseAt.set(now);
              responses.incrementAndGet();
            }
            return (double) responses.get();
          }
        };
    when(mockConsumer.metrics())
        .thenAnswer(invocation -> Map.of(heartbeatTotalName, heartbeatTotal));
  }

  // Compares the statuses with consecutive repeats collapsed: the group details in the health
  // (e.g. the generation) can change without the status changing
  private static void awaitStatuses(List<Health> healthChanges, Health.Status... statuses) {
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> {
          List<Health.Status> collapsed = new ArrayList<>();
          for (Health health : healthChanges) {
            if (collapsed.isEmpty() || collapsed.getLast() != health.getStatus()) {
              collapsed.add(health.getStatus());
            }
          }
          assertThat(collapsed).containsExactly(statuses);
        });
  }

  private void stubEmptyPolls() {
    when(mockConsumer.poll(any()))
        .thenAnswer(invocation -> {
          Thread.sleep(10);
          return ConsumerRecords.empty();
        });
  }

  private InboundConnectorContextBuilder.TestInboundConnectorContext
      contextWithAdditionalProperties(Map<String, Object> additionalProperties) {
    var properties =
        new KafkaConnectorProperties(
            KafkaConnectorProperties.AuthenticationType.custom,
            new KafkaAuthentication(null, null),
            kafkaTopic,
            "groupId",
            additionalProperties,
            null,
            KafkaConnectorProperties.AutoOffsetReset.NONE,
            new NoSchemaStrategy());
    return InboundConnectorContextBuilder.create()
        .properties(properties)
        .definition(InboundConnectorDefinitionBuilder.create().build())
        .validation(new DefaultValidationProvider())
        .build();
  }

  // Records health changes as they are logged, safe to read while the consumer thread runs
  private static List<Health> recordHealthChanges(
      InboundConnectorContextBuilder.TestInboundConnectorContext spyContext) {
    List<Health> healthChanges = new CopyOnWriteArrayList<>();
    // Lenient: the code under test also calls the log(Consumer) overload
    lenient()
        .doAnswer(invocation -> {
          Activity activity = invocation.getArgument(0);
          if (activity.healthChange() != null) {
            healthChanges.add(activity.healthChange());
          }
          return null;
        })
        .when(spyContext)
        .log(any(Activity.class));
    return healthChanges;
  }

  @Test
  void testGetKafkaProperties() {
    // When
    Properties properties =
        KafkaPropertyTransformer.getKafkaProperties(kafkaConnectorProperties, context);
    // Then
    assertEquals("localhost:9092", properties.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
    assertEquals(
        DEFAULT_KEY_DESERIALIZER, properties.get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG));
    assertEquals(
        DEFAULT_KEY_DESERIALIZER, properties.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
    assertEquals(false, properties.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG));

    assertEquals(
        "kafka-inbound-connector-" + context.getDefinition().deduplicationId(),
        properties.get(ConsumerConfig.GROUP_ID_CONFIG));
  }

  @Test
  void testGroupIdUsage() {
    // When
    KafkaAuthentication kafkaAuthentication = new KafkaAuthentication(null, null);
    KafkaConnectorProperties kafkaConnectorProperties =
        new KafkaConnectorProperties(
            KafkaConnectorProperties.AuthenticationType.custom,
            kafkaAuthentication,
            kafkaTopic,
            "my-group-id",
            null,
            List.of(0L, 0L),
            KafkaConnectorProperties.AutoOffsetReset.EARLIEST,
            new NoSchemaStrategy());
    Properties properties =
        KafkaPropertyTransformer.getKafkaProperties(kafkaConnectorProperties, context);
    // Then
    assertEquals("my-group-id", properties.get(ConsumerConfig.GROUP_ID_CONFIG));
  }

  @Test
  public void testConvertConsumerRecordToKafkaInboundMessage() {
    // When
    ConsumerRecord<Object, Object> consumerRecord =
        new ConsumerRecord<>("my-topic", 0, 0, "my-key", "{\"foo\": \"bar\"}");
    consumerRecord.headers().add("header", "headerValue".getBytes());
    KafkaInboundMessage kafkaInboundMessage =
        KafkaPropertyTransformer.convertConsumerRecordToKafkaInboundMessage(
            consumerRecord, KafkaConnectorConsumer.objectMapper.reader());

    // Then
    assertEquals("my-key", kafkaInboundMessage.getKey());
    assertEquals("{\"foo\": \"bar\"}", kafkaInboundMessage.getRawValue());
    ObjectNode expectedValue = JsonNodeFactory.instance.objectNode();
    expectedValue.set("foo", JsonNodeFactory.instance.textNode("bar"));
    assertEquals(expectedValue, kafkaInboundMessage.getValue());
    assertEquals("headerValue", kafkaInboundMessage.getHeaders().get("header"));
  }

  @Test
  public void testConvertSpecialCharactersRecordToKafkaInboundMessage() {
    // When
    ConsumerRecord<Object, Object> consumerRecord =
        new ConsumerRecord<>("my-topic", 0, 0, "my-key", "{\"foo\": \"\nb\ta\\r\"}");
    KafkaInboundMessage kafkaInboundMessage =
        KafkaPropertyTransformer.convertConsumerRecordToKafkaInboundMessage(
            consumerRecord, KafkaConnectorConsumer.objectMapper.reader());

    // Then
    assertEquals("my-key", kafkaInboundMessage.getKey());
    assertEquals("{\"foo\": \"\nb\ta\\r\"}", kafkaInboundMessage.getRawValue());
    ObjectNode expectedValue = JsonNodeFactory.instance.objectNode();
    expectedValue.set("foo", JsonNodeFactory.instance.textNode("\nb\ta\r"));
    assertEquals(expectedValue, kafkaInboundMessage.getValue());
  }

  @Test
  public void testConvertDoubleEscapedCharactersRecordToKafkaInboundMessage() {
    // When
    String kafkaMessageValue =
        "{\"ordertime\":1497014222380,\"orderid\":18,\"itemid\":\"Item_184\",\"message\":\"{\\\"valid\\\":true}\",\"address\":{\"city\":\"Mountain View\",\"state\":\"CA\",\"zipcode\":94041}}";
    ConsumerRecord<Object, Object> consumerRecord =
        new ConsumerRecord<>("my-topic", 0, 0, "my-key", kafkaMessageValue);
    KafkaInboundMessage kafkaInboundMessage =
        KafkaPropertyTransformer.convertConsumerRecordToKafkaInboundMessage(
            consumerRecord, KafkaConnectorConsumer.objectMapper.reader());

    // Then
    assertEquals("my-key", kafkaInboundMessage.getKey());
    assertEquals(kafkaMessageValue, kafkaInboundMessage.getRawValue());
    assertTrue(kafkaInboundMessage.getValue() instanceof JsonNode);
    assertEquals("Item_184", ((JsonNode) kafkaInboundMessage.getValue()).get("itemid").asText());
  }

  public KafkaExecutable getConsumerMock() {
    return new KafkaExecutable(
        properties -> mockConsumer,
        RetryPolicy.builder()
            .handle(Exception.class)
            .withDelay(Duration.ofMillis(50))
            .withMaxAttempts(MAX_ATTEMPTS)
            .build());
  }

  public KafkaExecutable getThrowingConsumerMock() {
    return new KafkaExecutable(
        properties -> {
          throw new RuntimeException("Some Kafka error");
        },
        RetryPolicy.builder()
            .handle(Exception.class)
            .withDelay(Duration.ofMillis(50))
            .withMaxAttempts(INFINITE_RETRIES)
            .build());
  }

  @ParameterizedTest
  @MethodSource("provideStringsForGetOffsets")
  public void testOffsets(Object input, List<Long> expected) {

    var properties = new HashMap<String, Object>();
    properties.put("topic", new KafkaTopic("test", "test"));
    properties.put("offsets", input);
    properties.put("authenticationType", "custom");
    properties.put("autoOffsetReset", "none");

    context =
        InboundConnectorContextBuilder.create()
            .secret("test", "test")
            .properties(properties)
            .definition(InboundConnectorDefinitionBuilder.create().build())
            .validation(new DefaultValidationProvider())
            .build();

    var boundProps = context.bindProperties(KafkaConnectorProperties.class);
    assertThat(boundProps.offsets()).isEqualTo(expected);
  }
}
