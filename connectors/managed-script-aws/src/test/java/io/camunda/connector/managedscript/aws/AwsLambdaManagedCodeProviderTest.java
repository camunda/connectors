/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.managedscript.api.ErrorCode;
import io.camunda.connector.managedscript.api.ExecutionRequest;
import io.camunda.connector.managedscript.api.ExecutionResponse;
import io.camunda.connector.managedscript.api.ExecutorReference;
import io.camunda.connector.managedscript.api.ProvisioningException;
import io.camunda.connector.managedscript.api.ScriptLanguage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.CloudWatchLogsException;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogGroupRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutRetentionPolicyRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.ResourceAlreadyExistsException;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.lambda.model.GetFunctionRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.lambda.model.InvalidParameterValueException;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.LambdaException;
import software.amazon.awssdk.services.lambda.model.LastUpdateStatus;
import software.amazon.awssdk.services.lambda.model.ResourceConflictException;
import software.amazon.awssdk.services.lambda.model.ResourceNotFoundException;
import software.amazon.awssdk.services.lambda.model.State;
import software.amazon.awssdk.services.lambda.model.TooManyRequestsException;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionConfigurationRequest;

class AwsLambdaManagedCodeProviderTest {

  private static final String ROLE = "arn:aws:iam::000000000000:role/executor";
  private static final String PREFIX = "ce-0123456789abcdef01234567-";
  private static final String PYTHON_EXECUTOR =
      LambdaExecutor.of(ScriptLanguage.PYTHON).functionName(PREFIX);
  private static final String NODE_EXECUTOR =
      LambdaExecutor.of(ScriptLanguage.JAVASCRIPT).functionName(PREFIX);

  private static final String STS_DENIAL =
      "Not authorized to perform sts:AssumeRoleWithWebIdentity (Service: Sts, Status Code: 403)";

  /** 30 s of execution timeout, and the 5 s of the wrapper. */
  private static final int FUNCTION_TIMEOUT = 35;

  private final LambdaClient lambda = mock(LambdaClient.class);
  private final LambdaClient invoker = mock(LambdaClient.class);
  private final AwsCredentialsProvider credentials = mock(AwsCredentialsProvider.class);
  private final CloudWatchLogsClient logs = mock(CloudWatchLogsClient.class);
  private final List<Duration> sleeps = new ArrayList<>();
  private final AwsLambdaManagedCodeProvider provider = provider(Duration.ofMinutes(1));

  @Test
  void createsTheLogGroupWithItsRetentionBeforeTheFunctionThenWaitsUntilItIsActive()
      throws IOException {
    when(lambda.getFunction(any(GetFunctionRequest.class)))
        .thenThrow(notFound())
        .thenReturn(function(State.PENDING, null, 512, FUNCTION_TIMEOUT, ROLE))
        .thenReturn(
            function(State.ACTIVE, LastUpdateStatus.SUCCESSFUL, 512, FUNCTION_TIMEOUT, ROLE));

    provider.ensureExecutors(ScriptLanguage.PYTHON);

    final var order = inOrder(logs, lambda);
    order.verify(logs).createLogGroup(any(CreateLogGroupRequest.class));
    order.verify(logs).putRetentionPolicy(any(PutRetentionPolicyRequest.class));
    order.verify(lambda).createFunction(any(CreateFunctionRequest.class));
    final var logGroup = ArgumentCaptor.forClass(PutRetentionPolicyRequest.class);
    verify(logs).putRetentionPolicy(logGroup.capture());
    assertThat(logGroup.getValue().logGroupName()).isEqualTo("/aws/lambda/" + PYTHON_EXECUTOR);
    assertThat(logGroup.getValue().retentionInDays()).isEqualTo(14);
    final var created = ArgumentCaptor.forClass(CreateFunctionRequest.class);
    verify(lambda).createFunction(created.capture());
    final var request = created.getValue();
    assertThat(request.functionName()).isEqualTo(PYTHON_EXECUTOR).hasSizeLessThanOrEqualTo(64);
    assertThat(request.runtimeAsString()).isEqualTo("python3.13");
    assertThat(request.handler()).isEqualTo("lambda_handler.handler");
    assertThat(request.packageTypeAsString()).isEqualTo("Zip");
    assertThat(request.role()).isEqualTo(ROLE);
    assertThat(request.timeout()).isEqualTo(FUNCTION_TIMEOUT);
    assertThat(request.memorySize()).isEqualTo(512);
    assertThat(request.tags())
        .containsEntry(
            AwsLambdaManagedCodeProvider.DIGEST_TAG,
            LambdaExecutor.of(ScriptLanguage.PYTHON).digest());
    assertThat(entries(request.code().zipFile().asByteArray()))
        .containsOnlyKeys("child.py", "executor_core.py", "lambda_handler.py");
    verify(lambda, never())
        .updateFunctionConfiguration(any(UpdateFunctionConfigurationRequest.class));
    assertThat(sleeps).hasSize(1);
  }

  @Test
  void packagesTheNodeExecutorWithItsLambdaEntryPoint() throws IOException {
    when(lambda.getFunction(any(GetFunctionRequest.class)))
        .thenThrow(notFound())
        .thenReturn(
            function(State.ACTIVE, LastUpdateStatus.SUCCESSFUL, 512, FUNCTION_TIMEOUT, ROLE));

    provider.ensureExecutors(ScriptLanguage.JAVASCRIPT);

    final var created = ArgumentCaptor.forClass(CreateFunctionRequest.class);
    verify(lambda).createFunction(created.capture());
    assertThat(created.getValue().functionName()).isEqualTo(NODE_EXECUTOR);
    assertThat(created.getValue().runtimeAsString()).isEqualTo("nodejs22.x");
    assertThat(created.getValue().handler()).isEqualTo("index.handler");
    assertThat(entries(created.getValue().code().zipFile().asByteArray()))
        .containsOnlyKeys("child.mjs", "executor_core.mjs", "index.mjs");
  }

  @Test
  void keepsALogGroupThatAnotherInstanceCreated() {
    when(logs.createLogGroup(any(CreateLogGroupRequest.class)))
        .thenThrow(ResourceAlreadyExistsException.builder().message("exists").build());
    when(lambda.getFunction(any(GetFunctionRequest.class)))
        .thenThrow(notFound())
        .thenReturn(
            function(State.ACTIVE, LastUpdateStatus.SUCCESSFUL, 512, FUNCTION_TIMEOUT, ROLE));

    provider.ensureExecutors(ScriptLanguage.PYTHON);

    verify(logs).putRetentionPolicy(any(PutRetentionPolicyRequest.class));
    verify(lambda).createFunction(any(CreateFunctionRequest.class));
  }

  @Test
  void honoursTheRetryAfterOfAThrottledStatusRead() {
    when(lambda.getFunction(any(GetFunctionRequest.class)))
        .thenThrow(notFound())
        .thenThrow(
            TooManyRequestsException.builder()
                .message("slow down")
                .statusCode(429)
                .retryAfterSeconds("7")
                .build())
        .thenReturn(
            function(State.ACTIVE, LastUpdateStatus.SUCCESSFUL, 512, FUNCTION_TIMEOUT, ROLE));

    provider.ensureExecutors(ScriptLanguage.PYTHON);

    assertThat(sleeps).hasSize(1);
    assertThat(sleeps.getFirst()).isBetween(Duration.ofSeconds(7), Duration.ofMillis(10_500));
  }

  @Test
  void adoptsAFunctionThatAnotherInstanceCreatedAndUpdatesItsDriftedSettings() {
    when(lambda.getFunction(any(GetFunctionRequest.class)))
        .thenThrow(notFound())
        .thenReturn(
            function(State.ACTIVE, LastUpdateStatus.SUCCESSFUL, 256, 20, "arn:old"),
            function(State.ACTIVE, LastUpdateStatus.IN_PROGRESS, 512, FUNCTION_TIMEOUT, ROLE),
            function(State.ACTIVE, LastUpdateStatus.SUCCESSFUL, 512, FUNCTION_TIMEOUT, ROLE));
    when(lambda.createFunction(any(CreateFunctionRequest.class)))
        .thenThrow(ResourceConflictException.builder().message("exists").build());

    provider.ensureExecutors(ScriptLanguage.PYTHON);

    final var update = ArgumentCaptor.forClass(UpdateFunctionConfigurationRequest.class);
    verify(lambda).updateFunctionConfiguration(update.capture());
    assertThat(update.getValue().functionName()).isEqualTo(PYTHON_EXECUTOR);
    assertThat(update.getValue().memorySize()).isEqualTo(512);
    assertThat(update.getValue().timeout()).isEqualTo(FUNCTION_TIMEOUT);
    assertThat(update.getValue().role()).isEqualTo(ROLE);
  }

  @Test
  void adoptsAnExistingFunctionWithTheConfiguredSettingsWithoutAnyWrite() {
    when(lambda.getFunction(any(GetFunctionRequest.class)))
        .thenReturn(
            function(State.ACTIVE, LastUpdateStatus.SUCCESSFUL, 512, FUNCTION_TIMEOUT, ROLE));

    provider.ensureExecutors(ScriptLanguage.PYTHON);
    provider.ensureExecutors(ScriptLanguage.JAVASCRIPT);

    verify(lambda, never()).createFunction(any(CreateFunctionRequest.class));
    verify(lambda, never())
        .updateFunctionConfiguration(any(UpdateFunctionConfigurationRequest.class));
    verify(logs, never()).createLogGroup(any(CreateLogGroupRequest.class));
    assertThat(sleeps).isEmpty();
  }

  @Test
  void reportsTheExecutorOfItsWrapperPerLanguageWithItsLogGroupWithoutCallingAws() {
    assertThat(provider.executors())
        .containsExactlyInAnyOrder(
            new ExecutorReference(
                ScriptLanguage.PYTHON, PYTHON_EXECUTOR, "/aws/lambda/" + PYTHON_EXECUTOR),
            new ExecutorReference(
                ScriptLanguage.JAVASCRIPT, NODE_EXECUTOR, "/aws/lambda/" + NODE_EXECUTOR));
    assertThat(PYTHON_EXECUTOR).matches(PREFIX + "py-[0-9a-f]{12}");
    verifyNoInteractions(lambda, invoker, logs);
  }

  @Test
  void createsItsOwnExecutorBesideThatOfAPreviousWrapperAndNeverTouchesIt() {
    // A runtime instance of the previous release still calls the executor of its own wrapper.
    final var previous = PREFIX + "py-000000000000";
    assertThat(previous).isNotEqualTo(PYTHON_EXECUTOR);
    when(lambda.getFunction(GetFunctionRequest.builder().functionName(PYTHON_EXECUTOR).build()))
        .thenThrow(notFound())
        .thenReturn(
            function(State.ACTIVE, LastUpdateStatus.SUCCESSFUL, 512, FUNCTION_TIMEOUT, ROLE));
    when(invoker.invoke(any(InvokeRequest.class)))
        .thenReturn(
            InvokeResponse.builder()
                .statusCode(200)
                .payload(
                    SdkBytes.fromUtf8String(
                        "{\"contractVersion\":\"1\",\"outcome\":\"COMPLETED\",\"variables\":{}}"))
                .build());

    provider.ensureExecutors(ScriptLanguage.PYTHON);
    provider.execute(ScriptLanguage.PYTHON, request("s"));

    final var created = ArgumentCaptor.forClass(CreateFunctionRequest.class);
    verify(lambda).createFunction(created.capture());
    assertThat(created.getValue().functionName()).isEqualTo(PYTHON_EXECUTOR);
    final var read = ArgumentCaptor.forClass(GetFunctionRequest.class);
    verify(lambda, atLeastOnce()).getFunction(read.capture());
    assertThat(read.getAllValues())
        .extracting(GetFunctionRequest::functionName)
        .containsOnly(PYTHON_EXECUTOR);
    verify(lambda, never())
        .updateFunctionConfiguration(any(UpdateFunctionConfigurationRequest.class));
    final var invoked = ArgumentCaptor.forClass(InvokeRequest.class);
    verify(invoker).invoke(invoked.capture());
    assertThat(invoked.getValue().functionName()).isEqualTo(PYTHON_EXECUTOR);
  }

  @Test
  void waitsOutAnUpdateOfAnotherInstanceAndComparesAgain() {
    when(lambda.getFunction(any(GetFunctionRequest.class)))
        .thenReturn(
            function(State.ACTIVE, LastUpdateStatus.SUCCESSFUL, 256, FUNCTION_TIMEOUT, ROLE),
            function(State.ACTIVE, LastUpdateStatus.SUCCESSFUL, 256, FUNCTION_TIMEOUT, ROLE),
            function(State.ACTIVE, LastUpdateStatus.SUCCESSFUL, 512, FUNCTION_TIMEOUT, ROLE));
    when(lambda.updateFunctionConfiguration(any(UpdateFunctionConfigurationRequest.class)))
        .thenThrow(ResourceConflictException.builder().message("update in progress").build());

    provider.ensureExecutors(ScriptLanguage.PYTHON);

    verify(lambda, times(1))
        .updateFunctionConfiguration(any(UpdateFunctionConfigurationRequest.class));
  }

  @Test
  void rejectsSettingsThatLambdaFailedToApply() {
    when(lambda.getFunction(any(GetFunctionRequest.class)))
        .thenReturn(
            function(State.ACTIVE, LastUpdateStatus.SUCCESSFUL, 256, FUNCTION_TIMEOUT, ROLE),
            function(State.ACTIVE, LastUpdateStatus.FAILED, 256, FUNCTION_TIMEOUT, ROLE));

    final var failure = ensureFails(ScriptLanguage.PYTHON);

    assertThat(failure.code()).isEqualTo(ErrorCode.PLATFORM_REJECTED);
    assertThat(failure.permissionDenied()).isFalse();
  }

  @Test
  void rejectsAFunctionThatFailedToBecomeActive() {
    when(lambda.getFunction(any(GetFunctionRequest.class)))
        .thenThrow(notFound())
        .thenReturn(
            GetFunctionResponse.builder()
                .configuration(
                    FunctionConfiguration.builder()
                        .state(State.FAILED)
                        .stateReason("cannot create the network interface")
                        .build())
                .build());

    final var failure = ensureFails(ScriptLanguage.PYTHON);

    assertThat(failure.code()).isEqualTo(ErrorCode.PLATFORM_REJECTED);
    assertThat(failure.getMessage()).contains("cannot create the network interface");
  }

  @Test
  void reportsAFunctionStillPendingAfterTheActivationTimeoutAsUnavailable() {
    when(lambda.getFunction(any(GetFunctionRequest.class)))
        .thenReturn(function(State.PENDING, null, 512, FUNCTION_TIMEOUT, ROLE));

    final var failure =
        catchThrowableOfType(
            ProvisioningException.class,
            () -> provider(Duration.ZERO).ensureExecutors(ScriptLanguage.PYTHON));

    assertThat(failure.code()).isEqualTo(ErrorCode.PLATFORM_UNAVAILABLE);
    assertThat(failure.code().retryable()).isTrue();
    assertThat(failure.getMessage()).contains("still Pending");
  }

  @Test
  void marksAnAccessDeniedAsAPermissionDenial() {
    when(lambda.getFunction(any(GetFunctionRequest.class)))
        .thenThrow(serviceError(403, "AccessDeniedException"));

    final var failure = ensureFails(ScriptLanguage.PYTHON);

    assertThat(failure.permissionDenied()).isTrue();
    assertThat(failure.code()).isEqualTo(ErrorCode.PLATFORM_REJECTED);
  }

  @Test
  void marksADeniedLogGroupAsAPermissionDenial() {
    when(lambda.getFunction(any(GetFunctionRequest.class))).thenThrow(notFound());
    when(logs.createLogGroup(any(CreateLogGroupRequest.class)))
        .thenThrow(
            software.amazon.awssdk.services.cloudwatchlogs.model.CloudWatchLogsException.builder()
                .statusCode(400)
                .awsErrorDetails(
                    AwsErrorDetails.builder().errorCode("AccessDeniedException").build())
                .message("not authorized")
                .build());

    assertThat(ensureFails(ScriptLanguage.PYTHON).permissionDenied()).isTrue();
    verify(lambda, never()).createFunction(any(CreateFunctionRequest.class));
  }

  @Test
  void marksARoleThatLambdaCannotAssumeYetAsAPermissionDenial() {
    when(lambda.getFunction(any(GetFunctionRequest.class))).thenThrow(notFound());
    when(lambda.createFunction(any(CreateFunctionRequest.class)))
        .thenThrow(
            InvalidParameterValueException.builder()
                .statusCode(400)
                .message("The role defined for the function cannot be assumed by Lambda.")
                .build());

    assertThat(ensureFails(ScriptLanguage.PYTHON).permissionDenied()).isTrue();
  }

  @Test
  void reportsAThrottledCreationAsRetryable() {
    when(lambda.getFunction(any(GetFunctionRequest.class))).thenThrow(notFound());
    when(lambda.createFunction(any(CreateFunctionRequest.class)))
        .thenThrow(TooManyRequestsException.builder().statusCode(429).message("slow").build());

    final var failure = ensureFails(ScriptLanguage.PYTHON);

    assertThat(failure.code()).isEqualTo(ErrorCode.PLATFORM_THROTTLED);
    assertThat(failure.code().retryable()).isTrue();
  }

  @Test
  void reportsAConcurrentRetentionUpdateAsRetryable() {
    when(lambda.getFunction(any(GetFunctionRequest.class))).thenThrow(notFound());
    when(logs.putRetentionPolicy(any(PutRetentionPolicyRequest.class)))
        .thenThrow(
            CloudWatchLogsException.builder()
                .statusCode(400)
                .awsErrorDetails(
                    AwsErrorDetails.builder()
                        .errorCode("OperationAbortedException")
                        .errorMessage("A conflicting operation is currently in progress")
                        .build())
                .build());

    final var failure = ensureFails(ScriptLanguage.PYTHON);

    assertThat(failure.code()).isEqualTo(ErrorCode.PLATFORM_UNAVAILABLE);
    assertThat(failure.code().retryable()).isTrue();
  }

  @Test
  void reportsAServiceErrorAsRetryable() {
    when(lambda.getFunction(any(GetFunctionRequest.class)))
        .thenThrow(serviceError(503, "ServiceException"));

    final var failure = ensureFails(ScriptLanguage.PYTHON);

    assertThat(failure.code()).isEqualTo(ErrorCode.PLATFORM_UNAVAILABLE);
    assertThat(failure.permissionDenied()).isFalse();
  }

  @Test
  void reportsAnUnreachableServiceAsRetryable() {
    when(lambda.getFunction(any(GetFunctionRequest.class)))
        .thenThrow(SdkClientException.create("Unable to execute HTTP request"));

    assertThat(ensureFails(ScriptLanguage.PYTHON).code()).isEqualTo(ErrorCode.PLATFORM_UNAVAILABLE);
  }

  @Test
  void marksCredentialsThatNeverResolvedAsAPermissionDenialThatNamesTheWebIdentityFailure() {
    // The chain keeps only the messages of its providers, not their exceptions.
    final var chain =
        chain(new ScriptedCredentials().thenThrow(stsError(403, "AccessDenied", STS_DENIAL)));

    final var failure =
        catchThrowableOfType(
            ProvisioningException.class,
            () -> provider(chain).ensureExecutors(ScriptLanguage.PYTHON));

    assertThat(failure.permissionDenied()).isTrue();
    assertThat(failure.getMessage())
        .startsWith(
            "The credentials of the runtime cannot be resolved to make Lambda executor '%s' ready: %s"
                .formatted(PYTHON_EXECUTOR, STS_DENIAL));
    verify(lambda, never()).getFunction(any(GetFunctionRequest.class));
  }

  @Test
  void namesAMissingTokenFileOfTheSdkWebIdentityProviderFirst() {
    final var chain =
        AwsCredentialsProviderChain.of(
            new ScriptedCredentials("EnvironmentVariableCredentialsProvider()")
                .thenThrow(SdkClientException.create("Unable to load credentials.")),
            WebIdentityTokenFileCredentialsProvider.builder()
                .webIdentityTokenFile(Path.of("/nonexistent/qa/token"))
                .roleArn("arn:aws:iam::000000000000:role/runtime")
                .build());

    final var failure =
        catchThrowableOfType(
            ProvisioningException.class,
            () -> provider(chain).ensureExecutors(ScriptLanguage.PYTHON));

    assertThat(failure.permissionDenied()).isTrue();
    assertThat(failure.getMessage())
        .startsWith(
            "The credentials of the runtime cannot be resolved to make Lambda executor '%s' ready: "
                .formatted(PYTHON_EXECUTOR))
        .doesNotStartWith(
            "The credentials of the runtime cannot be resolved to make Lambda executor '%s' ready: Unable to load credentials from any"
                .formatted(PYTHON_EXECUTOR))
        .contains("/nonexistent/qa/token");
  }

  @Test
  void quotesTheWholeChainWhenNoWebIdentityIsConfigured() {
    final var chain =
        chain(
            new ScriptedCredentials()
                .thenThrow(
                    SdkClientException.create(
                        "Either the environment variable AWS_WEB_IDENTITY_TOKEN_FILE or the"
                            + " javaproperty aws.webIdentityTokenFile must be set.")));

    final var failure =
        catchThrowableOfType(
            ProvisioningException.class,
            () -> provider(chain).ensureExecutors(ScriptLanguage.PYTHON));

    assertThat(failure.permissionDenied()).isTrue();
    assertThat(failure.getMessage())
        .contains(": Unable to load credentials from any of the providers in the chain");
  }

  @Test
  void marksADeniedRefreshOfResolvedCredentialsAsAPermissionDenial() {
    final var chain =
        chain(
            new ScriptedCredentials()
                .thenSucceed()
                .thenThrow(stsError(403, "AccessDenied", STS_DENIAL)));
    chain.resolveCredentials();
    final var provider = provider(chain);

    final var provisioning =
        catchThrowableOfType(
            ProvisioningException.class, () -> provider.ensureExecutors(ScriptLanguage.PYTHON));
    final var invocation =
        catchThrowableOfType(
            ProvisioningException.class,
            () -> provider.execute(ScriptLanguage.PYTHON, request("s")));

    assertThat(provisioning.permissionDenied()).isTrue();
    assertThat(provisioning.getMessage()).contains(STS_DENIAL);
    assertThat(invocation.permissionDenied()).isTrue();
    verify(invoker, never()).invoke(any(InvokeRequest.class));
  }

  @Test
  void marksALostTokenFileOfResolvedCredentialsAsAPermissionDenialThatNamesTheFile() {
    // The SDK reports the token file that its refresh cannot read as an unchecked I/O failure.
    final var chain =
        chain(
            new ScriptedCredentials()
                .thenSucceed()
                .thenThrow(
                    new UncheckedIOException(
                        new NoSuchFileException("/var/run/secrets/eks/token"))));
    chain.resolveCredentials();
    final var provider = provider(chain);

    final var provisioning =
        catchThrowableOfType(
            ProvisioningException.class, () -> provider.ensureExecutors(ScriptLanguage.PYTHON));
    final var invocation =
        catchThrowableOfType(
            ProvisioningException.class,
            () -> provider.execute(ScriptLanguage.PYTHON, request("s")));

    assertThat(provisioning.permissionDenied()).isTrue();
    assertThat(provisioning.getMessage())
        .contains("web-identity token file")
        .contains("/var/run/secrets/eks/token");
    assertThat(invocation.permissionDenied()).isTrue();
    assertThat(invocation.getMessage())
        .contains("web-identity token file")
        .contains("/var/run/secrets/eks/token");
    verify(invoker, never()).invoke(any(InvokeRequest.class));
  }

  @Test
  void rejectsAnInvalidWebIdentityTokenOfResolvedCredentials() {
    final var chain =
        chain(
            new ScriptedCredentials()
                .thenSucceed()
                .thenThrow(stsError(400, "InvalidIdentityToken", "Incorrect token audience")));
    chain.resolveCredentials();

    final var failure =
        catchThrowableOfType(
            ProvisioningException.class,
            () -> provider(chain).ensureExecutors(ScriptLanguage.PYTHON));

    assertThat(failure.code()).isEqualTo(ErrorCode.PLATFORM_REJECTED);
    assertThat(failure.permissionDenied()).isFalse();
    assertThat(failure.getMessage()).contains("Incorrect token audience");
  }

  @Test
  void reportsAnUnreachableCredentialsServiceAsRetryable() {
    final var chain =
        chain(
            new ScriptedCredentials()
                .thenSucceed()
                .thenThrow(
                    SdkClientException.create(
                        "Unable to execute HTTP request",
                        new SocketTimeoutException("Read timed out"))));
    chain.resolveCredentials();

    final var failure =
        catchThrowableOfType(
            ProvisioningException.class,
            () -> provider(chain).ensureExecutors(ScriptLanguage.PYTHON));

    assertThat(failure.code()).isEqualTo(ErrorCode.PLATFORM_UNAVAILABLE);
    assertThat(failure.permissionDenied()).isFalse();
  }

  @Test
  void rejectsAnyOtherClientError() {
    when(lambda.getFunction(any(GetFunctionRequest.class))).thenThrow(notFound());
    when(lambda.createFunction(any(CreateFunctionRequest.class)))
        .thenThrow(
            InvalidParameterValueException.builder()
                .statusCode(400)
                .message("Unsupported runtime")
                .build());

    final var failure = ensureFails(ScriptLanguage.PYTHON);

    assertThat(failure.code()).isEqualTo(ErrorCode.PLATFORM_REJECTED);
    assertThat(failure.permissionDenied()).isFalse();
  }

  @Test
  void sendsTheScriptToTheExecutorOfItsLanguage() {
    when(invoker.invoke(any(InvokeRequest.class)))
        .thenReturn(
            InvokeResponse.builder()
                .statusCode(200)
                .payload(
                    SdkBytes.fromUtf8String(
                        "{\"contractVersion\":\"1\",\"outcome\":\"COMPLETED\",\"variables\":{\"sum\":5}}"))
                .build());

    final var response = provider.execute(ScriptLanguage.JAVASCRIPT, request("export x"));

    assertThat(response.outcome()).isEqualTo(ExecutionResponse.COMPLETED);
    assertThat(response.variables()).containsEntry("sum", 5);
    final var invoked = ArgumentCaptor.forClass(InvokeRequest.class);
    verify(invoker).invoke(invoked.capture());
    assertThat(invoked.getValue().functionName()).isEqualTo(NODE_EXECUTOR);
    assertThat(invoked.getValue().invocationTypeAsString()).isEqualTo("RequestResponse");
    assertThat(invoked.getValue().payload().asUtf8String())
        .contains("\"contractVersion\":\"1\"")
        .contains("\"script\":\"export x\"")
        .contains("\"timeoutMs\":30000");
    verify(lambda, never()).invoke(any(InvokeRequest.class));
  }

  @Test
  void rejectsARequestLargerThanALambdaInvocationWithoutCallingIt() {
    // Escaped in JSON, each newline takes two bytes.
    final var script = "\n".repeat(AwsLambdaManagedCodeProvider.MAX_REQUEST_BYTES / 2);

    final var response = provider.execute(ScriptLanguage.PYTHON, request(script));

    assertThat(response.error().code()).isEqualTo(ErrorCode.RESOURCE_LIMIT.name());
    assertThat(response.error().retryable()).isFalse();
    verify(invoker, never()).invoke(any(InvokeRequest.class));
  }

  @Test
  void reportsAMissingExecutorAsDeploymentMissing() {
    when(invoker.invoke(any(InvokeRequest.class))).thenThrow(notFound());

    final var response = provider.execute(ScriptLanguage.PYTHON, request("s"));

    assertThat(response.isDeploymentMissing()).isTrue();
  }

  @Test
  void reportsAThrottledInvocationAsThrottled() {
    when(invoker.invoke(any(InvokeRequest.class)))
        .thenThrow(TooManyRequestsException.builder().statusCode(429).message("rate").build());

    final var response = provider.execute(ScriptLanguage.PYTHON, request("s"));

    assertThat(response.error().code()).isEqualTo(ErrorCode.PLATFORM_THROTTLED.name());
    assertThat(response.error().retryable()).isTrue();
  }

  @Test
  void reportsAnInvocationOfAFunctionBeingReactivatedAsUnavailable() {
    when(invoker.invoke(any(InvokeRequest.class)))
        .thenThrow(serviceError(409, "ResourceConflictException"));

    final var response = provider.execute(ScriptLanguage.PYTHON, request("s"));

    assertThat(response.error().code()).isEqualTo(ErrorCode.PLATFORM_UNAVAILABLE.name());
    assertThat(response.error().retryable()).isTrue();
  }

  @Test
  void reportsAServiceErrorOfAnInvocationAsUnavailable() {
    when(invoker.invoke(any(InvokeRequest.class)))
        .thenThrow(serviceError(502, "ResourceNotReadyException"));

    final var response = provider.execute(ScriptLanguage.PYTHON, request("s"));

    assertThat(response.error().code()).isEqualTo(ErrorCode.PLATFORM_UNAVAILABLE.name());
  }

  @Test
  void throwsADeniedInvocationAsAPermissionDenial() {
    when(invoker.invoke(any(InvokeRequest.class)))
        .thenThrow(serviceError(403, "AccessDeniedException"));

    assertThatThrownBy(() -> provider.execute(ScriptLanguage.PYTHON, request("s")))
        .isInstanceOfSatisfying(
            ProvisioningException.class,
            failure -> assertThat(failure.permissionDenied()).isTrue());
  }

  @Test
  void reportsAFunctionErrorAsAPlatformExecutionFailure() {
    when(invoker.invoke(any(InvokeRequest.class)))
        .thenReturn(
            InvokeResponse.builder()
                .statusCode(200)
                .functionError("Unhandled")
                .payload(SdkBytes.fromUtf8String("{\"errorMessage\":\"Task timed out\"}"))
                .build());

    final var response = provider.execute(ScriptLanguage.PYTHON, request("s"));

    assertThat(response.error().code()).isEqualTo(ErrorCode.PLATFORM_EXECUTION_FAILED.name());
    assertThat(response.error().retryable()).isFalse();
    assertThat(response.error().message()).contains("Task timed out");
  }

  @Test
  void reportsAnInvocationThatCouldNotConnectAsUnavailable() {
    when(invoker.invoke(any(InvokeRequest.class)))
        .thenThrow(
            SdkClientException.create(
                "Unable to execute HTTP request", new ConnectException("Connection refused")));

    final var response = provider.execute(ScriptLanguage.PYTHON, request("s"));

    assertThat(response.error().code()).isEqualTo(ErrorCode.PLATFORM_UNAVAILABLE.name());
    assertThat(response.error().retryable()).isTrue();
  }

  @Test
  void reportsCredentialsThatCannotBeResolvedAsUnavailableWithoutCalling() {
    // A read timeout of the metadata or STS call, which within the invocation would look sent.
    when(credentials.resolveCredentials())
        .thenThrow(
            SdkClientException.create(
                "Unable to load credentials from any of the providers in the chain",
                new SocketTimeoutException("Read timed out")));

    final var response = provider.execute(ScriptLanguage.PYTHON, request("s"));

    assertThat(response.error().code()).isEqualTo(ErrorCode.PLATFORM_UNAVAILABLE.name());
    assertThat(response.error().retryable()).isTrue();
    verify(invoker, never()).invoke(any(InvokeRequest.class));
  }

  @Test
  void reportsAnInvocationThatGotNoPooledConnectionAsUnavailable() {
    // The Apache client waits for a free connection while the others run scripts.
    when(invoker.invoke(any(InvokeRequest.class)))
        .thenThrow(
            SdkClientException.create(
                "Unable to execute HTTP request",
                new ConnectionRequestTimeoutException("Timeout deadline: 3000 MILLISECONDS")));

    final var response = provider.execute(ScriptLanguage.PYTHON, request("s"));

    assertThat(response.error().code()).isEqualTo(ErrorCode.PLATFORM_UNAVAILABLE.name());
    assertThat(response.error().retryable()).isTrue();
  }

  @Test
  void reportsACallCutByTheRuntimeStoppingAsInterrupted() {
    // Closing the provider closes the invocation client under the running call.
    when(invoker.invoke(any(InvokeRequest.class)))
        .thenAnswer(invocation -> {
          provider.close();
          throw SdkClientException.create(
              "Unable to execute HTTP request: Socket closed",
              new SocketException("Socket closed"));
        });

    final var response = provider.execute(ScriptLanguage.PYTHON, request("s"));

    assertThat(response.error().code()).isEqualTo(ErrorCode.PLATFORM_INTERRUPTED.name());
    assertThat(ErrorCode.PLATFORM_INTERRUPTED.retries()).isEqualTo(ErrorCode.Retries.UNCHANGED);
  }

  @Test
  void reportsAnInvocationLostAfterItWasSentAsAPlatformExecutionFailure() {
    when(invoker.invoke(any(InvokeRequest.class)))
        .thenThrow(
            SdkClientException.create(
                "Unable to execute HTTP request", new SocketTimeoutException("Read timed out")));

    final var response = provider.execute(ScriptLanguage.PYTHON, request("s"));

    assertThat(response.error().code()).isEqualTo(ErrorCode.PLATFORM_EXECUTION_FAILED.name());
    assertThat(response.error().retryable()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "not json",
        "{\"outcome\":\"COMPLETED\"}",
        "{\"contractVersion\":\"1\",\"outcome\":\"FAILED\"}"
      })
  void reportsAResponseOutsideTheContractAsAPlatformExecutionFailure(String body) {
    when(invoker.invoke(any(InvokeRequest.class)))
        .thenReturn(
            InvokeResponse.builder()
                .statusCode(200)
                .payload(SdkBytes.fromUtf8String(body))
                .build());

    final var response = provider.execute(ScriptLanguage.PYTHON, request("s"));

    assertThat(response.error().code()).isEqualTo(ErrorCode.PLATFORM_EXECUTION_FAILED.name());
  }

  @Test
  void givesTheWrapperFiveSecondsToEndALateScriptItself() {
    assertThat(AwsLambdaManagedCodeProvider.functionTimeoutSeconds(Duration.ofSeconds(30)))
        .isEqualTo(35);
    assertThat(AwsLambdaManagedCodeProvider.functionTimeoutSeconds(Duration.ofMillis(30_001)))
        .isEqualTo(36);
    assertThat(AwsLambdaManagedCodeProvider.functionTimeoutSeconds(Duration.ofSeconds(895)))
        .isEqualTo(900);
    assertThatThrownBy(
            () -> AwsLambdaManagedCodeProvider.functionTimeoutSeconds(Duration.ofSeconds(896)))
        .hasMessageContaining("at most 895 seconds");
  }

  @Test
  void packagesDeterministicallyWithNamesThatFitTheLongestPrefix() {
    final var executor = LambdaExecutor.of(ScriptLanguage.JAVASCRIPT);

    assertThat(executor.zip()).isEqualTo(LambdaExecutor.of(ScriptLanguage.JAVASCRIPT).zip());
    assertThat(executor.functionName("a".repeat(48))).hasSize(63);
    assertThat(executor.digest()).isNotEqualTo(LambdaExecutor.of(ScriptLanguage.PYTHON).digest());
  }

  private AwsLambdaManagedCodeProvider provider(Duration activationTimeout) {
    return provider(credentials, activationTimeout);
  }

  private AwsLambdaManagedCodeProvider provider(AwsCredentialsProvider credentials) {
    return provider(credentials, Duration.ofMinutes(1));
  }

  private AwsLambdaManagedCodeProvider provider(
      AwsCredentialsProvider credentials, Duration activationTimeout) {
    return new AwsLambdaManagedCodeProvider(
        lambda,
        invoker,
        credentials,
        logs,
        new ObjectMapper(),
        PREFIX,
        new AwsManagedCodeProperties(null, ROLE, 512, 14),
        Duration.ofSeconds(30),
        activationTimeout,
        Duration.ofMillis(100),
        sleeps::add);
  }

  /** The providers of the SDK's default chain around web identity, which never resolve here. */
  private static AwsCredentialsProviderChain chain(ScriptedCredentials webIdentity) {
    return AwsCredentialsProviderChain.of(
        new ScriptedCredentials("EnvironmentVariableCredentialsProvider()")
            .thenThrow(
                SdkClientException.create("Unable to load credentials from system settings.")),
        webIdentity,
        new ScriptedCredentials("InstanceProfileCredentialsProvider()")
            .thenThrow(
                SdkClientException.create(
                    "Failed to load credentials from IMDS.",
                    new ConnectException("Connection refused"))));
  }

  private static AwsServiceException stsError(int status, String code, String message) {
    return AwsServiceException.builder()
        .statusCode(status)
        .awsErrorDetails(
            AwsErrorDetails.builder()
                .serviceName("Sts")
                .errorCode(code)
                .errorMessage(message)
                .build())
        .message(message)
        .build();
  }

  private ProvisioningException ensureFails(ScriptLanguage language) {
    return catchThrowableOfType(
        ProvisioningException.class, () -> provider.ensureExecutors(language));
  }

  private static ExecutionRequest request(String script) {
    return new ExecutionRequest("7", script, Map.of("a", 2), Map.of("jobKey", "7"), 30_000);
  }

  private static ResourceNotFoundException notFound() {
    return ResourceNotFoundException.builder().statusCode(404).message("not found").build();
  }

  private static AwsServiceException serviceError(int status, String code) {
    return LambdaException.builder()
        .statusCode(status)
        .awsErrorDetails(AwsErrorDetails.builder().errorCode(code).errorMessage(code).build())
        .message(code)
        .build();
  }

  private static GetFunctionResponse function(
      State state, LastUpdateStatus lastUpdateStatus, int memory, int timeout, String role) {
    return GetFunctionResponse.builder()
        .configuration(
            FunctionConfiguration.builder()
                .state(state)
                .lastUpdateStatus(lastUpdateStatus)
                .memorySize(memory)
                .timeout(timeout)
                .role(role)
                .build())
        .build();
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

  /**
   * A provider of the chain that fails, or succeeds, as scripted, then repeats its last outcome.
   */
  private static final class ScriptedCredentials implements AwsCredentialsProvider {
    private final String name;
    private final List<RuntimeException> outcomes = new ArrayList<>();

    ScriptedCredentials() {
      this("WebIdentityTokenFileCredentialsProvider()");
    }

    ScriptedCredentials(String name) {
      this.name = name;
    }

    ScriptedCredentials thenSucceed() {
      outcomes.add(null);
      return this;
    }

    ScriptedCredentials thenThrow(RuntimeException failure) {
      outcomes.add(failure);
      return this;
    }

    @Override
    public AwsCredentials resolveCredentials() {
      final var failure = outcomes.size() > 1 ? outcomes.removeFirst() : outcomes.getFirst();
      if (failure != null) {
        throw failure;
      }
      return AwsBasicCredentials.create("AKIA", "secret");
    }

    @Override
    public String toString() {
      return name;
    }
  }

  /** Named as the pool timeout of the Apache client, which this module does not depend on. */
  private static final class ConnectionRequestTimeoutException extends IOException {
    ConnectionRequestTimeoutException(String message) {
      super(message);
    }
  }
}
