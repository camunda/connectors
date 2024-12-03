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

import static io.camunda.connector.e2e.AwsService.EVENTBRIDGE;
import static io.camunda.connector.e2e.AwsService.LAMBDA;
import static io.camunda.connector.e2e.AwsService.SNS;
import static io.camunda.connector.e2e.AwsService.SQS;
import static org.mockito.Mockito.when;

import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.inbound.importer.ProcessDefinitionSearch;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.zeebe.client.ZeebeClient;
import java.io.File;
import java.util.Collections;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
      "camunda.connector.polling.enabled=true"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
@ExtendWith(MockitoExtension.class)
public abstract class BaseAwsTest {
  private static final DockerImageName localstackImage =
      DockerImageName.parse("localstack/localstack");
  @TempDir File tempDir;
  @Autowired ZeebeClient zeebeClient;
  @MockBean ProcessDefinitionSearch processDefinitionSearch;

  static LocalStackContainer localstack;

  /**
   * Initializes the LocalStack container with required services before executing any tests. This
   * simulates the AWS environment for integration testing.
   *
   * @throws InterruptedException if the thread is interrupted during LocalStack container startup.
   */
  @BeforeAll
  static void initializeLocalStackContainer() throws InterruptedException {

    localstack =
        new LocalStackContainer(localstackImage)
            .withServices(LAMBDA, SNS, SQS, EVENTBRIDGE)
            .withEnv("DEFAULT_REGION", "us-east-1")
            .withEnv("AWS_ACCESS_KEY_ID", "myTestAccessKey")
            .withEnv("AWS_SECRET_ACCESS_KEY", "myTestSecretKey")
            .withEnv("LS_LOG", "trace-internal")
            .withEnv("LAMBDA_RUNTIME_ENVIRONMENT_TIMEOUT", "30");
    localstack.start();

    AwsTestHelper.waitForLocalStackToBeHealthy(localstack);
  }

  @BeforeEach
  void beforeEach() {
    when(processDefinitionSearch.query()).thenReturn(Collections.emptyList());
  }

  /** Stops the LocalStack container and cleans up any associated resources. */
  @AfterAll
  public static void cleanUpAfterTests() {
    localstack.stop();
    AwsTestHelper.removeLambdaContainers();
  }
}
