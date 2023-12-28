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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.LAMBDA;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.CreateFunctionRequest;
import com.amazonaws.services.lambda.model.FunctionCode;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.inbound.importer.ProcessDefinitionSearch;
import io.camunda.operate.CamundaOperateClient;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.spring.test.ZeebeSpringTest;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=false",
      "camunda.connector.polling.enabled=false"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ZeebeSpringTest
@ExtendWith(MockitoExtension.class)
public class AwsLambdaTest {

  protected static final String ELEMENT_TEMPLATE_PATH =
      "../../connectors/aws/aws-lambda/element-templates/aws-lambda-outbound-connector.json";
  private static final String FUNCTION_NAME = "myLambdaFunction";
  private static final String LAMBDA_FUNCTION_ZIP_FILE_PATH = "src/test/resources/function.zip";

  private static final DockerImageName localstackImage =
      DockerImageName.parse("localstack/localstack");
  private static LocalStackContainer localstack;

  @TempDir File tempDir;

  @Autowired ZeebeClient zeebeClient;

  @MockBean ProcessDefinitionSearch processDefinitionSearch;

  @Autowired CamundaOperateClient camundaOperateClient;

  private static AWSLambda lambdaClient;

  @BeforeAll
  public static void setUp() throws IOException {
    localstack =
        new LocalStackContainer(localstackImage)
            .withServices(S3, LAMBDA)
            .withEnv("DEFAULT_REGION", "us-east-1")
            .withEnv("AWS_ACCESS_KEY_ID", "myTestAccessKey")
            .withEnv("AWS_SECRET_ACCESS_KEY", "myTestSecretKey");
    localstack.start();

    lambdaClient =
        AWSLambdaClientBuilder.standard()
            .withCredentials(
                new AWSStaticCredentialsProvider(
                    new BasicAWSCredentials(localstack.getAccessKey(), localstack.getSecretKey())))
            .withEndpointConfiguration(
                new AwsClientBuilder.EndpointConfiguration(
                    localstack.getEndpoint().toString(), localstack.getRegion()))
            .build();

    File zipFile = Paths.get(LAMBDA_FUNCTION_ZIP_FILE_PATH).toFile();

    CreateFunctionRequest functionRequest =
        new CreateFunctionRequest()
            .withFunctionName("myLambdaFunction")
            .withRuntime("python3.8") // Specify the correct runtime for Python
            .withRole("arn:aws:iam::000000000000:role/lambda-execute")
            .withHandler("lambda_function.lambda_handler") // Handler format: fileName.methodName
            .withCode(
                new FunctionCode()
                    .withZipFile(ByteBuffer.wrap(Files.readAllBytes(zipFile.toPath()))));

    lambdaClient.createFunction(functionRequest);
  }

  @AfterAll
  public static void after() {
    localstack.stop();
    lambdaClient.shutdown();
  }

  @BeforeEach
  void beforeEach() {
    doNothing().when(processDefinitionSearch).query(any());
  }

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
