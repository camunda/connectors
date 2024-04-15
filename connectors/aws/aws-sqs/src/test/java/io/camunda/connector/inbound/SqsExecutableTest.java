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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorDefinition;
import io.camunda.connector.api.inbound.ProcessElement;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.common.suppliers.AmazonSQSClientSupplier;
import io.camunda.connector.inbound.model.SqsInboundProperties;
import io.camunda.connector.test.inbound.InboundConnectorContextBuilder;
import io.camunda.connector.test.inbound.InboundConnectorDefinitionBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
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

  private static final ObjectMapper objectMapper = ObjectMapperSupplier.getMapperInstance();
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
  public void activateTest(Map<String, Object> properties) throws InterruptedException {
    // given
    var definition = createDefinition();
    InboundConnectorContext context = createConnectorContext(properties, definition);
    InboundConnectorContext spyContext = spy(context);
    Message message = createMessage().withReceiptHandle("receiptHandle");
    Message message1 = spy(message);
    when(supplier.sqsClient(any(AWSCredentialsProvider.class), eq(ACTUAL_QUEUE_REGION)))
        .thenReturn(sqsClient);
    when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenReturn(new ReceiveMessageResult().withMessages(message1));
    consumer =
        new SqsQueueConsumer(
            sqsClient,
            objectMapper.convertValue(properties, SqsInboundProperties.class),
            spyContext);
    // when
    SqsExecutable sqsExecutable = new SqsExecutable(supplier, executorService, consumer);
    sqsExecutable.activate(spyContext);
    // then
    assertThat(consumer.isQueueConsumerActive()).isTrue();
    consumer.setQueueConsumerActive(false);
    executorService.shutdown();
    executorService.awaitTermination(1, TimeUnit.SECONDS);
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

  private InboundConnectorDefinition createDefinition() {
    var element = new ProcessElement("proc-id", 1, 2, "element-id", "<default>");
    return InboundConnectorDefinitionBuilder.create().elements(element).type("type").build();
  }

  private InboundConnectorContext createConnectorContext(
      Map<String, Object> properties, InboundConnectorDefinition definition) {
    return InboundConnectorContextBuilder.create()
        .secret(AWS_SECRET_KEY, ACTUAL_SECRET_KEY)
        .secret(AWS_ACCESS_KEY, ACTUAL_ACCESS_KEY)
        .secret(SQS_QUEUE_URL, ACTUAL_QUEUE_URL)
        .secret(ATTRIBUTE_NAME, ACTUAL_ATTRIBUTE_NAME)
        .secret(MESSAGE_ATTRIBUTE_NAME, ACTUAL_MESSAGE_ATTRIBUTE_NAME)
        .properties(properties)
        .objectMapper(objectMapper)
        .definition(definition)
        .validation(new DefaultValidationProvider())
        .build();
  }

  private Message createMessage() {
    return new Message().withMessageId("1").withBody("{\"a\":\"c\"}");
  }

  private static Stream<Map<String, Object>> successRequestCases() throws IOException {
    final String cases =
        readString(new File(SqsExecutableTest.SUCCESS_CASES_RESOURCE_PATH).toPath(), UTF_8);
    return objectMapper
        .readValue(cases, new TypeReference<List<Map<String, Object>>>() {})
        .stream();
  }
}
