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
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionConfigurationRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionConfigurationResponse;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.LastUpdateStatus;
import software.amazon.awssdk.services.lambda.model.PublishVersionRequest;
import software.amazon.awssdk.services.lambda.model.PublishVersionResponse;
import software.amazon.awssdk.services.lambda.model.ResourceConflictException;
import software.amazon.awssdk.services.lambda.model.ResourceNotFoundException;
import software.amazon.awssdk.services.lambda.model.State;

class AwsLambdaManagedCodeProviderTest {

  private static final String ROLE = "arn:aws:iam::000000000000:role/test";

  private final LambdaClient lambda = mock(LambdaClient.class, CALLS_REAL_METHODS);
  private final AwsLambdaScriptPackager packager = new AwsLambdaScriptPackager();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AwsLambdaManagedCodeProvider provider =
      new AwsLambdaManagedCodeProvider(
          lambda,
          objectMapper,
          ROLE,
          256,
          Duration.ofSeconds(30),
          Duration.ofSeconds(5),
          Duration.ofMillis(1));

  @Test
  void createsAndPublishesAFunctionNamedAfterTheArtifact() throws IOException {
    final var artifact = artifact(ScriptLanguage.PYTHON, "python3", "calc.py");
    doReturn(CreateFunctionResponse.builder().version("1").build())
        .when(lambda)
        .createFunction(any(CreateFunctionRequest.class));
    doReturn(configuration(State.ACTIVE, LastUpdateStatus.SUCCESSFUL))
        .when(lambda)
        .getFunctionConfiguration(any(GetFunctionConfigurationRequest.class));
    doReturn(PublishVersionResponse.builder().version("1").build())
        .when(lambda)
        .publishVersion(any(PublishVersionRequest.class));

    final var handle = provision(artifact);

    assertThat(handle.ready().toCompletableFuture().join())
        .isEqualTo(new ProviderDeployment("aws", artifact.deploymentName() + ":1"));
    final var request = ArgumentCaptor.forClass(CreateFunctionRequest.class);
    verify(lambda).createFunction(request.capture());
    assertThat(request.getValue().functionName()).isEqualTo(artifact.deploymentName());
    assertThat(request.getValue().runtimeAsString()).isEqualTo("python3.13");
    assertThat(request.getValue().handler()).isEqualTo("handler.lambda_handler");
    assertThat(request.getValue().role()).isEqualTo(ROLE);
    assertThat(request.getValue().publish()).isTrue();
    assertThat(request.getValue().tags())
        .containsEntry(AwsLambdaManagedCodeProvider.DIGEST_TAG, artifact.digest());
    assertThat(entries(request.getValue().code().zipFile().asByteArray()))
        .containsOnlyKeys("handler.py", "user_script.py")
        .containsEntry("user_script.py", "def execute(v, c):\n    return v\n");
    final var publish = ArgumentCaptor.forClass(PublishVersionRequest.class);
    verify(lambda).publishVersion(publish.capture());
    assertThat(publish.getValue().codeSha256()).isEqualTo("sha");
  }

  @Test
  void adoptsAFunctionCreatedByAnotherInstanceAndWaitsUntilItIsActive() {
    final var artifact = artifact(ScriptLanguage.JAVASCRIPT, "nodejs", "sum.js");
    doThrow(ResourceConflictException.builder().message("exists").build())
        .when(lambda)
        .createFunction(any(CreateFunctionRequest.class));
    doReturn(
            configuration(State.PENDING, null),
            configuration(State.ACTIVE, LastUpdateStatus.IN_PROGRESS),
            configuration(State.ACTIVE, LastUpdateStatus.SUCCESSFUL))
        .when(lambda)
        .getFunctionConfiguration(any(GetFunctionConfigurationRequest.class));
    doThrow(ResourceConflictException.builder().message("in progress").build())
        .doReturn(PublishVersionResponse.builder().version("3").build())
        .when(lambda)
        .publishVersion(any(PublishVersionRequest.class));

    final var handle = provision(artifact);

    assertThat(handle.ready().toCompletableFuture().join().deploymentId())
        .isEqualTo(artifact.deploymentName() + ":3");
  }

  @Test
  void failsWithoutRetriesWhenTheFunctionFails() {
    final var artifact = artifact(ScriptLanguage.JAVASCRIPT, "nodejs", "sum.js");
    doReturn(CreateFunctionResponse.builder().version("1").build())
        .when(lambda)
        .createFunction(any(CreateFunctionRequest.class));
    doReturn(
            GetFunctionConfigurationResponse.builder()
                .state(State.FAILED)
                .stateReason("role cannot be assumed")
                .build())
        .when(lambda)
        .getFunctionConfiguration(any(GetFunctionConfigurationRequest.class));

    final var failure =
        catchThrowableOfType(ProvisioningException.class, () -> provision(artifact));

    assertThat(failure.code()).isEqualTo("BUILD_FAILED");
    assertThat(failure.retryable()).isFalse();
    assertThat(failure.getMessage()).contains("role cannot be assumed");
    verify(lambda, never()).publishVersion(any(PublishVersionRequest.class));
  }

  @Test
  void rejectsAnUnsupportedRuntime() {
    final var failure =
        catchThrowableOfType(
            ProvisioningException.class,
            () -> packager.packageFor(artifact(ScriptLanguage.PYTHON, "python2", "a.py")));

    assertThat(failure.code()).isEqualTo("UNSUPPORTED_RUNTIME");
    assertThat(failure.retryable()).isFalse();
  }

  @Test
  void packagesDeterministically() {
    final var artifact = artifact(ScriptLanguage.JAVASCRIPT, "nodejs", "sum.js");

    assertThat(AwsLambdaManagedCodeProvider.zip(packager.packageFor(artifact)))
        .isEqualTo(AwsLambdaManagedCodeProvider.zip(packager.packageFor(artifact)));
  }

  @Test
  void invokesThePublishedVersionAndReadsTheContractResponse() {
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
        provider.invoke(
            new ProviderDeployment("aws", "camunda-ms-abc:4"),
            new ExecutionRequest("7", Map.of("a", 2), Map.of("jobKey", "7")));

    assertThat(response.outcome()).isEqualTo(ExecutionResponse.COMPLETED);
    assertThat(response.variables()).containsEntry("sum", 5);
    final var request = ArgumentCaptor.forClass(InvokeRequest.class);
    verify(lambda).invoke(request.capture());
    assertThat(request.getValue().functionName()).isEqualTo("camunda-ms-abc");
    assertThat(request.getValue().qualifier()).isEqualTo("4");
    assertThat(request.getValue().payload().asUtf8String())
        .contains("\"contractVersion\":\"1\"")
        .contains("\"variables\":{\"a\":2}");
  }

  @Test
  void reportsAMissingFunctionAsDeploymentMissing() {
    doThrow(ResourceNotFoundException.builder().message("gone").build())
        .when(lambda)
        .invoke(any(InvokeRequest.class));

    final var response =
        provider.invoke(
            new ProviderDeployment("aws", "camunda-ms-abc:4"),
            new ExecutionRequest("7", Map.of(), Map.of()));

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
        provider.invoke(
            new ProviderDeployment("aws", "camunda-ms-abc:4"),
            new ExecutionRequest("7", Map.of(), Map.of()));

    assertThat(response.outcome()).isEqualTo(ExecutionResponse.FAILED);
    assertThat(response.error().code()).isEqualTo("PLATFORM_EXECUTION_FAILED");
    assertThat(response.error().message()).contains("Task timed out");
  }

  @Test
  void deletesIdempotently() {
    doThrow(ResourceNotFoundException.builder().message("gone").build())
        .when(lambda)
        .deleteFunction(any(DeleteFunctionRequest.class));

    provider.delete(new ProviderDeployment("aws", "camunda-ms-abc:4"));

    final var request = ArgumentCaptor.forClass(DeleteFunctionRequest.class);
    verify(lambda).deleteFunction(request.capture());
    assertThat(request.getValue().functionName()).isEqualTo("camunda-ms-abc");
  }

  private ProvisioningHandle provision(ArtifactSpec artifact) {
    return provider.ensureProvisioned(artifact);
  }

  @Test
  void changesTheDigestWithTheHandler() {
    final var script =
        "export function execute(v) { return v; }\n".getBytes(StandardCharsets.UTF_8);

    assertThat(
            ArtifactSpec.digest(
                packager.buildVersion(),
                ScriptLanguage.JAVASCRIPT,
                "nodejs",
                script,
                Optional.empty()))
        .isNotEqualTo(
            ArtifactSpec.digest(
                new LocalScriptPackager().buildVersion(),
                ScriptLanguage.JAVASCRIPT,
                "nodejs",
                script,
                Optional.empty()));
  }

  private static ArtifactSpec artifact(ScriptLanguage language, String runtime, String name) {
    final var script =
        language == ScriptLanguage.PYTHON
            ? "def execute(v, c):\n    return v\n"
            : "export function execute(v) { return v; }\n";
    return ArtifactSpec.create(
        "default",
        "tenant-a",
        "aws",
        new AwsLambdaScriptPackager().buildVersion(),
        ArtifactSpec.DEFAULT_DEPLOYMENT_NAME_PREFIX,
        language,
        runtime,
        name,
        script.getBytes(StandardCharsets.UTF_8),
        Optional.empty());
  }

  private static GetFunctionConfigurationResponse configuration(
      State state, LastUpdateStatus lastUpdateStatus) {
    return GetFunctionConfigurationResponse.builder()
        .state(state)
        .lastUpdateStatus(lastUpdateStatus)
        .codeSha256("sha")
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
