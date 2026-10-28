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
package io.camunda.connector.runtime.managedcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogGroupRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogGroupResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutRetentionPolicyRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutRetentionPolicyResponse;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.lambda.model.GetFunctionRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.LastUpdateStatus;
import software.amazon.awssdk.services.lambda.model.ResourceConflictException;
import software.amazon.awssdk.services.lambda.model.ResourceNotFoundException;
import software.amazon.awssdk.services.lambda.model.State;
import software.amazon.awssdk.services.lambda.model.TooManyRequestsException;

class AwsLambdaManagedCodeProviderTest {

  private static final String ROLE = "arn:aws:iam::000000000000:role/test";
  private static final String PREFIX = "ce-0123456789abcdef01234567-";
  private static final String PYTHON_EXECUTOR =
      ExecutorPackage.aws(ScriptLanguage.PYTHON).functionName(PREFIX);
  private static final String NODE_EXECUTOR =
      ExecutorPackage.aws(ScriptLanguage.JAVASCRIPT).functionName(PREFIX);

  private final LambdaClient lambda = mock(LambdaClient.class, CALLS_REAL_METHODS);
  private final CloudWatchLogsClient logs = mock(CloudWatchLogsClient.class, CALLS_REAL_METHODS);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AwsLambdaManagedCodeProvider provider =
      new AwsLambdaManagedCodeProvider(
          lambda,
          logs,
          objectMapper,
          PREFIX,
          ROLE,
          256,
          14,
          Duration.ofSeconds(30),
          Duration.ofSeconds(5),
          Duration.ofMillis(1));

  @BeforeEach
  void stubLogGroups() {
    doReturn(CreateLogGroupResponse.builder().build())
        .when(logs)
        .createLogGroup(any(CreateLogGroupRequest.class));
    doReturn(PutRetentionPolicyResponse.builder().build())
        .when(logs)
        .putRetentionPolicy(any(PutRetentionPolicyRequest.class));
  }

  @Test
  void createsOneExecutorPerLanguageNamedAfterItsWrapper() throws IOException {
    doThrow(notFound())
        .doReturn(configuration(State.ACTIVE, LastUpdateStatus.SUCCESSFUL))
        .doThrow(notFound())
        .doReturn(configuration(State.ACTIVE, LastUpdateStatus.SUCCESSFUL))
        .when(lambda)
        .getFunction(any(GetFunctionRequest.class));
    doReturn(CreateFunctionResponse.builder().build())
        .when(lambda)
        .createFunction(any(CreateFunctionRequest.class));

    provider.ensureExecutors();

    final var requests = ArgumentCaptor.forClass(CreateFunctionRequest.class);
    verify(lambda, times(2)).createFunction(requests.capture());
    final var python = requests.getAllValues().get(0);
    assertThat(python.functionName()).isEqualTo(PYTHON_EXECUTOR).hasSizeLessThanOrEqualTo(64);
    assertThat(python.runtimeAsString()).isEqualTo("python3.13");
    assertThat(python.handler()).isEqualTo("aws_handler.handler");
    assertThat(python.packageTypeAsString()).isEqualTo("Zip");
    assertThat(python.role()).isEqualTo(ROLE);
    assertThat(python.timeout()).isEqualTo(35);
    assertThat(python.tags())
        .containsEntry(
            AwsLambdaManagedCodeProvider.EXECUTOR_TAG,
            ExecutorPackage.aws(ScriptLanguage.PYTHON).digest());
    assertThat(entries(python.code().zipFile().asByteArray()))
        .containsOnlyKeys("executor_core.py", "child.py", "aws_handler.py");
    final var node = requests.getAllValues().get(1);
    assertThat(node.functionName()).isEqualTo(NODE_EXECUTOR);
    assertThat(node.runtimeAsString()).isEqualTo("nodejs22.x");
    assertThat(node.handler()).isEqualTo("index.handler");
    assertThat(entries(node.code().zipFile().asByteArray()))
        .containsOnlyKeys("executor_core.mjs", "child.mjs", "index.mjs");
    final var logGroups = ArgumentCaptor.forClass(CreateLogGroupRequest.class);
    verify(logs, times(2)).createLogGroup(logGroups.capture());
    assertThat(logGroups.getAllValues())
        .extracting(CreateLogGroupRequest::logGroupName)
        .containsExactly("/aws/lambda/" + PYTHON_EXECUTOR, "/aws/lambda/" + NODE_EXECUTOR);
    final var retention = ArgumentCaptor.forClass(PutRetentionPolicyRequest.class);
    verify(logs, times(2)).putRetentionPolicy(retention.capture());
    assertThat(retention.getValue().retentionInDays()).isEqualTo(14);
  }

  @Test
  void adoptsExistingExecutorsWithoutAnyWrite() {
    doReturn(configuration(State.ACTIVE, LastUpdateStatus.SUCCESSFUL))
        .when(lambda)
        .getFunction(any(GetFunctionRequest.class));

    provider.ensureExecutors();
    provider.ensureExecutors();

    verify(lambda, never()).createFunction(any(CreateFunctionRequest.class));
    verify(logs, never()).createLogGroup(any(CreateLogGroupRequest.class));
  }

  @Test
  void adoptsAnExecutorCreatedByAnotherInstanceAndWaitsUntilItIsActive() {
    doThrow(notFound())
        .doReturn(
            configuration(State.PENDING, null),
            configuration(State.ACTIVE, LastUpdateStatus.IN_PROGRESS),
            configuration(State.ACTIVE, LastUpdateStatus.SUCCESSFUL))
        .when(lambda)
        .getFunction(any(GetFunctionRequest.class));
    doThrow(ResourceConflictException.builder().message("exists").build())
        .when(lambda)
        .createFunction(any(CreateFunctionRequest.class));

    provider.ensureExecutors();

    verify(lambda, times(1)).createFunction(any(CreateFunctionRequest.class));
  }

  @Test
  void keepsPollingWhenTheStatusReadIsThrottled() {
    doReturn(configuration(State.ACTIVE, LastUpdateStatus.SUCCESSFUL))
        .when(lambda)
        .getFunction(any(GetFunctionRequest.class));
    doThrow(notFound())
        .doThrow(TooManyRequestsException.builder().message("slow down").build())
        .doReturn(configuration(State.ACTIVE, LastUpdateStatus.SUCCESSFUL))
        .when(lambda)
        .getFunction(any(GetFunctionRequest.class));
    doReturn(CreateFunctionResponse.builder().build())
        .when(lambda)
        .createFunction(any(CreateFunctionRequest.class));

    provider.ensureExecutors();

    verify(lambda, times(1)).createFunction(any(CreateFunctionRequest.class));
  }

  @Test
  void failsWithoutRetriesWhenAnExecutorFails() {
    doThrow(notFound())
        .doReturn(
            GetFunctionResponse.builder()
                .configuration(
                    FunctionConfiguration.builder()
                        .state(State.FAILED)
                        .stateReason("role cannot be assumed")
                        .build())
                .build())
        .when(lambda)
        .getFunction(any(GetFunctionRequest.class));
    doReturn(CreateFunctionResponse.builder().build())
        .when(lambda)
        .createFunction(any(CreateFunctionRequest.class));

    final var failure =
        catchThrowableOfType(ProvisioningException.class, provider::ensureExecutors);

    assertThat(failure.code()).isEqualTo("BUILD_FAILED");
    assertThat(failure.retryable()).isFalse();
    assertThat(failure.getMessage()).contains("role cannot be assumed");
  }

  @Test
  void sendsTheScriptToTheExecutorOfItsLanguage() {
    doReturn(
            InvokeResponse.builder()
                .statusCode(200)
                .payload(
                    SdkBytes.fromUtf8String(
                        "{\"contractVersion\":\"1\",\"outcome\":\"COMPLETED\",\"variables\":{\"sum\":5}}"))
                .build())
        .when(lambda)
        .invoke(any(InvokeRequest.class));

    final var response =
        provider.execute(
            ScriptLanguage.JAVASCRIPT,
            new ExecutionRequest(
                "7",
                "export function execute() {}",
                Map.of("a", 2),
                Map.of("jobKey", "7"),
                30_000));

    assertThat(response.outcome()).isEqualTo(ExecutionResponse.COMPLETED);
    assertThat(response.variables()).containsEntry("sum", 5);
    final var request = ArgumentCaptor.forClass(InvokeRequest.class);
    verify(lambda).invoke(request.capture());
    assertThat(request.getValue().functionName()).isEqualTo(NODE_EXECUTOR);
    assertThat(request.getValue().qualifier()).isNull();
    assertThat(request.getValue().payload().asUtf8String())
        .contains("\"contractVersion\":\"1\"")
        .contains("\"script\":\"export function execute() {}\"")
        .contains("\"variables\":{\"a\":2}")
        .contains("\"timeoutMs\":30000");
  }

  @Test
  void rejectsARequestLargerThanALambdaInvocation() {
    final var response =
        provider.execute(
            ScriptLanguage.PYTHON,
            new ExecutionRequest(
                "7",
                "x".repeat(AwsLambdaManagedCodeProvider.MAX_REQUEST_BYTES),
                Map.of(),
                Map.of(),
                30_000));

    assertThat(response.error().code()).isEqualTo("RESOURCE_LIMIT");
    assertThat(response.error().retryable()).isFalse();
    verify(lambda, never()).invoke(any(InvokeRequest.class));
  }

  @Test
  void reportsAMissingExecutorAsDeploymentMissing() {
    doThrow(notFound()).when(lambda).invoke(any(InvokeRequest.class));

    final var response =
        provider.execute(
            ScriptLanguage.PYTHON, new ExecutionRequest("7", "s", Map.of(), Map.of(), 30_000));

    assertThat(response.isDeploymentMissing()).isTrue();
  }

  @Test
  void reportsAFunctionErrorAsAPlatformFailure() {
    doReturn(
            InvokeResponse.builder()
                .statusCode(200)
                .functionError("Unhandled")
                .payload(SdkBytes.fromUtf8String("{\"errorMessage\":\"Task timed out\"}"))
                .build())
        .when(lambda)
        .invoke(any(InvokeRequest.class));

    final var response =
        provider.execute(
            ScriptLanguage.PYTHON, new ExecutionRequest("7", "s", Map.of(), Map.of(), 30_000));

    assertThat(response.outcome()).isEqualTo(ExecutionResponse.FAILED);
    assertThat(response.error().code()).isEqualTo("PLATFORM_EXECUTION_FAILED");
    assertThat(response.error().message()).contains("Task timed out");
  }

  @Test
  void packagesDeterministicallyAndNamesFitTheLongestPrefix() {
    final var executor = ExecutorPackage.aws(ScriptLanguage.JAVASCRIPT);

    assertThat(executor.zip()).isEqualTo(ExecutorPackage.aws(ScriptLanguage.JAVASCRIPT).zip());
    assertThat(executor.functionName("a".repeat(48))).hasSize(63);
    assertThat(executor.digest())
        .isNotEqualTo(ExecutorPackage.gcp(ScriptLanguage.JAVASCRIPT).digest());
  }

  @Test
  void givesTheWrapperTimeToEndALateScriptItself() {
    assertThat(AwsLambdaManagedCodeProvider.functionTimeoutSeconds(Duration.ofSeconds(30)))
        .isEqualTo(35);
    assertThat(AwsLambdaManagedCodeProvider.functionTimeoutSeconds(Duration.ofHours(1)))
        .isEqualTo(900);
  }

  private static ResourceNotFoundException notFound() {
    return ResourceNotFoundException.builder().message("not found").build();
  }

  private static GetFunctionResponse configuration(State state, LastUpdateStatus lastUpdateStatus) {
    return GetFunctionResponse.builder()
        .configuration(
            FunctionConfiguration.builder().state(state).lastUpdateStatus(lastUpdateStatus).build())
        .build();
  }

  private static Map<String, String> entries(byte[] zip) throws IOException {
    final Map<String, String> entries = new HashMap<>();
    try (var input = new ZipInputStream(new ByteArrayInputStream(zip))) {
      for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
        entries.put(entry.getName(), new String(input.readAllBytes(), StandardCharsets.UTF_8));
      }
    }
    return entries;
  }
}
