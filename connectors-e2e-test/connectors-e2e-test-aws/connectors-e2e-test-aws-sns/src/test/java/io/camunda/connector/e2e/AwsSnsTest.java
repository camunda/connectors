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

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.sns.outbound.model.TopicType;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.io.File;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.CreateTopicResponse;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

@SlowTest
public class AwsSnsTest extends BaseAwsTest {

  private static final String ELEMENT_TEMPLATE_PATH =
      "../../../connectors/aws/aws-sns/element-templates/aws-sns-outbound-connector.json";
  private static final String TEST_TOPIC_NAME = "test-sns-topic";
  private static final String TEST_QUEUE_NAME = "test-sqs-sqs-queue";
  private static final String MESSAGE_GROUP_ID = "messageGroupId";
  private static final String MESSAGE_DEDUPLICATION_ID = "messageDeduplicationId";
  private static final String EXPECTED_SNS_MESSAGE =
      "{\"message\":\"Hello, AWS SNS e2e testing world!\"}";

  private String topicArn;
  private String topicFifoArn;
  private SqsClient sqsClient;
  private SnsClient snsClient;

  /**
   * Sets up the necessary AWS SNS and SQS clients before each test. It creates a SNS topic and a
   * SQS queue, then subscribes the queue to the topic.
   */
  @BeforeEach
  public void init() {
    // Initialize Amazon SNS client
    snsClient =
        SnsClient.builder()
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        localstack.getAccessKey(), localstack.getSecretKey())))
            .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SNS))
            .region(Region.of(localstack.getRegion()))
            .build();

    // Create a SNS topic
    CreateTopicRequest createTopicRequest =
        CreateTopicRequest.builder().name(TEST_TOPIC_NAME).build();
    CreateTopicResponse createTopicResult = snsClient.createTopic(createTopicRequest);

    // Create a SNS FIFO topic
    createTopicRequest =
        CreateTopicRequest.builder()
            .name(TEST_TOPIC_NAME + ".fifo")
            .attributes(java.util.Map.of("FifoTopic", "true", "ContentBasedDeduplication", "true"))
            .build();
    CreateTopicResponse createTopicFifoResult = snsClient.createTopic(createTopicRequest);
    topicArn = createTopicResult.topicArn();

    topicArn = createTopicResult.topicArn();
    topicFifoArn = createTopicFifoResult.topicArn();
    // Initialize Amazon SQS client
    sqsClient = AwsTestHelper.initSqsClient(localstack);
  }

  /**
   * Test method to verify SNS function integration within a BPMN process. It deploys a BPMN model
   * with a SNS service task and validates the execution result.
   */
  @Test
  public void testSnsFunction() throws JsonProcessingException {
    // Create an SQS queue
    String sqsQueueUrl = AwsTestHelper.createQueue(sqsClient, TEST_QUEUE_NAME, false);
    // Extract the queue ARN and subscribe the SQS queue to the SNS topic
    String queueArn = AwsTestHelper.getQueueArn(sqsClient, sqsQueueUrl);
    snsClient.subscribe(
        SubscribeRequest.builder()
            .topicArn(topicArn)
            .protocol(SQS.getLocalStackName())
            .endpoint(queueArn)
            .build());

    var model = Bpmn.createProcess().executable().startEvent().serviceTask("sns").endEvent().done();

    var elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("authentication.type", "credentials")
            .property("authentication.accessKey", localstack.getAccessKey())
            .property("authentication.secretKey", localstack.getSecretKey())
            .property("configuration.region", localstack.getRegion())
            .property("topic.topicArn", topicArn)
            .property("topic.subject", "Sample Subject")
            .property("topic.message", "=".concat(EXPECTED_SNS_MESSAGE))
            .property(
                "topic.messageAttributes",
                "={\"AttributeKey1\":{\"DataType\":\"String\",\"StringValue\":\"AttributeValue1\"}, \"AttributeKey2\":{\"DataType\":\"Number\",\"StringValue\":\"123\"}}")
            .property("retryCount", "0")
            .property("resultVariable", "result")
            .property("configuration.endpoint", localstack.getEndpoint().toString())
            .writeTo(new File(tempDir, "template.json"));

    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "testSns.bpmn"))
            .apply(elementTemplate, "sns", new File(tempDir, "resultSns.bpmn"));

    var bpmnTest =
        ZeebeTest.with(camundaClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariableNames("result");

    // Retrieve and validate messages from the SQS queue
    List<Message> messages = AwsTestHelper.receiveMessages(sqsClient, sqsQueueUrl);
    assertFalse(messages.isEmpty(), "The SQS queue should have received a message");
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode jsonNode = objectMapper.readTree(messages.getFirst().body());
    String actualMessageContent = jsonNode.get("Message").asText();
    assertEquals(
        EXPECTED_SNS_MESSAGE,
        actualMessageContent,
        "The received message content does not match the expected content");
  }

  /**
   * Verifies SNS FIFO integration in a BPMN process with a service task, ensuring ordered message
   * delivery and deduplication.
   */
  @Test
  public void testSnsFifoFunction() throws JsonProcessingException {
    // Create an SQS queue
    String sqsFifoQueueUrl =
        AwsTestHelper.createQueue(sqsClient, TEST_QUEUE_NAME.concat(".fifo"), true);
    // Extract the queue ARN and subscribe the SQS queue to the SNS topic
    String queueArn = AwsTestHelper.getQueueArn(sqsClient, sqsFifoQueueUrl);
    snsClient.subscribe(
        SubscribeRequest.builder()
            .topicArn(topicFifoArn)
            .protocol(SQS.getLocalStackName())
            .endpoint(queueArn)
            .build());

    var model = Bpmn.createProcess().executable().startEvent().serviceTask("sns").endEvent().done();

    var elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("authentication.type", "credentials")
            .property("authentication.accessKey", localstack.getAccessKey())
            .property("authentication.secretKey", localstack.getSecretKey())
            .property("configuration.region", localstack.getRegion())
            .property("topic.topicArn", topicFifoArn)
            .property("topic.type", TopicType.fifo.name())
            .property("topic.messageGroupId", MESSAGE_GROUP_ID)
            .property("topic.messageDeduplicationId", MESSAGE_DEDUPLICATION_ID)
            .property("topic.subject", "Sample Subject")
            .property("topic.message", "=".concat(EXPECTED_SNS_MESSAGE))
            .property(
                "topic.messageAttributes",
                "={\"AttributeKey1\":{\"DataType\":\"String\",\"StringValue\":\"AttributeValue1\"}, \"AttributeKey2\":{\"DataType\":\"Number\",\"StringValue\":\"123\"}}")
            .property("retryCount", "0")
            .property("resultVariable", "result")
            .property("configuration.endpoint", localstack.getEndpoint().toString())
            .writeTo(new File(tempDir, "template.json"));

    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "testSns.bpmn"))
            .apply(elementTemplate, "sns", new File(tempDir, "resultSns.bpmn"));

    var bpmnTest =
        ZeebeTest.with(camundaClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariableNames("result");

    // Retrieve and validate messages from the SQS queue
    List<Message> messages = AwsTestHelper.receiveMessages(sqsClient, sqsFifoQueueUrl);
    assertFalse(messages.isEmpty(), "The SQS queue should have received a message");
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode jsonNode = objectMapper.readTree(messages.getFirst().body());
    String actualMessageContent = jsonNode.get("Message").asText();
    assertEquals(
        EXPECTED_SNS_MESSAGE,
        actualMessageContent,
        "The received message content does not match the expected content");
    String messageGroupId =
        messages.getFirst().attributes().get(MessageSystemAttributeName.MESSAGE_GROUP_ID);
    String messageDeduplicationId =
        messages.getFirst().attributes().get(MessageSystemAttributeName.MESSAGE_DEDUPLICATION_ID);
    assertEquals(MESSAGE_GROUP_ID, messageGroupId);
    assertEquals(MESSAGE_DEDUPLICATION_ID, messageDeduplicationId);
  }
}
