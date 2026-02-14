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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.io.File;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

@SlowTest
public class AwsEventBridgeTest extends BaseAwsTest {
  private static final String ELEMENT_TEMPLATE_PATH =
      "../../../connectors/aws/aws-eventbridge/element-templates/aws-eventbridge-outbound-connector.json";

  private static final String RULE_NAME = "test-rule";
  private static final String QUEUE_NAME = "test-queue";
  private static final String TARGET_ID = "1";
  private static final String EVENT_BUS_NAME = "default";
  private static final String SOURCE = "my-application";
  private static final String DETAIL_TYPE = "orderPlaced";
  private static final String DETAIL = "{\"eventType\":\"newOrder\"}";
  private static final String EVENT_PATTERN =
      """
            {
              "source": ["my-application"],
              "detail-type": ["orderPlaced"]
            }
            """;

  private EventBridgeClient eventBridgeClient;
  private SqsClient sqsClient;
  private String queueUrl;

  @BeforeEach
  public void setUp() {
    eventBridgeClient = AwsTestHelper.initEventBridgeClient(localstack);
    sqsClient = AwsTestHelper.initSqsClient(localstack);
    // Create an SQS queue and get its ARN
    queueUrl = AwsTestHelper.createQueue(sqsClient, QUEUE_NAME, false);
    GetQueueAttributesResponse queueAttributes =
        sqsClient.getQueueAttributes(
            GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.QUEUE_ARN)
                .build());
    String queueArn = queueAttributes.attributesAsStrings().get("QueueArn");
    // Define an event pattern
    // Create a rule on the default event bus and add the SQS queue as a target
    eventBridgeClient.putRule(
        PutRuleRequest.builder()
            .name(RULE_NAME)
            .eventPattern(EVENT_PATTERN)
            .eventBusName(EVENT_BUS_NAME)
            .build());
    eventBridgeClient.putTargets(
        PutTargetsRequest.builder()
            .rule(RULE_NAME)
            .eventBusName(EVENT_BUS_NAME)
            .targets(Target.builder().arn(queueArn).id(TARGET_ID).build())
            .build());
  }

  @AfterEach
  public void cleanUpResources() {
    // Clean up resources
    sqsClient.deleteQueue(DeleteQueueRequest.builder().queueUrl(queueUrl).build());
    // Remove targets from the rule
    eventBridgeClient.removeTargets(
        RemoveTargetsRequest.builder().rule(RULE_NAME).ids(TARGET_ID).build());
    // Now delete the rule
    eventBridgeClient.deleteRule(DeleteRuleRequest.builder().name(RULE_NAME).build());
  }

  @Test
  public void testEventBridgeConnectorFunction() throws JsonProcessingException {
    var model =
        Bpmn.createProcess()
            .executable()
            .startEvent()
            .serviceTask("aws-eventbridge-element-id")
            .endEvent()
            .done();
    var elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("authentication.type", "credentials")
            .property("authentication.accessKey", localstack.getAccessKey())
            .property("authentication.secretKey", localstack.getSecretKey())
            .property("configuration.region", localstack.getRegion())
            .property("input.eventBusName", "=\"" + EVENT_BUS_NAME + "\"")
            .property("input.source", SOURCE)
            .property("input.detailType", DETAIL_TYPE)
            .property("input.detail", "=" + DETAIL)
            .property("retryBackoff", "PT0S")
            .property("retryCount", "0")
            .property("resultVariable", "result")
            .property("configuration.endpoint", localstack.getEndpoint().toString())
            .writeTo(new File(tempDir, "template.json"));

    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "test.bpmn"))
            .apply(elementTemplate, "aws-eventbridge-element-id", new File(tempDir, "result.bpmn"));

    var bpmnTest =
        ZeebeTest.with(camundaClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariableNames("result");
    // Poll the SQS queue to check for the event
    List<Message> messages = AwsTestHelper.receiveMessages(sqsClient, queueUrl);
    // Assert that the queue received the message
    assertFalse(messages.isEmpty(), "The queue should have the event message");
    ObjectMapper objectMapper = ObjectMapperSupplier.getMapperInstance();

    // Parse the body of the first message
    var dataMap = objectMapper.readValue(messages.getFirst().body(), Map.class);

    // Assert the expected values
    assertEquals(SOURCE, dataMap.get("source"));
    assertEquals(DETAIL_TYPE, dataMap.get("detail-type"));

    // Deserialize the DETAIL constant to compare as a Map
    var expectedDetailMap = objectMapper.readValue(DETAIL, Map.class);
    assertEquals(expectedDetailMap, dataMap.get("detail"));
  }
}
