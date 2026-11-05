/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.gcp;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.ErrorDetails;
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.auth.oauth2.IdToken;
import com.google.auth.oauth2.IdTokenProvider;
import com.google.cloud.functions.v2.CreateFunctionRequest;
import com.google.cloud.functions.v2.Environment;
import com.google.cloud.functions.v2.Function;
import com.google.cloud.functions.v2.FunctionServiceClient;
import com.google.cloud.functions.v2.GenerateUploadUrlRequest;
import com.google.cloud.functions.v2.GenerateUploadUrlResponse;
import com.google.cloud.functions.v2.ServiceConfig;
import com.google.cloud.functions.v2.StateMessage;
import com.google.cloud.functions.v2.StorageSource;
import com.google.cloud.functions.v2.UpdateFunctionRequest;
import com.google.longrunning.Operation;
import com.google.longrunning.OperationsClient;
import com.google.protobuf.Any;
import com.google.rpc.Code;
import com.google.rpc.RetryInfo;
import com.google.rpc.Status;
import io.camunda.connector.managedscript.api.ErrorCode;
import io.camunda.connector.managedscript.api.ExecutionRequest;
import io.camunda.connector.managedscript.api.ExecutionResponse;
import io.camunda.connector.managedscript.api.ProvisioningException;
import io.camunda.connector.managedscript.api.ScriptLanguage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

@WireMockTest
class GcpCloudRunManagedCodeProviderTest {

  private static final String SERVICE_ACCOUNT = "executor@camunda-p.iam.gserviceaccount.com";
  private static final String BUILD_SERVICE_ACCOUNT = "builder@camunda-p.iam.gserviceaccount.com";
  private static final String PREFIX = "ce-0123456789abcdef01234567-";
  private static final String LOCATION = "projects/camunda-p/locations/europe-west1";
  private static final String PYTHON_EXECUTOR =
      CloudRunExecutor.of(ScriptLanguage.PYTHON).functionName(PREFIX);
  private static final String NODE_EXECUTOR =
      CloudRunExecutor.of(ScriptLanguage.JAVASCRIPT).functionName(PREFIX);

  /** 30 s of execution timeout, and the 5 s of the wrapper. */
  private static final int FUNCTION_TIMEOUT = 35;

  private static final String CONTRACT_RESPONSE =
      "{\"contractVersion\":\"1\",\"outcome\":\"COMPLETED\",\"variables\":{\"sum\":5}}";

  private final FunctionServiceClient functions = mock(FunctionServiceClient.class);
  private final OperationsClient operations = mock(OperationsClient.class);

  @SuppressWarnings("unchecked")
  private final UnaryCallable<CreateFunctionRequest, Operation> create = mock(UnaryCallable.class);

  @SuppressWarnings("unchecked")
  private final UnaryCallable<UpdateFunctionRequest, Operation> update = mock(UnaryCallable.class);

  private final IdTokenProvider idTokenProvider = mock(IdTokenProvider.class);
  private final List<Duration> sleeps = new ArrayList<>();
  private GcpCloudRunManagedCodeProvider provider;
  private IdToken idToken;
  private String url;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMock) throws IOException {
    url = wireMock.getHttpBaseUrl() + "/executor";
    idToken = idToken();
    when(functions.getOperationsClient()).thenReturn(operations);
    when(functions.createFunctionCallable()).thenReturn(create);
    when(functions.updateFunctionCallable()).thenReturn(update);
    when(idTokenProvider.idTokenWithAudience(anyString(), any())).thenReturn(idToken);
    provider = provider(Duration.ofMinutes(1));
  }

  @Test
  void createsTheFunctionFromAnUploadedSourceThenWaitsForItsOperation(WireMockRuntimeInfo wireMock)
      throws IOException {
    when(functions.getFunction(anyString())).thenThrow(error(Code.NOT_FOUND)).thenReturn(active());
    when(functions.generateUploadUrl(any(GenerateUploadUrlRequest.class)))
        .thenReturn(
            GenerateUploadUrlResponse.newBuilder()
                .setUploadUrl(wireMock.getHttpBaseUrl() + "/upload")
                .setStorageSource(
                    StorageSource.newBuilder().setBucket("gcf-v2-uploads").setObject("source.zip"))
                .build());
    stubFor(put("/upload").willReturn(ok()));
    when(create.call(any(CreateFunctionRequest.class))).thenReturn(operation("create", false));
    when(operations.getOperation("create"))
        .thenReturn(operation("create", false), operation("create", true));

    provider.ensureExecutors(ScriptLanguage.PYTHON);

    final var uploaded =
        WireMock.findAll(
                putRequestedFor(urlEqualTo("/upload"))
                    .withHeader("Content-Type", equalTo("application/zip")))
            .getFirst()
            .getBody();
    assertThat(entries(uploaded))
        .containsOnlyKeys("child.py", "executor_core.py", "main.py", "requirements.txt");
    final var created = ArgumentCaptor.forClass(CreateFunctionRequest.class);
    verify(create).call(created.capture());
    final var request = created.getValue();
    assertThat(request.getParent()).isEqualTo(LOCATION);
    assertThat(request.getFunctionId()).isEqualTo(PYTHON_EXECUTOR).hasSizeLessThanOrEqualTo(63);
    final var function = request.getFunction();
    assertThat(function.getName()).isEqualTo(LOCATION + "/functions/" + PYTHON_EXECUTOR);
    assertThat(function.getEnvironment()).isEqualTo(Environment.GEN_2);
    assertThat(function.getBuildConfig().getServiceAccount())
        .isEqualTo("projects/camunda-p/serviceAccounts/" + BUILD_SERVICE_ACCOUNT);
    assertThat(function.getLabelsMap())
        .containsEntry(
            GcpCloudRunManagedCodeProvider.DIGEST_LABEL,
            CloudRunExecutor.of(ScriptLanguage.PYTHON).digest().substring(0, 63));
    assertThat(function.getBuildConfig().getRuntime()).isEqualTo("python313");
    assertThat(function.getBuildConfig().getEntryPoint()).isEqualTo("handler");
    assertThat(function.getBuildConfig().getSource().getStorageSource().getObject())
        .isEqualTo("source.zip");
    final var settings = function.getServiceConfig();
    assertThat(settings.getAvailableMemory()).isEqualTo("512Mi");
    assertThat(settings.getTimeoutSeconds()).isEqualTo(FUNCTION_TIMEOUT);
    assertThat(settings.getMaxInstanceCount()).isEqualTo(10);
    assertThat(settings.getMaxInstanceRequestConcurrency()).isEqualTo(1);
    assertThat(settings.getServiceAccountEmail()).isEqualTo(SERVICE_ACCOUNT);
    assertThat(settings.getEnvironmentVariablesMap())
        .containsExactly(Map.entry("EXECUTOR_FUNCTION_TIMEOUT_MS", "35000"));
    assertThat(settings.getAllTrafficOnLatestRevision()).isTrue();
    verify(update, never()).call(any(UpdateFunctionRequest.class));
    assertThat(sleeps).hasSize(1);
  }

  @Test
  void packagesTheNodeExecutorWithItsPinnedFunctionsFramework() throws IOException {
    final var node = CloudRunExecutor.of(ScriptLanguage.JAVASCRIPT);
    final var python = CloudRunExecutor.of(ScriptLanguage.PYTHON);

    assertThat(node.runtime()).isEqualTo("nodejs22");
    assertThat(entries(node.zip()))
        .containsOnlyKeys("child.mjs", "executor_core.mjs", "index.mjs", "package.json");
    assertThat(new String(entries(node.zip()).get("package.json"), StandardCharsets.UTF_8))
        .contains("\"@google-cloud/functions-framework\": \"5.0.5\"")
        .contains("\"main\": \"index.mjs\"");
    assertThat(new String(entries(python.zip()).get("requirements.txt"), StandardCharsets.UTF_8))
        .isEqualTo("functions-framework==3.10.2\n");
  }

  @Test
  void adoptsAnExistingFunctionWithTheConfiguredSettingsWithoutAnyWrite() {
    when(functions.getFunction(anyString())).thenReturn(active());

    provider.ensureExecutors(ScriptLanguage.PYTHON);
    provider.ensureExecutors(ScriptLanguage.JAVASCRIPT);

    verify(functions).getFunction(LOCATION + "/functions/" + PYTHON_EXECUTOR);
    verify(functions).getFunction(LOCATION + "/functions/" + NODE_EXECUTOR);
    verify(functions, never()).generateUploadUrl(any(GenerateUploadUrlRequest.class));
    verify(create, never()).call(any(CreateFunctionRequest.class));
    verify(update, never()).call(any(UpdateFunctionRequest.class));
    assertThat(sleeps).isEmpty();
  }

  @Test
  void createsItsOwnExecutorBesideThatOfAPreviousWrapperAndNeverTouchesIt(
      WireMockRuntimeInfo wireMock) {
    // A runtime instance of the previous release still calls the executor of its own wrapper.
    final var previous = LOCATION + "/functions/" + PREFIX + "py-000000000000";
    final var own = LOCATION + "/functions/" + PYTHON_EXECUTOR;
    assertThat(previous).isNotEqualTo(own);
    when(functions.getFunction(own)).thenThrow(error(Code.NOT_FOUND)).thenReturn(active());
    uploadTo(wireMock);
    when(create.call(any(CreateFunctionRequest.class))).thenReturn(operation("create", false));
    when(operations.getOperation("create")).thenReturn(operation("create", true));
    stubFor(post("/executor").willReturn(ok(CONTRACT_RESPONSE)));

    provider.ensureExecutors(ScriptLanguage.PYTHON);
    provider.execute(ScriptLanguage.PYTHON, request("s"));

    final var created = ArgumentCaptor.forClass(CreateFunctionRequest.class);
    verify(create).call(created.capture());
    assertThat(created.getValue().getFunctionId()).isEqualTo(PYTHON_EXECUTOR);
    verify(functions, never()).getFunction(previous);
    verify(update, never()).call(any(UpdateFunctionRequest.class));
    WireMock.verify(1, postRequestedFor(urlEqualTo("/executor")));
  }

  @Test
  void adoptsAFunctionThatAnotherInstanceIsCreating(WireMockRuntimeInfo wireMock) {
    when(functions.getFunction(anyString()))
        .thenThrow(error(Code.NOT_FOUND))
        .thenReturn(function(Function.State.DEPLOYING, settings()), active());
    uploadTo(wireMock);
    when(create.call(any(CreateFunctionRequest.class))).thenThrow(error(Code.ALREADY_EXISTS));

    provider.ensureExecutors(ScriptLanguage.PYTHON);

    verify(operations, never()).getOperation(anyString());
    assertThat(sleeps).hasSize(1);
  }

  @Test
  void updatesDriftedSettingsInPlaceAsANewRevision() {
    when(functions.getFunction(anyString()))
        .thenReturn(
            function(
                Function.State.ACTIVE,
                ServiceConfig.newBuilder()
                    .setUri(url)
                    .setAvailableMemory("256Mi")
                    .setTimeoutSeconds(20)
                    .setMaxInstanceCount(3)
                    .setMaxInstanceRequestConcurrency(1)
                    .setServiceAccountEmail("old@camunda-p.iam.gserviceaccount.com")
                    .putEnvironmentVariables("LOG_EXECUTION_ID", "true")
                    .putEnvironmentVariables("EXECUTOR_FUNCTION_TIMEOUT_MS", "20000")
                    .build()),
            active());
    when(update.call(any(UpdateFunctionRequest.class))).thenReturn(operation("update", false));
    when(operations.getOperation("update")).thenReturn(operation("update", true));

    provider.ensureExecutors(ScriptLanguage.PYTHON);

    final var updated = ArgumentCaptor.forClass(UpdateFunctionRequest.class);
    verify(update).call(updated.capture());
    assertThat(updated.getValue().getUpdateMask().getPathsList())
        .containsExactlyElementsOf(GcpCloudRunManagedCodeProvider.SETTINGS);
    assertThat(updated.getValue().getFunction().getName())
        .isEqualTo(LOCATION + "/functions/" + PYTHON_EXECUTOR);
    final var settings = updated.getValue().getFunction().getServiceConfig();
    assertThat(settings.getAvailableMemory()).isEqualTo("512Mi");
    assertThat(settings.getTimeoutSeconds()).isEqualTo(FUNCTION_TIMEOUT);
    assertThat(settings.getMaxInstanceCount()).isEqualTo(10);
    assertThat(settings.getMaxInstanceRequestConcurrency()).isEqualTo(1);
    assertThat(settings.getServiceAccountEmail()).isEqualTo(SERVICE_ACCOUNT);
    assertThat(settings.getEnvironmentVariablesMap())
        .containsEntry("LOG_EXECUTION_ID", "true")
        .containsEntry("EXECUTOR_FUNCTION_TIMEOUT_MS", "35000");
    verify(functions, never()).generateUploadUrl(any(GenerateUploadUrlRequest.class));
    verify(create, never()).call(any(CreateFunctionRequest.class));
  }

  @Test
  void updatesAFunctionWhoseMaxInstanceCountDrifted() {
    when(functions.getFunction(anyString()))
        .thenReturn(
            function(Function.State.ACTIVE, settings().setMaxInstanceCount(100).build()), active());
    when(update.call(any(UpdateFunctionRequest.class))).thenReturn(operation("update", true));
    when(operations.getOperation("update")).thenReturn(operation("update", true));

    provider.ensureExecutors(ScriptLanguage.PYTHON);

    verify(update).call(any(UpdateFunctionRequest.class));
  }

  @Test
  void movesAllTrafficOfAnAdoptedFunctionToItsLatestRevision() {
    when(functions.getFunction(anyString()))
        .thenReturn(
            function(
                Function.State.ACTIVE, settings().setAllTrafficOnLatestRevision(false).build()),
            active());
    when(update.call(any(UpdateFunctionRequest.class))).thenReturn(operation("update", true));
    when(operations.getOperation("update")).thenReturn(operation("update", true));

    provider.ensureExecutors(ScriptLanguage.PYTHON);

    final var updated = ArgumentCaptor.forClass(UpdateFunctionRequest.class);
    verify(update).call(updated.capture());
    assertThat(updated.getValue().getFunction().getServiceConfig().getAllTrafficOnLatestRevision())
        .isTrue();
    assertThat(updated.getValue().getUpdateMask().getPathsList())
        .contains("service_config.all_traffic_on_latest_revision");
  }

  @Test
  void comparesTheMemoryInBytes() {
    when(functions.getFunction(anyString()))
        .thenReturn(function(Function.State.ACTIVE, settings().setAvailableMemory("536870912")));

    provider.ensureExecutors(ScriptLanguage.PYTHON);

    verify(update, never()).call(any(UpdateFunctionRequest.class));
  }

  @Test
  void waitsOutAnUpdateOfAnotherInstanceAndComparesAgain() {
    final var drifted = function(Function.State.ACTIVE, settings().setTimeoutSeconds(20));
    when(functions.getFunction(anyString())).thenReturn(drifted, active());
    when(update.call(any(UpdateFunctionRequest.class))).thenThrow(error(Code.ABORTED));

    provider.ensureExecutors(ScriptLanguage.PYTHON);

    verify(update, times(1)).call(any(UpdateFunctionRequest.class));
  }

  @Test
  void rejectsSettingsThatTheUpdateDidNotApply() {
    when(functions.getFunction(anyString()))
        .thenReturn(function(Function.State.ACTIVE, settings().setTimeoutSeconds(20)));
    when(update.call(any(UpdateFunctionRequest.class))).thenReturn(operation("update", false));
    when(operations.getOperation("update")).thenReturn(operation("update", true));

    final var failure = ensureFails(ScriptLanguage.PYTHON);

    assertThat(failure.code()).isEqualTo(ErrorCode.PLATFORM_REJECTED);
    assertThat(failure.getMessage()).contains("did not apply its new settings");
    verify(update, times(1)).call(any(UpdateFunctionRequest.class));
  }

  @Test
  void reportsTheErrorOfAFailedUpdate() {
    when(functions.getFunction(anyString()))
        .thenReturn(function(Function.State.ACTIVE, settings().setAvailableMemory("256Mi")));
    when(update.call(any(UpdateFunctionRequest.class))).thenReturn(operation("update", false));
    when(operations.getOperation("update"))
        .thenReturn(failed("update", Code.INVALID_ARGUMENT, "Invalid memory for the CPU"));

    final var failure = ensureFails(ScriptLanguage.PYTHON);

    assertThat(failure.code()).isEqualTo(ErrorCode.PLATFORM_REJECTED);
    assertThat(failure.permissionDenied()).isFalse();
    assertThat(failure.getMessage()).contains("Invalid memory for the CPU");
  }

  @Test
  void marksACreationDeniedForTheFunctionServiceAccountAsAPermissionDenial(
      WireMockRuntimeInfo wireMock) {
    when(functions.getFunction(anyString())).thenThrow(error(Code.NOT_FOUND));
    uploadTo(wireMock);
    when(create.call(any(CreateFunctionRequest.class))).thenReturn(operation("create", false));
    when(operations.getOperation("create"))
        .thenReturn(failed("create", Code.PERMISSION_DENIED, "Missing iam.serviceAccounts.actAs"));

    final var failure = ensureFails(ScriptLanguage.PYTHON);

    assertThat(failure.permissionDenied()).isTrue();
    assertThat(failure.code()).isEqualTo(ErrorCode.PLATFORM_REJECTED);
  }

  @Test
  void rejectsAFunctionThatFailedToDeploy() {
    when(functions.getFunction(anyString()))
        .thenReturn(
            function(Function.State.FAILED, settings()).toBuilder()
                .addStateMessages(StateMessage.newBuilder().setMessage("Build failed: npm ERR!"))
                .build());

    final var failure = ensureFails(ScriptLanguage.JAVASCRIPT);

    assertThat(failure.code()).isEqualTo(ErrorCode.PLATFORM_REJECTED);
    assertThat(failure.getMessage()).contains("Build failed: npm ERR!");
  }

  @Test
  void reportsAFunctionStillDeployingAfterTheActivationTimeoutAsUnavailable() {
    when(functions.getFunction(anyString()))
        .thenReturn(function(Function.State.DEPLOYING, settings()));

    final var failure =
        catchThrowableOfType(
            ProvisioningException.class,
            () -> provider(Duration.ZERO).ensureExecutors(ScriptLanguage.PYTHON));

    assertThat(failure.code()).isEqualTo(ErrorCode.PLATFORM_UNAVAILABLE);
    assertThat(failure.code().retryable()).isTrue();
    assertThat(failure.getMessage()).contains("still DEPLOYING");
  }

  @ParameterizedTest
  @ValueSource(strings = {"PERMISSION_DENIED", "UNAUTHENTICATED"})
  void marksADenialAsAPermissionDenial(Code code) {
    when(functions.getFunction(anyString())).thenThrow(error(code));

    final var failure = ensureFails(ScriptLanguage.PYTHON);

    assertThat(failure.permissionDenied()).isTrue();
    assertThat(failure.code()).isEqualTo(ErrorCode.PLATFORM_REJECTED);
  }

  @Test
  void honoursTheRetryDelayOfAThrottledRead() {
    when(functions.getFunction(anyString()))
        .thenReturn(function(Function.State.DEPLOYING, settings()))
        .thenThrow(
            new ApiException(
                "quota",
                null,
                status(StatusCode.Code.RESOURCE_EXHAUSTED),
                true,
                ErrorDetails.builder()
                    .setRawErrorMessages(
                        List.of(
                            Any.pack(
                                RetryInfo.newBuilder()
                                    .setRetryDelay(
                                        com.google.protobuf.Duration.newBuilder().setSeconds(7))
                                    .build())))
                    .build()))
        .thenReturn(active());

    provider.ensureExecutors(ScriptLanguage.PYTHON);

    // The first while the function deploys, the second for the throttled read.
    assertThat(sleeps).hasSize(2);
    assertThat(sleeps.getLast()).isBetween(Duration.ofSeconds(7), Duration.ofMillis(10_500));
  }

  @Test
  void reportsAThrottledCreationAsRetryable(WireMockRuntimeInfo wireMock) {
    when(functions.getFunction(anyString())).thenThrow(error(Code.NOT_FOUND));
    uploadTo(wireMock);
    when(create.call(any(CreateFunctionRequest.class))).thenThrow(error(Code.RESOURCE_EXHAUSTED));

    final var failure = ensureFails(ScriptLanguage.PYTHON);

    assertThat(failure.code()).isEqualTo(ErrorCode.PLATFORM_THROTTLED);
    assertThat(failure.code().retryable()).isTrue();
  }

  @Test
  void reportsAServiceErrorAsRetryable() {
    when(functions.getFunction(anyString())).thenThrow(error(Code.UNAVAILABLE));

    final var failure = ensureFails(ScriptLanguage.PYTHON);

    assertThat(failure.code()).isEqualTo(ErrorCode.PLATFORM_UNAVAILABLE);
    assertThat(failure.permissionDenied()).isFalse();
  }

  @Test
  void rejectsAnyOtherClientError(WireMockRuntimeInfo wireMock) {
    when(functions.getFunction(anyString())).thenThrow(error(Code.NOT_FOUND));
    uploadTo(wireMock);
    when(create.call(any(CreateFunctionRequest.class))).thenThrow(error(Code.INVALID_ARGUMENT));

    final var failure = ensureFails(ScriptLanguage.PYTHON);

    assertThat(failure.code()).isEqualTo(ErrorCode.PLATFORM_REJECTED);
    assertThat(failure.permissionDenied()).isFalse();
  }

  @Test
  void reportsAFailedUploadByItsStatusWithoutCreatingTheFunction(WireMockRuntimeInfo wireMock) {
    when(functions.getFunction(anyString())).thenThrow(error(Code.NOT_FOUND));
    uploadTo(wireMock);
    stubFor(put("/upload").willReturn(aResponse().withStatus(503)));

    final var failure = ensureFails(ScriptLanguage.PYTHON);

    assertThat(failure.code()).isEqualTo(ErrorCode.PLATFORM_UNAVAILABLE);
    verify(create, never()).call(any(CreateFunctionRequest.class));
  }

  @ParameterizedTest
  @ValueSource(ints = {401, 403})
  void reportsADeniedUploadAsAPermissionDenial(int status, WireMockRuntimeInfo wireMock) {
    when(functions.getFunction(anyString())).thenThrow(error(Code.NOT_FOUND));
    uploadTo(wireMock);
    stubFor(put("/upload").willReturn(aResponse().withStatus(status)));

    final var failure = ensureFails(ScriptLanguage.PYTHON);

    assertThat(failure.code()).isEqualTo(ErrorCode.PLATFORM_REJECTED);
    assertThat(failure.permissionDenied()).isTrue();
    verify(create, never()).call(any(CreateFunctionRequest.class));
  }

  @Test
  void sendsTheScriptToTheUrlOfTheExecutorWithAnIdTokenThatCarriesTheEmail() throws IOException {
    ready(ScriptLanguage.JAVASCRIPT);
    stubFor(post("/executor").willReturn(ok(CONTRACT_RESPONSE)));

    final var response = provider.execute(ScriptLanguage.JAVASCRIPT, request("export x"));

    assertThat(response.outcome()).isEqualTo(ExecutionResponse.COMPLETED);
    assertThat(response.variables()).containsEntry("sum", 5);
    final var sent = WireMock.findAll(postRequestedFor(urlEqualTo("/executor"))).getFirst();
    assertThat(sent.getHeader("Authorization")).isEqualTo("Bearer " + idToken.getTokenValue());
    assertThat(sent.getHeader("Content-Type")).isEqualTo("application/json");
    assertThat(sent.getBodyAsString())
        .contains("\"contractVersion\":\"1\"")
        .contains("\"script\":\"export x\"")
        .contains("\"timeoutMs\":30000");
    verify(idTokenProvider).idTokenWithAudience(url, List.of(IdTokenProvider.Option.INCLUDE_EMAIL));
  }

  @Test
  void reportsAnExecutorThatWasNotMadeReadyAsDeploymentMissing() {
    final var response = provider.execute(ScriptLanguage.PYTHON, request("s"));

    assertThat(response.isDeploymentMissing()).isTrue();
    WireMock.verify(0, postRequestedFor(urlEqualTo("/executor")));
  }

  @Test
  void rejectsARequestLargerThanACloudRunRequestWithoutCallingIt() {
    ready(ScriptLanguage.PYTHON);
    // Escaped in JSON, each newline takes two bytes.
    final var script = "\n".repeat(GcpCloudRunManagedCodeProvider.MAX_REQUEST_BYTES / 2);

    final var response = provider.execute(ScriptLanguage.PYTHON, request(script));

    assertThat(response.error().code()).isEqualTo(ErrorCode.RESOURCE_LIMIT.name());
    assertThat(response.error().retryable()).isFalse();
    WireMock.verify(0, postRequestedFor(urlEqualTo("/executor")));
  }

  @Test
  void reportsAMissingFunctionAsDeploymentMissingAndForgetsItsUrl() {
    ready(ScriptLanguage.PYTHON);
    stubFor(post("/executor").willReturn(aResponse().withStatus(404)));

    assertThat(provider.execute(ScriptLanguage.PYTHON, request("s")).isDeploymentMissing())
        .isTrue();
    assertThat(provider.execute(ScriptLanguage.PYTHON, request("s")).isDeploymentMissing())
        .isTrue();
    WireMock.verify(1, postRequestedFor(urlEqualTo("/executor")));
  }

  @Test
  void keepsTheUrlMadeReadyAgainWhenALateMissingFunctionAnswers() throws Exception {
    ready(ScriptLanguage.PYTHON);
    stubFor(post("/executor").willReturn(aResponse().withStatus(404).withFixedDelay(1_000)));
    final var late =
        CompletableFuture.supplyAsync(() -> provider.execute(ScriptLanguage.PYTHON, request("s")));
    final long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (WireMock.findAll(postRequestedFor(urlEqualTo("/executor"))).isEmpty()
        && System.nanoTime() < deadline) {
      Thread.onSpinWait();
    }

    // Another job got its own 404 meanwhile, and the executor was made ready again, at the same
    // URL.
    provider.ensureExecutors(ScriptLanguage.PYTHON);
    assertThat(late).isNotDone();
    assertThat(late.get(5, TimeUnit.SECONDS).isDeploymentMissing()).isTrue();
    stubFor(post("/executor").willReturn(ok(CONTRACT_RESPONSE)));

    assertThat(provider.execute(ScriptLanguage.PYTHON, request("s")).outcome())
        .isEqualTo(ExecutionResponse.COMPLETED);
    WireMock.verify(2, postRequestedFor(urlEqualTo("/executor")));
  }

  @ParameterizedTest
  @ValueSource(ints = {401, 403})
  void throwsADeniedInvocationAsAPermissionDenial(int status) {
    ready(ScriptLanguage.PYTHON);
    stubFor(post("/executor").willReturn(aResponse().withStatus(status)));

    assertThatThrownBy(() -> provider.execute(ScriptLanguage.PYTHON, request("s")))
        .isInstanceOfSatisfying(
            ProvisioningException.class,
            failure -> assertThat(failure.permissionDenied()).isTrue());
  }

  @ParameterizedTest
  @CsvSource({
    "429, PLATFORM_THROTTLED, true",
    "502, PLATFORM_UNAVAILABLE, true",
    "503, PLATFORM_UNAVAILABLE, true",
    "500, PLATFORM_EXECUTION_FAILED, false",
    "504, PLATFORM_EXECUTION_FAILED, false",
    "413, RESOURCE_LIMIT, false",
    "400, PLATFORM_REJECTED, false"
  })
  void mapsAnAnswerOfThePlatformWithoutCallingAgain(int status, ErrorCode code, boolean retryable) {
    ready(ScriptLanguage.PYTHON);
    stubFor(post("/executor").willReturn(aResponse().withStatus(status).withBody("upstream")));

    final var response = provider.execute(ScriptLanguage.PYTHON, request("s"));

    assertThat(response.error().code()).isEqualTo(code.name());
    assertThat(response.error().retryable()).isEqualTo(retryable);
    WireMock.verify(exactly(1), postRequestedFor(urlEqualTo("/executor")));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "<html>Service Unavailable</html>",
        "{}",
        "[]",
        "{\"outcome\":\"COMPLETED\"}",
        "{\"contractVersion\":\"1\",\"outcome\":\"FAILED\"}"
      })
  void reportsAnAnswerOutsideTheContractAsAPlatformExecutionFailure(String body) {
    ready(ScriptLanguage.PYTHON);
    stubFor(post("/executor").willReturn(ok(body)));

    final var response = provider.execute(ScriptLanguage.PYTHON, request("s"));

    assertThat(response.error().code()).isEqualTo(ErrorCode.PLATFORM_EXECUTION_FAILED.name());
    assertThat(response.error().retryable()).isFalse();
  }

  @Test
  void reportsAConnectionLostAfterTheRequestAsAPlatformExecutionFailure() {
    ready(ScriptLanguage.PYTHON);
    stubFor(post("/executor").willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    final var response = provider.execute(ScriptLanguage.PYTHON, request("s"));

    assertThat(response.error().code()).isEqualTo(ErrorCode.PLATFORM_EXECUTION_FAILED.name());
    WireMock.verify(exactly(1), postRequestedFor(urlEqualTo("/executor")));
  }

  @Test
  void reportsAnExecutorThatCannotBeReachedAsUnavailable() {
    // Nothing listens on port 1.
    url = "http://127.0.0.1:1/executor";
    ready(ScriptLanguage.PYTHON);

    final var response = provider.execute(ScriptLanguage.PYTHON, request("s"));

    assertThat(response.error().code()).isEqualTo(ErrorCode.PLATFORM_UNAVAILABLE.name());
    assertThat(response.error().retryable()).isTrue();
  }

  @Test
  void throwsADeniedIdTokenAsAPermissionDenial() throws IOException {
    ready(ScriptLanguage.PYTHON);
    when(idTokenProvider.idTokenWithAudience(anyString(), any()))
        .thenThrow(new IOException("Error code 403 trying to getIDToken: Permission denied"));

    assertThatThrownBy(() -> provider.execute(ScriptLanguage.PYTHON, request("s")))
        .isInstanceOfSatisfying(
            ProvisioningException.class,
            failure -> assertThat(failure.permissionDenied()).isTrue());
    WireMock.verify(0, postRequestedFor(urlEqualTo("/executor")));
  }

  @Test
  void reportsAnIdTokenThatCannotBeMintedAsUnavailable() throws IOException {
    ready(ScriptLanguage.PYTHON);
    when(idTokenProvider.idTokenWithAudience(anyString(), any()))
        .thenThrow(new IOException("Unexpected Error code 503 trying to getIDToken"));

    final var response = provider.execute(ScriptLanguage.PYTHON, request("s"));

    assertThat(response.error().code()).isEqualTo(ErrorCode.PLATFORM_UNAVAILABLE.name());
  }

  @Test
  void givesTheWrapperFiveSecondsToEndALateScriptItself() {
    assertThat(GcpCloudRunManagedCodeProvider.functionTimeoutSeconds(Duration.ofSeconds(30)))
        .isEqualTo(35);
    assertThat(GcpCloudRunManagedCodeProvider.functionTimeoutSeconds(Duration.ofMillis(30_001)))
        .isEqualTo(36);
    assertThat(GcpCloudRunManagedCodeProvider.functionTimeoutSeconds(Duration.ofSeconds(3595)))
        .isEqualTo(3600);
    assertThatThrownBy(
            () -> GcpCloudRunManagedCodeProvider.functionTimeoutSeconds(Duration.ofSeconds(3596)))
        .hasMessageContaining("at most 3595 seconds");
  }

  @Test
  void packagesDeterministicallyWithNamesThatFitTheLongestPrefix() {
    final var executor = CloudRunExecutor.of(ScriptLanguage.JAVASCRIPT);

    assertThat(executor.zip()).isEqualTo(CloudRunExecutor.of(ScriptLanguage.JAVASCRIPT).zip());
    assertThat(executor.functionName("a".repeat(48))).hasSize(63);
    assertThat(executor.digest()).isNotEqualTo(CloudRunExecutor.of(ScriptLanguage.PYTHON).digest());
  }

  private GcpCloudRunManagedCodeProvider provider(Duration activationTimeout) {
    return new GcpCloudRunManagedCodeProvider(
        functions,
        GcpClients.http(),
        idTokenProvider,
        new ObjectMapper(),
        PREFIX,
        new GcpManagedCodeProperties(
            "camunda-p", "europe-west1", SERVICE_ACCOUNT, BUILD_SERVICE_ACCOUNT, null, 512, 10),
        Duration.ofSeconds(30),
        activationTimeout,
        Duration.ofMillis(100),
        sleeps::add);
  }

  /** Makes the executor of the language ready, served at {@link #url}. */
  private void ready(ScriptLanguage language) {
    when(functions.getFunction(anyString())).thenReturn(active());
    provider.ensureExecutors(language);
  }

  private void uploadTo(WireMockRuntimeInfo wireMock) {
    when(functions.generateUploadUrl(any(GenerateUploadUrlRequest.class)))
        .thenReturn(
            GenerateUploadUrlResponse.newBuilder()
                .setUploadUrl(wireMock.getHttpBaseUrl() + "/upload")
                .build());
    stubFor(put("/upload").willReturn(ok()));
  }

  private ProvisioningException ensureFails(ScriptLanguage language) {
    return catchThrowableOfType(
        ProvisioningException.class, () -> provider.ensureExecutors(language));
  }

  private Function active() {
    return function(Function.State.ACTIVE, settings());
  }

  /** The configured settings, as Cloud Run reports them, with the variable it adds. */
  private ServiceConfig.Builder settings() {
    return ServiceConfig.newBuilder()
        .setUri(url)
        .setAvailableMemory("512Mi")
        .setTimeoutSeconds(FUNCTION_TIMEOUT)
        .setMaxInstanceCount(10)
        .setMaxInstanceRequestConcurrency(1)
        .setServiceAccountEmail(SERVICE_ACCOUNT)
        .setAllTrafficOnLatestRevision(true)
        .putEnvironmentVariables("LOG_EXECUTION_ID", "true")
        .putEnvironmentVariables("EXECUTOR_FUNCTION_TIMEOUT_MS", "35000");
  }

  private static Function function(Function.State state, ServiceConfig.Builder settings) {
    return function(state, settings.build());
  }

  private static Function function(Function.State state, ServiceConfig settings) {
    return Function.newBuilder().setState(state).setServiceConfig(settings).build();
  }

  private static Operation operation(String name, boolean done) {
    return Operation.newBuilder().setName(name).setDone(done).build();
  }

  private static Operation failed(String name, Code code, String message) {
    return Operation.newBuilder()
        .setName(name)
        .setDone(true)
        .setError(Status.newBuilder().setCode(code.getNumber()).setMessage(message))
        .build();
  }

  private static ApiException error(Code code) {
    return new ApiException(code.name(), null, status(StatusCode.Code.valueOf(code.name())), false);
  }

  private static StatusCode status(StatusCode.Code code) {
    return new StatusCode() {
      @Override
      public StatusCode.Code getCode() {
        return code;
      }

      @Override
      public Object getTransportCode() {
        return code;
      }
    };
  }

  private static ExecutionRequest request(String script) {
    return new ExecutionRequest("7", script, Map.of("a", 2), Map.of("jobKey", "7"), 30_000);
  }

  /** An unsigned token, which the client only decodes for its expiry. */
  private static IdToken idToken() throws IOException {
    final var encoder = Base64.getUrlEncoder().withoutPadding();
    final var header =
        encoder.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8));
    final var payload =
        encoder.encodeToString(
            "{\"email\":\"deployer@camunda-p.iam.gserviceaccount.com\",\"exp\":%d}"
                .formatted(Instant.now().plusSeconds(3600).getEpochSecond())
                .getBytes(StandardCharsets.UTF_8));
    return IdToken.create(header + "." + payload + ".c2lnbmF0dXJl");
  }

  private static Map<String, byte[]> entries(byte[] zip) throws IOException {
    final Map<String, byte[]> entries = new TreeMap<>();
    try (var input = new ZipInputStream(new ByteArrayInputStream(zip))) {
      for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
        entries.put(entry.getName(), input.readAllBytes());
      }
    }
    return entries;
  }
}
