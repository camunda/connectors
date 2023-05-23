/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.google.gson.Gson;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.common.suppliers.AmazonSQSClientSupplier;
import io.camunda.connector.common.suppliers.SqsGsonComponentSupplier;
import io.camunda.connector.impl.inbound.InboundConnectorProperties;
import io.camunda.connector.impl.inbound.correlation.StartEventCorrelationPoint;
import io.camunda.connector.impl.inbound.result.MessageCorrelationResult;
import io.camunda.connector.inbound.model.SqsInboundProperties;
import io.camunda.connector.test.inbound.InboundConnectorContextBuilder;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SqsExecutableTest {
  private static final String ACTUAL_QUEUE_URL = "https://sqs.region.amazonaws.com/camunda-test";
  private static final String ACTUAL_QUEUE_REGION = "us-east-1";
  private static final String AWS_ACCESS_KEY = "AWS_ACCESS_KEY";
  private static final String AWS_SECRET_KEY = "AWS_SECRET_KEY";
  private static final String SQS_QUEUE_URL = "SQS_QUEUE_URL";
  private static final String ACTUAL_ACCESS_KEY = "4W553CR3TK3Y";
  private static final String ACTUAL_SECRET_KEY = "AAAABBBBCCCDDD";
  private static final String ATTRIBUTE_NAME = "ATTRIBUTE_NAME_KEY";
  private static final String ACTUAL_ATTRIBUTE_NAME = "attribute";
  private static final String MESSAGE_ATTRIBUTE_NAME = "MESSAGE_ATTRIBUTE_NAME_KEY";
  private static final String ACTUAL_MESSAGE_ATTRIBUTE_NAME = "message attribute";

  private static final Gson GSON = SqsGsonComponentSupplier.gsonInstance();
  private static final String SUCCESS_CASES_RESOURCE_PATH =
      "src/test/resources/requests/inbound/success-test-cases.json";

  @Mock private AmazonSQS sqsClient;
  @Mock private AmazonSQSClientSupplier supplier;
  private ExecutorService executorService;
  private SqsQueueConsumer consumer;

  @BeforeEach
  public void setUp() {
    executorService = Executors.newSingleThreadExecutor();
  }

  @ParameterizedTest
  @MethodSource("successRequestCases")
  public void activateTest(String input) throws InterruptedException {
    // given
    SqsInboundProperties properties = GSON.fromJson(input, SqsInboundProperties.class);
    InboundConnectorProperties connectorProps = createConnectorProperties();
    InboundConnectorContext context = createConnectorContext(properties, connectorProps);
    InboundConnectorContext spyContext = spy(context);
    Message message = createMessage().withReceiptHandle("receiptHandle");
    Message message1 = spy(message);
    when(supplier.sqsClient(ACTUAL_ACCESS_KEY, ACTUAL_SECRET_KEY, ACTUAL_QUEUE_REGION))
        .thenReturn(sqsClient);
    when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenReturn(new ReceiveMessageResult().withMessages(message1));
    consumer = new SqsQueueConsumer(sqsClient, properties, spyContext);
    // when
    SqsExecutable sqsExecutable = new SqsExecutable(supplier, executorService, consumer);
    sqsExecutable.activate(spyContext);
    // then
    assertThat(consumer.isQueueConsumerActive()).isTrue();
    consumer.setQueueConsumerActive(false);
    executorService.shutdown();
    executorService.awaitTermination(1, TimeUnit.SECONDS);
    verify(spyContext).replaceSecrets(properties);
    verify(spyContext).validate(properties);
    verify(spyContext, atLeast(1)).correlate(MessageMapper.toSqsInboundMessage(message));
  }

  @Test
  public void deactivateTest() {
    // Given
    consumer = new SqsQueueConsumer(sqsClient, null, null);
    consumer.setQueueConsumerActive(true);
    SqsExecutable sqsExecutable = new SqsExecutable(supplier, executorService, consumer);
    // When
    sqsExecutable.deactivate();
    // Then
    assertThat(consumer.isQueueConsumerActive()).isFalse();
    assertThat(executorService.isShutdown()).isTrue();
  }

  private InboundConnectorProperties createConnectorProperties() {
    return new InboundConnectorProperties(
        new StartEventCorrelationPoint(1, "proc-id", 2),
        Map.of("inbound.context", "context"),
        "proc-id",
        2,
        1,
        "element-id");
  }

  private InboundConnectorContext createConnectorContext(
      SqsInboundProperties properties, InboundConnectorProperties connectorProps) {
    return InboundConnectorContextBuilder.create()
        .secret(AWS_SECRET_KEY, ACTUAL_SECRET_KEY)
        .secret(AWS_ACCESS_KEY, ACTUAL_ACCESS_KEY)
        .secret(SQS_QUEUE_URL, ACTUAL_QUEUE_URL)
        .secret(ATTRIBUTE_NAME, ACTUAL_ATTRIBUTE_NAME)
        .secret(MESSAGE_ATTRIBUTE_NAME, ACTUAL_MESSAGE_ATTRIBUTE_NAME)
        .propertiesAsType(properties)
        .properties(connectorProps)
        .result(new MessageCorrelationResult("", 0))
        .build();
  }

  private Message createMessage() {
    return new Message().withMessageId("1").withBody("{\"a\":\"c\"}");
  }

  private static Stream<String> successRequestCases() throws IOException {
    return loadRequestCasesFromFile(SUCCESS_CASES_RESOURCE_PATH);
  }

  @SuppressWarnings("unchecked")
  private static Stream<String> loadRequestCasesFromFile(final String fileName) throws IOException {
    final String cases = readString(new File(fileName).toPath(), UTF_8);
    var array = GSON.fromJson(cases, ArrayList.class);
    return array.stream().map(GSON::toJson).map(Arguments::of);
  }
}
