/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.runner;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.managedscript.api.ErrorCode;
import io.camunda.connector.managedscript.api.ExecutionRequest;
import io.camunda.connector.managedscript.api.ExecutionResponse;
import io.camunda.connector.managedscript.api.ScriptLanguage;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@WireMockTest
class RunnerProviderTest {

  private static final String TOKEN = "eyJhbGciOiJSUzI1NiJ9.first-token.signature";

  private static final ExecutionRequest REQUEST =
      new ExecutionRequest(
          "2251799813685249",
          "def main(variables, context): ...",
          Map.of("order", Map.of("total", 42)),
          Map.of("processInstanceKey", "2251799813685249"),
          10_000);

  @TempDir Path tokenDir;

  private Path tokenPath;
  private WireMock runner;
  private RunnerProvider provider;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMock) throws IOException {
    tokenPath = tokenDir.resolve("token");
    Files.writeString(tokenPath, TOKEN + "\n");
    runner = wireMock.getWireMock();
    provider = provider(wireMock.getHttpBaseUrl());
  }

  @Test
  void isNamedRunner() {
    assertThat(provider.name()).isEqualTo("runner");
  }

  @Test
  void hasNoExecutorsToMakeReady() {
    provider.ensureExecutors();

    assertThat(runner.find(WireMock.anyRequestedFor(WireMock.anyUrl()))).isEmpty();
  }

  @Test
  void sendsTheLanguageTheRuntimeAndThePayloadOfTheRequest() {
    stubAnswer(aResponse().withStatus(200).withBody(completed()));

    provider.execute(ScriptLanguage.PYTHON, REQUEST);

    runner.verifyThat(
        1,
        postRequestedFor(urlEqualTo("/v1/executions"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(
                equalToJson(
                    """
                    {
                      "language": "python",
                      "runtime": "python-3.13",
                      "contractVersion": "1",
                      "executionId": "2251799813685249",
                      "script": "def main(variables, context): ...",
                      "variables": {"order": {"total": 42}},
                      "context": {"processInstanceKey": "2251799813685249"},
                      "timeoutMs": 10000
                    }
                    """)));
  }

  @Test
  void keepsTheLanguageTheRuntimeAndTheTokenWhateverTheInputHolds() {
    stubAnswer(aResponse().withStatus(200).withBody(completed()));
    final var request =
        new ExecutionRequest(
            "1",
            "x",
            Map.of("language", "javascript", "runtime", "../../host", "Authorization", "Bearer x"),
            Map.of(),
            1_000);

    provider.execute(ScriptLanguage.PYTHON, request);

    runner.verifyThat(
        1,
        postRequestedFor(urlEqualTo("/v1/executions"))
            .withHeader("Authorization", equalTo("Bearer " + TOKEN))
            .withRequestBody(WireMock.matchingJsonPath("$.language", equalTo("python")))
            .withRequestBody(WireMock.matchingJsonPath("$.runtime", equalTo("python-3.13"))));
  }

  @Test
  void sendsTheContentOfTheTokenFileAsABearerToken() {
    stubAnswer(aResponse().withStatus(200).withBody(completed()));

    provider.execute(ScriptLanguage.PYTHON, REQUEST);

    runner.verifyThat(
        1,
        postRequestedFor(urlEqualTo("/v1/executions"))
            .withHeader("Authorization", equalTo("Bearer " + TOKEN)));
  }

  @Test
  void readsTheTokenFileAgainForEachCall() throws IOException {
    stubAnswer(aResponse().withStatus(200).withBody(completed()));
    provider.execute(ScriptLanguage.PYTHON, REQUEST);
    Files.writeString(tokenPath, "refreshed-token");

    provider.execute(ScriptLanguage.PYTHON, REQUEST);

    runner.verifyThat(
        1,
        postRequestedFor(urlEqualTo("/v1/executions"))
            .withHeader("Authorization", equalTo("Bearer " + TOKEN)));
    runner.verifyThat(
        1,
        postRequestedFor(urlEqualTo("/v1/executions"))
            .withHeader("Authorization", equalTo("Bearer refreshed-token")));
  }

  @Test
  void returnsTheExecutionResponseOfTheRunnerWithItsLogs() {
    stubAnswer(
        aResponse()
            .withStatus(200)
            .withBody(
                """
                {"contractVersion": "1", "outcome": "FAILED",
                 "error": {"code": "SCRIPT_ERROR", "message": "KeyError: 'total'",
                           "stackTrace": "Traceback ...", "retryable": false},
                 "logs": "loaded 3 order lines\\n", "logsTruncated": true}
                """));

    final var response = provider.execute(ScriptLanguage.PYTHON, REQUEST);

    assertThat(response.outcome()).isEqualTo(ExecutionResponse.FAILED);
    assertThat(response.error().code()).isEqualTo("SCRIPT_ERROR");
    assertThat(response.error().stackTrace()).isEqualTo("Traceback ...");
    assertThat(response.logs()).isEqualTo("loaded 3 order lines\n");
    assertThat(response.logsTruncated()).isTrue();
  }

  @Test
  void returnsTheVariablesOfACompletedScript() {
    stubAnswer(aResponse().withStatus(200).withBody(completed()));

    final var response = provider.execute(ScriptLanguage.PYTHON, REQUEST);

    assertThat(response.outcome()).isEqualTo(ExecutionResponse.COMPLETED);
    assertThat(response.variables()).isEqualTo(Map.of("sum", 5));
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 401, 403})
  void reportsARefusalOfTheRunnerAsRejected(int status) {
    stubAnswer(aResponse().withStatus(status).withBody("token " + TOKEN + " is not valid"));

    final var response = provider.execute(ScriptLanguage.PYTHON, REQUEST);

    assertFailed(response, ErrorCode.PLATFORM_REJECTED);
    assertThat(response.error().message()).contains(Integer.toString(status));
  }

  @ParameterizedTest
  @ValueSource(ints = {500, 502, 503, 504})
  void reportsAServerErrorOfTheRunnerAsUnavailable(int status) {
    stubAnswer(aResponse().withStatus(status));

    assertFailed(provider.execute(ScriptLanguage.PYTHON, REQUEST), ErrorCode.PLATFORM_UNAVAILABLE);
  }

  @Test
  void reportsAThrottledRunnerAsThrottled() {
    stubAnswer(aResponse().withStatus(429));

    assertFailed(provider.execute(ScriptLanguage.PYTHON, REQUEST), ErrorCode.PLATFORM_THROTTLED);
  }

  @ParameterizedTest
  @ValueSource(ints = {302, 404})
  void reportsAnUnexpectedAnswerAsAPlatformErrorWithoutFollowingIt(int status) {
    stubAnswer(aResponse().withStatus(status).withHeader("Location", "/elsewhere"));

    assertFailed(provider.execute(ScriptLanguage.PYTHON, REQUEST), ErrorCode.PLATFORM_ERROR);
    assertThat(runner.find(WireMock.anyRequestedFor(urlPathEqualTo("/elsewhere")))).isEmpty();
  }

  @Test
  void reportsARefusedConnectionAsUnavailable() throws IOException {
    final int closedPort;
    try (var socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }
    final var unreachable = provider("http://localhost:" + closedPort);

    assertFailed(
        unreachable.execute(ScriptLanguage.PYTHON, REQUEST), ErrorCode.PLATFORM_UNAVAILABLE);
  }

  @Test
  void reportsAResetConnectionAsUnavailable() {
    stubAnswer(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));

    assertFailed(provider.execute(ScriptLanguage.PYTHON, REQUEST), ErrorCode.PLATFORM_UNAVAILABLE);
  }

  @Test
  void reportsARunnerThatDoesNotAnswerInTimeAsUnavailable(WireMockRuntimeInfo wireMock) {
    stubAnswer(aResponse().withStatus(200).withBody(completed()).withFixedDelay(2_000));
    final var impatient =
        new RunnerProvider(properties(wireMock.getHttpBaseUrl()), Duration.ofMillis(100));
    final var request = new ExecutionRequest("1", "x", Map.of(), Map.of(), 100);

    assertFailed(impatient.execute(ScriptLanguage.PYTHON, request), ErrorCode.PLATFORM_UNAVAILABLE);
  }

  @Test
  void ignoresTheProxyOfTheJvm(WireMockRuntimeInfo wireMock) throws IOException {
    stubAnswer(aResponse().withStatus(200).withBody(completed()));
    final int closedPort;
    try (var socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }
    final var jvmProxy = ProxySelector.getDefault();
    try {
      ProxySelector.setDefault(ProxySelector.of(new InetSocketAddress("localhost", closedPort)));
      final var response =
          provider(wireMock.getHttpBaseUrl()).execute(ScriptLanguage.PYTHON, REQUEST);

      assertThat(response.outcome()).isEqualTo(ExecutionResponse.COMPLETED);
    } finally {
      ProxySelector.setDefault(jvmProxy);
    }
  }

  @Test
  void reportsAnAnswerThatIsNotAnExecutionResponseAsAnInvalidResult() {
    stubAnswer(aResponse().withStatus(200).withBody("<html>queue-proxy</html>"));

    assertFailed(provider.execute(ScriptLanguage.PYTHON, REQUEST), ErrorCode.INVALID_RESULT);
  }

  @Test
  void reportsAnAnswerAboveTheSizeLimitAsAnInvalidResult() {
    final var padding = "x".repeat(RunnerProvider.MAX_RESPONSE_BYTES);
    stubAnswer(
        aResponse()
            .withStatus(200)
            .withBody(
                "{\"contractVersion\": \"1\", \"outcome\": \"COMPLETED\", \"variables\": {\"p\": \""
                    + padding
                    + "\"}}"));

    assertFailed(provider.execute(ScriptLanguage.PYTHON, REQUEST), ErrorCode.INVALID_RESULT);
  }

  @Test
  void rejectsALanguageWithoutARuntimeAndSendsNothing() {
    final var response = provider.execute(ScriptLanguage.JAVASCRIPT, REQUEST);

    assertFailed(response, ErrorCode.PLATFORM_REJECTED);
    assertThat(response.error().message()).contains("javascript");
    assertThat(runner.find(WireMock.anyRequestedFor(WireMock.anyUrl()))).isEmpty();
  }

  @Test
  void reportsAMissingTokenFileAsAPlatformErrorAndSendsNothing() throws IOException {
    Files.delete(tokenPath);

    assertFailed(provider.execute(ScriptLanguage.PYTHON, REQUEST), ErrorCode.PLATFORM_ERROR);
    assertThat(runner.find(WireMock.anyRequestedFor(WireMock.anyUrl()))).isEmpty();
  }

  @Test
  void reportsAnEmptyTokenFileAsAPlatformErrorAndSendsNothing() throws IOException {
    Files.writeString(tokenPath, "\n");

    assertFailed(provider.execute(ScriptLanguage.PYTHON, REQUEST), ErrorCode.PLATFORM_ERROR);
    assertThat(runner.find(WireMock.anyRequestedFor(WireMock.anyUrl()))).isEmpty();
  }

  private RunnerProvider provider(String url) {
    return new RunnerProvider(properties(url));
  }

  private RunnerProviderProperties properties(String url) {
    return new RunnerProviderProperties(url, tokenPath.toString(), Map.of("python", "python-3.13"));
  }

  private void stubAnswer(com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder answer) {
    runner.register(post(urlEqualTo("/v1/executions")).willReturn(answer));
  }

  private static String completed() {
    return """
    {"contractVersion": "1", "outcome": "COMPLETED", "variables": {"sum": 5},
     "logs": "", "logsTruncated": false}
    """;
  }

  /** A failure raised outside the script, with the retries of its code and never the token. */
  private static void assertFailed(ExecutionResponse response, ErrorCode code) {
    assertThat(response.outcome()).isEqualTo(ExecutionResponse.FAILED);
    assertThat(response.error().code()).isEqualTo(code.name());
    assertThat(response.error().retryable()).isEqualTo(code.retryable());
    assertThat(response.error().stackTrace()).isNull();
    assertThat(response.error().message()).isNotBlank().doesNotContain(TOKEN);
  }
}
