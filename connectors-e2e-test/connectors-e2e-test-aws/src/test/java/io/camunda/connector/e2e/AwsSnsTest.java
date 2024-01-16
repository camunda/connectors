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

import static io.camunda.zeebe.process.test.assertions.BpmnAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.CreateTopicRequest;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.io.File;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;

public class AwsSnsTest extends BaseAwsTest {

  private static final String ELEMENT_TEMPLATE_PATH =
      "../../connectors/aws/aws-sns/element-templates/aws-sns-outbound-connector.json";
  private static final String TEST_TOPIC_NAME = "test-sns-topic";
  private static final String TEST_QUEUE_NAME = "test-sqs-sqs-queue";
  private static final String EXPECTED_SNS_MESSAGE =
      "{\"message\":\"Hello, AWS SNS e2e testing world!\"}";

  private String topicArn;
  private AmazonSQS sqsClient;
  private String sqsQueueUrl;

  /**
   * Sets up the necessary AWS SNS and SQS clients before each test. It creates a SNS topic and a
   * SQS queue, then subscribes the queue to the topic.
   */
  @BeforeEach
  public void init() {
    // Initialize Amazon SNS client
    AmazonSNS snsClient =
        AmazonSNSClientBuilder.standard()
            .withCredentials(
                new AWSStaticCredentialsProvider(
                    new BasicAWSCredentials(localstack.getAccessKey(), localstack.getSecretKey())))
            .withEndpointConfiguration(
                new AwsClientBuilder.EndpointConfiguration(
                    localstack.getEndpointOverride(LocalStackContainer.Service.SNS).toString(),
                    localstack.getRegion()))
            .build();

    // Create a SNS topic
    CreateTopicRequest createTopicRequest = new CreateTopicRequest(TEST_TOPIC_NAME);
    CreateTopicResult createTopicResult = snsClient.createTopic(createTopicRequest);

    topicArn = createTopicResult.getTopicArn();

    // Initialize Amazon SQS client
    sqsClient =
        AmazonSQSClientBuilder.standard()
            .withCredentials(
                new AWSStaticCredentialsProvider(
                    new BasicAWSCredentials(localstack.getAccessKey(), localstack.getSecretKey())))
            .withEndpointConfiguration(
                new AwsClientBuilder.EndpointConfiguration(
                    localstack.getEndpoint().toString(), localstack.getRegion()))
            .build();
    // Create an SQS queue
    CreateQueueResult createQueueResult =
        sqsClient.createQueue(new CreateQueueRequest(TEST_QUEUE_NAME));
    sqsQueueUrl = createQueueResult.getQueueUrl().replace("localhost", "127.0.0.1");

    // Extract the queue ARN and subscribe the SQS queue to the SNS topic
    String queueArn =
        sqsClient
            .getQueueAttributes(
                new GetQueueAttributesRequest(sqsQueueUrl).withAttributeNames("QueueArn"))
            .getAttributes()
            .get("QueueArn");

    snsClient.subscribe(topicArn, SQS.getLocalStackName(), queueArn);
  }

  /**
   * Test method to verify SNS function integration within a BPMN process. It deploys a BPMN model
   * with a SNS service task and validates the execution result.
   */
  @Test
  public void testSnsFunction() throws JsonProcessingException, InterruptedException {

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
        ZeebeTest.with(zeebeClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("result");

    // Retrieve and validate messages from the SQS queue
    List<Message> messages =
        sqsClient
            .receiveMessage(
                new ReceiveMessageRequest(sqsQueueUrl)
                    .withWaitTimeSeconds(5)
                    .withMaxNumberOfMessages(1))
            .getMessages();
    assertFalse(messages.isEmpty(), "The SQS queue should have received a message");
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode jsonNode = objectMapper.readTree(messages.get(0).getBody());
    String actualMessageContent = jsonNode.get("Message").asText();
    assertEquals(
        EXPECTED_SNS_MESSAGE,
        actualMessageContent,
        "The received message content does not match the expected content");
  }
}
