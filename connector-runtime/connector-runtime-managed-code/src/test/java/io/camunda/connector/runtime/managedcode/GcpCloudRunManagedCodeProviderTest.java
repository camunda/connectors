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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.gax.rpc.ApiExceptionFactory;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.functions.v2.Function;
import com.google.cloud.functions.v2.ServiceConfig;
import com.google.cloud.functions.v2.StateMessage;
import com.google.cloud.functions.v2.StorageSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class GcpCloudRunManagedCodeProviderTest {

  private static final String LOCATION = "projects/p/locations/europe-west1";
  private static final String FUNCTION_SA = "fn@p.iam.gserviceaccount.com";
  private static final String PREFIX = "camunda-ms-";
  private static final String PYTHON_URI = "https://camunda-ms-py-ew.a.run.app";
  private static final String NODE_URI = "https://camunda-ms-js-ew.a.run.app";
  private static final String PYTHON_EXECUTOR =
      ExecutorPackage.gcp(ScriptLanguage.PYTHON).functionName(PREFIX);
  private static final String NODE_EXECUTOR =
      ExecutorPackage.gcp(ScriptLanguage.JAVASCRIPT).functionName(PREFIX);

  private final FakeApi api = new FakeApi();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final GcpCloudRunManagedCodeProvider provider = provider(Duration.ofSeconds(5), 1);

  @Test
  void uploadsAndCreatesOneExecutorPerLanguageWithLimits() throws IOException {
    api.gets.add(Optional.empty());
    api.gets.add(Optional.of(function(Function.State.DEPLOYING, "")));
    api.gets.add(Optional.of(function(Function.State.ACTIVE, PYTHON_URI)));
    api.gets.add(Optional.empty());
    api.gets.add(Optional.of(function(Function.State.ACTIVE, NODE_URI)));

    provider.ensureExecutors();

    assertThat(api.createdIds).containsExactly(PYTHON_EXECUTOR, NODE_EXECUTOR);
    final var python = api.created.getFirst();
    assertThat(python.getName()).isEqualTo(LOCATION + "/functions/" + PYTHON_EXECUTOR);
    assertThat(python.getBuildConfig().getRuntime()).isEqualTo("python313");
    assertThat(python.getBuildConfig().getEntryPoint()).isEqualTo("executor");
    assertThat(python.getBuildConfig().getSource().getStorageSource().getObject())
        .isEqualTo("uploaded.zip");
    assertThat(python.getServiceConfig().getServiceAccountEmail()).isEqualTo(FUNCTION_SA);
    assertThat(python.getServiceConfig().getMaxInstanceCount()).isEqualTo(5);
    assertThat(python.getServiceConfig().getMaxInstanceRequestConcurrency()).isEqualTo(1);
    assertThat(python.getServiceConfig().getAvailableMemory()).isEqualTo("256Mi");
    assertThat(python.getServiceConfig().getTimeoutSeconds()).isEqualTo(35);
    assertThat(python.getServiceConfig().getEnvironmentVariablesMap())
        .containsEntry("EXECUTOR_FUNCTION_TIMEOUT_MS", "35000");
    assertThat(python.getLabelsMap())
        .containsEntry(
            GcpCloudRunManagedCodeProvider.EXECUTOR_LABEL,
            ExecutorPackage.gcp(ScriptLanguage.PYTHON).digest().substring(0, 63));
    assertThat(entries(api.uploads.getFirst()))
        .containsOnlyKeys("executor_core.py", "child.py", "main.py", "requirements.txt");
    final var node = api.created.get(1);
    assertThat(node.getBuildConfig().getRuntime()).isEqualTo("nodejs22");
    assertThat(entries(api.uploads.get(1)))
        .containsOnlyKeys("executor_core.mjs", "child.mjs", "index.js", "package.json")
        .hasEntrySatisfying(
            "package.json", json -> assertThat(json).contains("@google-cloud/functions-framework"));
  }

  @Test
  void adoptsExistingExecutorsWithoutUploadingTheirSourceAgain() {
    api.repeatLast = Optional.of(function(Function.State.ACTIVE, PYTHON_URI));

    provider.ensureExecutors();

    assertThat(api.uploads).isEmpty();
    assertThat(api.createdIds).isEmpty();
  }

  @Test
  void waitsForAnExecutorThatAnotherInstanceCreated() {
    api.gets.add(Optional.empty());
    api.repeatLast = Optional.of(function(Function.State.ACTIVE, PYTHON_URI));
    api.alreadyExists = true;

    provider.ensureExecutors();

    assertThat(api.createdIds).containsExactly(PYTHON_EXECUTOR);
  }

  @Test
  void keepsPollingWhenAReadIsThrottledOrNotYetAllowed() {
    api.getFailures.add(new ProvisioningException("PLATFORM_THROTTLED", "slow down", true));
    api.getFailures.add(new ProvisioningException("PERMISSION_DENIED", "not yet", true));
    api.repeatLast = Optional.of(function(Function.State.ACTIVE, PYTHON_URI));

    provider.ensureExecutors();

    assertThat(api.createdIds).isEmpty();
  }

  @Test
  void failsWithoutRetriesWhenTheBuildFails() {
    api.gets.add(
        Optional.of(
            function(Function.State.FAILED, "").toBuilder()
                .addStateMessages(StateMessage.newBuilder().setMessage("build failed: pip"))
                .build()));

    final var failure =
        catchThrowableOfType(ProvisioningException.class, provider::ensureExecutors);

    assertThat(failure.code()).isEqualTo("BUILD_FAILED");
    assertThat(failure.retryable()).isFalse();
    assertThat(failure.getMessage()).contains("build failed: pip");
  }

  @Test
  void timesOutRetryablyWhileAnExecutorIsDeploying() {
    api.repeatLast = Optional.of(function(Function.State.DEPLOYING, ""));

    final var failure =
        catchThrowableOfType(
            ProvisioningException.class, provider(Duration.ofMillis(20), 5)::ensureExecutors);

    assertThat(failure.code()).isEqualTo("PROVISIONING_TIMEOUT");
    assertThat(failure.retryable()).isTrue();
  }

  @Test
  void stopsWaitingOnALastingDenial() {
    api.repeatFailure = new ProvisioningException("PERMISSION_DENIED", "denied", true);

    final long start = System.nanoTime();
    final var failure =
        catchThrowableOfType(
            ProvisioningException.class, provider(Duration.ofSeconds(2), 1)::ensureExecutors);

    assertThat(failure.code()).isEqualTo("PERMISSION_DENIED");
    assertThat(failure.retryable()).isTrue();
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
  }

  @Test
  void sendsTheScriptToTheUrlOfTheExecutorOfItsLanguage() {
    readyExecutors();
    api.invokeResult =
        new CloudRunFunctionsApi.HttpResult(
            200, "{\"contractVersion\":\"1\",\"outcome\":\"COMPLETED\",\"variables\":{\"sum\":5}}");

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
    assertThat(api.invokedUris).containsExactly(NODE_URI);
    assertThat(new String(api.invokedBodies.getFirst(), StandardCharsets.UTF_8))
        .contains("\"script\":\"export function execute() {}\"")
        .contains("\"variables\":{\"a\":2}")
        .contains("\"timeoutMs\":30000");
  }

  @Test
  void reportsAnExecutorWithoutKnownUrlAsMissing() {
    final var response = provider.execute(ScriptLanguage.PYTHON, request());

    assertThat(response.isDeploymentMissing()).isTrue();
    assertThat(api.invokedUris).isEmpty();
  }

  @Test
  void mapsHttpStatusesToContractFailures() {
    readyExecutors();
    assertFailure(invokeWithStatus(403), "PLATFORM_UNAVAILABLE", true);
    assertFailure(invokeWithStatus(429), "PLATFORM_THROTTLED", true);
    assertFailure(invokeWithStatus(503), "PLATFORM_UNAVAILABLE", true);
    assertFailure(invokeWithStatus(504), "PLATFORM_EXECUTION_FAILED", false);
    assertFailure(invokeWithStatus(500), "PLATFORM_EXECUTION_FAILED", false);
    assertThat(invokeWithStatus(404).isDeploymentMissing()).isTrue();
    // The URL is forgotten, so the executors are looked up again.
    assertThat(provider.execute(ScriptLanguage.PYTHON, request()).isDeploymentMissing()).isTrue();
    assertThat(api.invokedUris).hasSize(6);
  }

  @Test
  void reportsATokenOrNetworkFailureAsRetryable() {
    readyExecutors();
    api.invokeFailure = new ProvisioningException("PLATFORM_UNAVAILABLE", "no token", true);

    assertFailure(invokeWithStatus(200), "PLATFORM_UNAVAILABLE", true);
  }

  @Test
  void rejectsARequestLargerThanCloudRunAccepts() {
    readyExecutors();

    final var response =
        provider.execute(
            ScriptLanguage.PYTHON,
            new ExecutionRequest(
                "7",
                "x".repeat(GcpCloudRunManagedCodeProvider.MAX_REQUEST_BYTES),
                Map.of(),
                Map.of(),
                30_000));

    assertThat(response.error().code()).isEqualTo("RESOURCE_LIMIT");
    assertThat(api.invokedUris).isEmpty();
  }

  @Test
  void translatesApiErrors() {
    assertTranslation(StatusCode.Code.RESOURCE_EXHAUSTED, "PLATFORM_THROTTLED", true);
    assertTranslation(StatusCode.Code.UNAVAILABLE, "PLATFORM_UNAVAILABLE", true);
    assertTranslation(StatusCode.Code.PERMISSION_DENIED, "PERMISSION_DENIED", true);
    assertTranslation(StatusCode.Code.INVALID_ARGUMENT, "PROVIDER_REJECTED", false);
  }

  private GcpCloudRunManagedCodeProvider provider(Duration provisioningTimeout, int pollMillis) {
    return new GcpCloudRunManagedCodeProvider(
        api,
        objectMapper,
        LOCATION,
        PREFIX,
        FUNCTION_SA,
        256,
        5,
        Duration.ofSeconds(30),
        provisioningTimeout,
        Duration.ofMillis(pollMillis));
  }

  private void readyExecutors() {
    api.gets.add(Optional.of(function(Function.State.ACTIVE, PYTHON_URI)));
    api.gets.add(Optional.of(function(Function.State.ACTIVE, NODE_URI)));
    provider.ensureExecutors();
  }

  private ExecutionResponse invokeWithStatus(int status) {
    api.invokeResult = new CloudRunFunctionsApi.HttpResult(status, "error");
    return provider.execute(ScriptLanguage.PYTHON, request());
  }

  private static ExecutionRequest request() {
    return new ExecutionRequest(
        "7", "def execute(v, c):\n    return v\n", Map.of(), Map.of(), 30_000);
  }

  private static void assertFailure(ExecutionResponse response, String code, boolean retryable) {
    assertThat(response.outcome()).isEqualTo(ExecutionResponse.FAILED);
    assertThat(response.error().code()).isEqualTo(code);
    assertThat(response.error().retryable()).isEqualTo(retryable);
  }

  private static void assertTranslation(StatusCode.Code code, String expected, boolean retryable) {
    final var translated =
        GoogleCloudRunFunctionsApi.translate(
            ApiExceptionFactory.createException("boom", null, new FakeStatusCode(code), false));
    assertThat(translated.code()).isEqualTo(expected);
    assertThat(translated.retryable()).isEqualTo(retryable);
  }

  private static Function function(Function.State state, String uri) {
    return Function.newBuilder()
        .setState(state)
        .setServiceConfig(ServiceConfig.newBuilder().setUri(uri))
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

  private static final class FakeApi implements CloudRunFunctionsApi {
    final Deque<Optional<Function>> gets = new ArrayDeque<>();
    final Deque<ProvisioningException> getFailures = new ArrayDeque<>();
    Optional<Function> repeatLast;
    ProvisioningException repeatFailure;
    boolean alreadyExists;
    final List<byte[]> uploads = new ArrayList<>();
    final List<String> createdIds = new ArrayList<>();
    final List<Function> created = new ArrayList<>();
    final List<String> deleted = new ArrayList<>();
    final List<String> invokedUris = new ArrayList<>();
    final List<byte[]> invokedBodies = new ArrayList<>();
    HttpResult invokeResult;
    ProvisioningException invokeFailure;

    @Override
    public Optional<Function> get(String functionName) {
      if (!getFailures.isEmpty()) {
        throw getFailures.poll();
      }
      if (repeatFailure != null) {
        throw repeatFailure;
      }
      if (gets.isEmpty()) {
        return repeatLast;
      }
      return gets.poll();
    }

    @Override
    public StorageSource upload(byte[] zip) {
      uploads.add(zip);
      return StorageSource.newBuilder()
          .setBucket("gcf-v2-uploads")
          .setObject("uploaded.zip")
          .build();
    }

    @Override
    public boolean create(String functionId, Function function) {
      createdIds.add(functionId);
      created.add(function);
      return !alreadyExists;
    }

    @Override
    public void delete(String functionName) {
      deleted.add(functionName);
    }

    @Override
    public HttpResult invoke(String uri, byte[] body, Duration timeout) {
      if (invokeFailure != null) {
        throw invokeFailure;
      }
      invokedUris.add(uri);
      invokedBodies.add(body);
      return invokeResult;
    }

    @Override
    public void close() {}
  }

  private record FakeStatusCode(Code code) implements StatusCode {
    @Override
    public Code getCode() {
      return code;
    }

    @Override
    public Object getTransportCode() {
      return code.getHttpStatusCode();
    }
  }
}
