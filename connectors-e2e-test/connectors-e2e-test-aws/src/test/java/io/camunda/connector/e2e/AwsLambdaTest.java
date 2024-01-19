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

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class AwsLambdaTest extends BaseAwsTest {

  private static final String ELEMENT_TEMPLATE_PATH =
      "../../connectors/aws/aws-lambda/element-templates/aws-lambda-outbound-connector.json";
  private static final String FUNCTION_NAME = "myLambdaFunction";
  private static final String LAMBDA_FUNCTION_ZIP_FILE_PATH = "src/test/resources/function.zip";

  private static AWSLambda lambdaClient;

  /** Initializes the AWS Lambda client and sets up the Lambda function for testing. */
  @BeforeAll
  public static void initLambdaClient() throws IOException, InterruptedException {

    lambdaClient =
        AWSLambdaClientBuilder.standard()
            .withCredentials(
                new AWSStaticCredentialsProvider(
                    new BasicAWSCredentials(localstack.getAccessKey(), localstack.getSecretKey())))
            .withEndpointConfiguration(
                new AwsClientBuilder.EndpointConfiguration(
                    localstack.getEndpoint().toString(), localstack.getRegion()))
            .build();

    AwsLambdaTestHelper.waitForLambdaClientInitialization(lambdaClient);
    AwsLambdaTestHelper.initializeLambdaFunction(
        lambdaClient, LAMBDA_FUNCTION_ZIP_FILE_PATH, FUNCTION_NAME);
    AwsLambdaTestHelper.waitForLambdaFunctionToBeReady(lambdaClient, FUNCTION_NAME);
  }

  @AfterAll
  public static void cleanUpLambdaClient() {
    if (lambdaClient != null) {
      lambdaClient.shutdown();
    }
  }

  /**
   * Tests the integration of AWS Lambda within a Camunda BPMN process. This test deploys a BPMN
   * model with a Lambda service task and asserts the execution results.
   *
   * @throws Exception if any exception occurs during the test execution
   */
  @Test
  public void testLambdaFunction() throws Exception {

    var model = Bpmn.createProcess().executable().startEvent().serviceTask("aws").endEvent().done();

    var elementTemplate =
        ElementTemplate.from(ELEMENT_TEMPLATE_PATH)
            .property("authentication.type", "credentials")
            .property("authentication.accessKey", localstack.getAccessKey())
            .property("authentication.secretKey", localstack.getSecretKey())
            .property("configuration.region", localstack.getRegion())
            .property("awsFunction.operationType", "sync")
            .property("awsFunction.functionName", FUNCTION_NAME)
            .property("awsFunction.payload", "str")
            .property("retryCount", "0")
            .property("awsFunction.payload", "str")
            .property("resultExpression", "={response: payload}")
            .property("configuration.endpoint", localstack.getEndpoint().toString())
            .writeTo(new File(tempDir, "template.json"));

    var updatedModel =
        new BpmnFile(model)
            .writeToFile(new File(tempDir, "test.bpmn"))
            .apply(elementTemplate, "aws", new File(tempDir, "result.bpmn"));

    var bpmnTest =
        ZeebeTest.with(zeebeClient)
            .deploy(updatedModel)
            .createInstance()
            .waitForProcessCompletion();

    Object expectedResult =
        ObjectMapperSupplier.getMapperInstance()
            .readValue(
                "{\"statusCode\":200,\"body\":\"{\\\"message\\\": \\\"Hello from your Python Lambda function!\\\", \\\"receivedEvent\\\": \\\"str\\\"}\"}",
                Object.class);
    assertThat(bpmnTest.getProcessInstanceEvent()).hasVariableWithValue("response", expectedResult);
  }
}
