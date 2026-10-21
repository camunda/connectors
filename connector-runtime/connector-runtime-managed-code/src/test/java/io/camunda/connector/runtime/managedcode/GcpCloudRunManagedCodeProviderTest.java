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
  private static final String URI = "https://camunda-ms-abc-xyz-ew.a.run.app";

  private final FakeApi api = new FakeApi();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final GcpCloudRunManagedCodeProvider provider =
      new GcpCloudRunManagedCodeProvider(
          api,
          objectMapper,
          LOCATION,
          FUNCTION_SA,
          256,
          5,
          Duration.ofSeconds(30),
          Duration.ofSeconds(5),
          Duration.ofMillis(1));

  @Test
  void uploadsAndCreatesAFunctionNamedAfterTheArtifactWithLimits() throws IOException {
    final var artifact = artifact(ScriptLanguage.PYTHON, "python3", "calc.py");
    api.gets.add(Optional.empty());
    api.gets.add(Optional.of(function(Function.State.DEPLOYING, "")));
    api.gets.add(Optional.of(function(Function.State.ACTIVE, URI)));

    final var handle = provider.ensureProvisioned(artifact);

    assertThat(handle.ready().toCompletableFuture().join())
        .isEqualTo(new ProviderDeployment("gcp", artifact.deploymentName() + "@" + URI));
    assertThat(api.createdIds).containsExactly(artifact.deploymentName());
    final var created = api.created.getFirst();
    assertThat(created.getName()).isEqualTo(LOCATION + "/functions/" + artifact.deploymentName());
    assertThat(created.getBuildConfig().getRuntime()).isEqualTo("python313");
    assertThat(created.getBuildConfig().getEntryPoint()).isEqualTo("managed_script");
    assertThat(created.getBuildConfig().getSource().getStorageSource().getObject())
        .isEqualTo("uploaded.zip");
    assertThat(created.getServiceConfig().getServiceAccountEmail()).isEqualTo(FUNCTION_SA);
    assertThat(created.getServiceConfig().getMaxInstanceCount()).isEqualTo(5);
    assertThat(created.getServiceConfig().getAvailableMemory()).isEqualTo("256Mi");
    assertThat(created.getServiceConfig().getTimeoutSeconds()).isEqualTo(30);
    assertThat(created.getLabelsMap())
        .containsEntry(
            GcpCloudRunManagedCodeProvider.DIGEST_LABEL, artifact.digest().substring(0, 63));
    assertThat(entries(api.uploads.getFirst()))
        .containsOnlyKeys("main.py", "requirements.txt", "user_script.py")
        .containsEntry("user_script.py", "def execute(v, c):\n    return v\n");
  }

  @Test
  void adoptsAnExistingFunctionWithoutUploadingItsSourceAgain() {
    final var artifact = artifact(ScriptLanguage.JAVASCRIPT, "nodejs", "sum.js");
    api.gets.add(Optional.of(function(Function.State.ACTIVE, URI)));

    final var handle = provider.ensureProvisioned(artifact);

    assertThat(handle.ready().toCompletableFuture().join().deploymentId())
        .isEqualTo(artifact.deploymentName() + "@" + URI);
    assertThat(api.uploads).isEmpty();
    assertThat(api.createdIds).isEmpty();
  }

  @Test
  void waitsForAFunctionThatAnotherInstanceCreated() {
    final var artifact = artifact(ScriptLanguage.JAVASCRIPT, "nodejs22", "sum.js");
    api.gets.add(Optional.empty());
    api.gets.add(Optional.of(function(Function.State.ACTIVE, URI)));
    api.alreadyExists = true;

    final var handle = provider.ensureProvisioned(artifact);

    assertThat(handle.ready().toCompletableFuture().join().deploymentId()).endsWith("@" + URI);
    assertThat(api.created.getFirst().getBuildConfig().getRuntime()).isEqualTo("nodejs22");
  }

  @Test
  void keepsPollingWhenAReadIsThrottledOrNotYetAllowed() {
    final var artifact = artifact(ScriptLanguage.PYTHON, "python3", "calc.py");
    api.getFailures.add(new ProvisioningException("PLATFORM_THROTTLED", "slow down", true));
    api.getFailures.add(new ProvisioningException("PERMISSION_DENIED", "not yet", true));
    api.gets.add(Optional.of(function(Function.State.ACTIVE, URI)));

    assertThat(provider.ensureProvisioned(artifact).ready().toCompletableFuture().join())
        .isNotNull();
  }

  @Test
  void failsWithoutRetriesWhenTheBuildFails() {
    final var artifact = artifact(ScriptLanguage.PYTHON, "python3", "calc.py");
    api.gets.add(
        Optional.of(
            function(Function.State.FAILED, "").toBuilder()
                .addStateMessages(StateMessage.newBuilder().setMessage("build failed: pip"))
                .build()));

    final var failure =
        catchThrowableOfType(
            ProvisioningException.class, () -> provider.ensureProvisioned(artifact));

    assertThat(failure.code()).isEqualTo("BUILD_FAILED");
    assertThat(failure.retryable()).isFalse();
    assertThat(failure.getMessage()).contains("build failed: pip");
  }

  @Test
  void timesOutRetryablyWhileTheFunctionIsDeploying() {
    final var shortTimeout =
        new GcpCloudRunManagedCodeProvider(
            api,
            objectMapper,
            LOCATION,
            FUNCTION_SA,
            256,
            5,
            Duration.ofSeconds(30),
            Duration.ofMillis(20),
            Duration.ofMillis(5));
    api.repeatLast = Optional.of(function(Function.State.DEPLOYING, ""));

    final var failure =
        catchThrowableOfType(
            ProvisioningException.class,
            () ->
                shortTimeout.ensureProvisioned(
                    artifact(ScriptLanguage.PYTHON, "python3", "calc.py")));

    assertThat(failure.code()).isEqualTo("PROVISIONING_TIMEOUT");
    assertThat(failure.retryable()).isTrue();
  }

  @Test
  void stopsWaitingOnALastingDenialBeforeTheJobDoes() {
    final var shortTimeout =
        new GcpCloudRunManagedCodeProvider(
            api,
            objectMapper,
            LOCATION,
            FUNCTION_SA,
            256,
            5,
            Duration.ofSeconds(30),
            Duration.ofSeconds(2),
            Duration.ofMillis(1));
    api.repeatFailure = new ProvisioningException("PERMISSION_DENIED", "denied", true);

    final long start = System.nanoTime();
    final var failure =
        catchThrowableOfType(
            ProvisioningException.class,
            () ->
                shortTimeout.ensureProvisioned(
                    artifact(ScriptLanguage.PYTHON, "python3", "calc.py")));

    assertThat(failure.code()).isEqualTo("PERMISSION_DENIED");
    assertThat(failure.retryable()).isTrue();
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
  }

  @Test
  void invokesTheFunctionUrlAndReadsTheContractResponse() {
    api.invokeResult =
        new CloudRunFunctionsApi.HttpResult(
            200, "{\"contractVersion\":\"1\",\"outcome\":\"COMPLETED\",\"variables\":{\"sum\":5}}");

    final var response =
        provider.invoke(
            new ProviderDeployment("gcp", "camunda-ms-abc@" + URI),
            new ExecutionRequest("7", Map.of("a", 2), Map.of("jobKey", "7")));

    assertThat(response.outcome()).isEqualTo(ExecutionResponse.COMPLETED);
    assertThat(response.variables()).containsEntry("sum", 5);
    assertThat(api.invokedUris).containsExactly(URI);
    assertThat(new String(api.invokedBodies.getFirst(), StandardCharsets.UTF_8))
        .contains("\"contractVersion\":\"1\"")
        .contains("\"variables\":{\"a\":2}");
  }

  @Test
  void mapsHttpStatusesToContractFailures() {
    assertThat(invokeWithStatus(404).isDeploymentMissing()).isTrue();
    assertFailure(invokeWithStatus(403), "PLATFORM_UNAVAILABLE", true);
    assertFailure(invokeWithStatus(429), "PLATFORM_THROTTLED", true);
    assertFailure(invokeWithStatus(503), "PLATFORM_UNAVAILABLE", true);
    assertFailure(invokeWithStatus(504), "PLATFORM_EXECUTION_FAILED", false);
    assertFailure(invokeWithStatus(500), "PLATFORM_EXECUTION_FAILED", false);
  }

  @Test
  void reportsATokenOrNetworkFailureAsRetryable() {
    api.invokeFailure = new ProvisioningException("PLATFORM_UNAVAILABLE", "no token", true);

    assertFailure(invokeWithStatus(200), "PLATFORM_UNAVAILABLE", true);
  }

  @Test
  void deletesTheFunctionByName() {
    provider.delete(new ProviderDeployment("gcp", "camunda-ms-abc@" + URI));

    assertThat(api.deleted).containsExactly(LOCATION + "/functions/camunda-ms-abc");
  }

  @Test
  void packagesNodeWithAnEsmEntrypointAndNoDependencies() {
    final var scriptPackage =
        new GcpCloudRunScriptPackager()
            .packageFor(artifact(ScriptLanguage.JAVASCRIPT, "nodejs20", "sum.js"));

    assertThat(scriptPackage.files())
        .containsOnlyKeys("index.mjs", "package.json", "user_script.mjs");
    assertThat(new String(scriptPackage.files().get("package.json"), StandardCharsets.UTF_8))
        .contains("\"type\": \"module\"")
        .doesNotContain("dependencies");
    assertThat(scriptPackage.runtime()).isEqualTo("nodejs20");
  }

  @Test
  void rejectsAnUnsupportedRuntime() {
    final var failure =
        catchThrowableOfType(
            ProvisioningException.class,
            () ->
                new GcpCloudRunScriptPackager()
                    .packageFor(artifact(ScriptLanguage.PYTHON, "python2", "a.py")));

    assertThat(failure.code()).isEqualTo("UNSUPPORTED_RUNTIME");
  }

  @Test
  void translatesApiErrors() {
    assertTranslation(StatusCode.Code.RESOURCE_EXHAUSTED, "PLATFORM_THROTTLED", true);
    assertTranslation(StatusCode.Code.UNAVAILABLE, "PLATFORM_UNAVAILABLE", true);
    assertTranslation(StatusCode.Code.PERMISSION_DENIED, "PERMISSION_DENIED", true);
    assertTranslation(StatusCode.Code.INVALID_ARGUMENT, "PROVIDER_REJECTED", false);
  }

  private ExecutionResponse invokeWithStatus(int status) {
    api.invokeResult = new CloudRunFunctionsApi.HttpResult(status, "error");
    return provider.invoke(
        new ProviderDeployment("gcp", "camunda-ms-abc@" + URI),
        new ExecutionRequest("7", Map.of(), Map.of()));
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

  private static ArtifactSpec artifact(ScriptLanguage language, String runtime, String name) {
    final var script =
        language == ScriptLanguage.PYTHON
            ? "def execute(v, c):\n    return v\n"
            : "export function execute(v) { return v; }\n";
    return ArtifactSpec.create(
        "default",
        "tenant-a",
        "gcp",
        new GcpCloudRunScriptPackager().buildVersion(),
        ArtifactSpec.DEFAULT_DEPLOYMENT_NAME_PREFIX,
        language,
        runtime,
        name,
        script.getBytes(StandardCharsets.UTF_8),
        Optional.empty());
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
