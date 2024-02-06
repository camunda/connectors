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

import static io.camunda.connector.e2e.AwsTestHelper.initSqsClient;
import static io.camunda.zeebe.process.test.assertions.BpmnAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class AwsSqsTest extends BaseAwsTest {
  private static final String ELEMENT_TEMPLATE_PATH =
      "../../../connectors/aws/aws-sqs/element-templates/aws-sqs-outbound-connector.json";
  private static final String TEST_STANDARD_QUEUE_NAME = "test-sqs-queue";
  private static final String TEST_FIFO_QUEUE_NAME = TEST_STANDARD_QUEUE_NAME + ".fifo";
  private static final String ELEMENT_ID = "sqs-standard";
  private static final String PRIORITY = "High";
  private static final String MESSAGE_BODY = "{\"sqsMessage\":\"Hello SQS world\"}";
  private static final String MESSAGE_FIFO_BODY = "{\"sqsMessage\":\"Hello SQS FIFO world\"}";
  private static final String FIFO_QUEUE = "fifo";
  private static final String STANDARD_QUEUE = "standard";

  private static AmazonSQS sqsClient;
  private String sqsQueueUrl;

  /**
   * Initializes the Amazon SQS client before all test methods. This setup is required to interact
   * with AWS SQS for the test cases.
   */
  @BeforeAll
  public static void initClient() {
    sqsClient = initSqsClient(localstack);
  }

  /**
   * Cleans up by deleting the created SQS queue after each test method. This ensures that each test
   * starts with a fresh environment.
   */
  @AfterEach
  public void cleanup() {
    if (sqsQueueUrl != null && !sqsQueueUrl.isEmpty()) {
      AwsTestHelper.deleteQueue(sqsClient, sqsQueueUrl);
    }
  }

  /**
   * Tests the AWS SQS connector with a standard queue type. Validates that a message can be
   * successfully sent to a standard SQS queue and the same message is received back.
   */
  @Test
  public void testSqsConnectorStandardType() {
    sqsQueueUrl = AwsTestHelper.createQueue(sqsClient, TEST_STANDARD_QUEUE_NAME, false);

    var model =
        Bpmn.createProcess().executable().startEvent().serviceTask(ELEMENT_ID).endEvent().done();
    var elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("authentication.type", "credentials")
            .property("authentication.accessKey", localstack.getAccessKey())
            .property("authentication.secretKey", localstack.getSecretKey())
            .property("configuration.region", localstack.getRegion())
            .property("queue.url", sqsQueueUrl)
            .property("queue.type", STANDARD_QUEUE)
            .property("queue.messageBody", "=".concat(MESSAGE_BODY))
            .property(
                "queue.messageAttributes",
                "={\"priority\":{\"StringValue\":\"High\",\"DataType\":\"String\"}}")
            .property("retryBackoff", "PT0S")
            .property("retryCount", "0")
            .property("resultVariable", "result")
            .property("configuration.endpoint", localstack.getEndpoint().toString())
            .writeTo(new File(tempDir, "template.json"));

    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "testSqs.bpmn"))
            .apply(elementTemplate, ELEMENT_ID, new File(tempDir, "result.bpmn"));

    var bpmnTest =
        ZeebeTest.with(zeebeClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("result");

    List<Message> messages = AwsTestHelper.receiveMessages(sqsClient, sqsQueueUrl);

    assertFalse(messages.isEmpty(), "The SQS queue should have received a message");
    String actualBody = messages.get(0).getBody();
    assertEquals(
        MESSAGE_BODY, actualBody, "The received message content does not match the expected body");

    Map<String, MessageAttributeValue> messageAttributes = messages.get(0).getMessageAttributes();
    MessageAttributeValue priority = messageAttributes.get("priority");
    assertEquals(
        PRIORITY,
        priority.getStringValue(),
        "The received message priority does not match the expected value");
  }

  /**
   * Tests the AWS SQS connector with a FIFO queue type. Validates that a message can be
   * successfully sent to a FIFO SQS queue and the same message is received back, preserving the
   * order of messages.
   */
  @Test
  public void testSqsConnectorFifoType() {
    sqsQueueUrl = AwsTestHelper.createQueue(sqsClient, TEST_FIFO_QUEUE_NAME, true);

    var model =
        Bpmn.createProcess().executable().startEvent().serviceTask(ELEMENT_ID).endEvent().done();

    var elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("authentication.type", "credentials")
            .property("authentication.accessKey", localstack.getAccessKey())
            .property("authentication.secretKey", localstack.getSecretKey())
            .property("configuration.region", localstack.getRegion())
            .property("queue.url", sqsQueueUrl)
            .property("queue.type", FIFO_QUEUE)
            .property("queue.messageBody", "=".concat(MESSAGE_FIFO_BODY))
            .property(
                "queue.messageAttributes",
                "={\"priority\":{\"StringValue\":\"High\",\"DataType\":\"String\"}}")
            .property("queue.messageGroupId", "group1")
            .property("queue.messageDeduplicationId", UUID.randomUUID().toString())
            .property("retryBackoff", "PT0S")
            .property("retryCount", "0")
            .property("resultVariable", "result")
            .property("configuration.endpoint", localstack.getEndpoint().toString())
            .writeTo(new File(tempDir, "template.json"));

    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "testSqs.bpmn"))
            .apply(elementTemplate, ELEMENT_ID, new File(tempDir, "result.bpmn"));

    var bpmnTest =
        ZeebeTest.with(zeebeClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariable("result");

    List<Message> messages = AwsTestHelper.receiveMessages(sqsClient, sqsQueueUrl);

    assertFalse(messages.isEmpty(), "The SQS queue should have received a message");
    String actualBody = messages.get(0).getBody();
    assertEquals(
        MESSAGE_FIFO_BODY,
        actualBody,
        "The received message content does not match the expected body");

    Map<String, MessageAttributeValue> messageAttributes = messages.get(0).getMessageAttributes();
    MessageAttributeValue priorityAttribute = messageAttributes.get("priority");
    assertEquals(
        PRIORITY,
        priorityAttribute.getStringValue(),
        "The received message priority does not match the expected value");
  }
}
