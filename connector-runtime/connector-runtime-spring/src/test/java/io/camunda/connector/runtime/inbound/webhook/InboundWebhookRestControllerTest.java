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
package io.camunda.connector.runtime.inbound.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextImpl;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.ProcessElementWithRuntimeData;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivityLogRegistry;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.core.inbound.correlation.StartEventCorrelationPoint;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InboundWebhookRestControllerTest {

  @Test
  void shouldFailFastOnNegativeMaxRequestBodyBytes() {
    var controller = new InboundWebhookRestController(new WebhookConnectorRegistry());
    controller.maxRequestBodyBytes = -1;

    assertThatThrownBy(controller::validateWebhookConfig).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldFailFastOnNonFiniteRateLimit() {
    var controller = new InboundWebhookRestController(new WebhookConnectorRegistry());
    controller.rateLimitEnabled = true;
    controller.rateLimitPermitsPerSecond = Double.NaN;

    assertThatThrownBy(controller::validateWebhookConfig).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldLogRequestDetailsWithRedactionAndTruncation() throws Exception {
    var activityLogRegistry = new ActivityLogRegistry();
    var connector = buildConnector(activityLogRegistry);
    var webhookConnectorRegistry = new WebhookConnectorRegistry();
    webhookConnectorRegistry.register(connector);
    var controller = new InboundWebhookRestController(webhookConnectorRegistry);

    var request = new MockHttpServletRequest();
    request.setScheme("https");
    request.setServerName("example.com");
    request.setServerPort(443);
    request.setRequestURI("/inbound/myPath");
    request.setQueryString("token=secret-token&q=visible");
    request.setMethod("POST");
    request.setContent("a".repeat(1005).getBytes(StandardCharsets.UTF_8));

    controller.inbound("myPath", Map.of("authorization", "******", "x-test", "visible"), request);

    var latestActivity = latestActivity(activityLogRegistry, connector.id());
    assertThat(latestActivity.message())
        .contains("POST https://example.com")
        .contains("/inbound/myPath")
        .contains("authorization: [redacted]")
        .contains("x-test: visible")
        .contains("token=[redacted]")
        .contains("q=visible")
        .contains("Body: " + "a".repeat(1000))
        .contains("... (truncated)");
  }

  @Test
  void shouldOmitBodyFromLogWhenMultipartRequestContainsParts() throws Exception {
    var activityLogRegistry = new ActivityLogRegistry();
    var connector = buildConnector(activityLogRegistry);
    var webhookConnectorRegistry = new WebhookConnectorRegistry();
    webhookConnectorRegistry.register(connector);
    var controller = new InboundWebhookRestController(webhookConnectorRegistry);

    var request = new MockMultipartHttpServletRequest();
    request.setScheme("https");
    request.setServerName("example.com");
    request.setServerPort(443);
    request.setRequestURI("/inbound/myPath");
    request.setMethod("POST");
    request.addFile(
        new MockMultipartFile(
            "file", "test.txt", "text/plain", "top secret file contents".getBytes()));
    request.setContentType("multipart/form-data; boundary=x");
    request.setContent(multipartBody("top secret file contents"));

    controller.inbound("myPath", new HashMap<>(), request);

    var latestActivity = latestActivity(activityLogRegistry, connector.id());
    assertThat(latestActivity.message())
        .contains("POST https://example.com")
        .contains("/inbound/myPath")
        .contains("Body: (omitted for multipart request)")
        .doesNotContain("top secret file contents");
  }

  @Test
  void shouldPreserveRawBodyForFormUrlEncodedRequest() throws Exception {
    // Regression test for: @RequestParam in the controller caused Spring MVC to call
    // getParameterMap() before the method body, consuming the servlet input stream for
    // application/x-www-form-urlencoded requests. rawBody() then returned empty bytes,
    // breaking HMAC verification for Slack Interactivity (block_actions) payloads.
    //
    // This test goes through the full Spring MVC dispatch via MockMvc so that
    // Spring actually resolves method parameters — the condition that triggered the bug.

    var rawBodyCaptor = new AtomicReference<byte[]>();

    var executable = mock(WebhookConnectorExecutable.class);
    var webhookResult = mock(WebhookResult.class);
    when(webhookResult.request()).thenReturn(new MappedHttpRequest(Map.of(), Map.of(), Map.of()));
    when(executable.triggerWebhook(any(WebhookProcessingPayload.class)))
        .thenAnswer(
            inv -> {
              rawBodyCaptor.set(((WebhookProcessingPayload) inv.getArgument(0)).rawBody());
              return webhookResult;
            });

    var correlationHandler = mock(InboundCorrelationHandler.class);
    when(correlationHandler.correlate(anyList(), any()))
        .thenThrow(new ConnectorInputException("invalid input"));

    var details = webhookDefinition("processA", 1, "myPath");
    var context =
        new InboundConnectorContextImpl(
            new NullSecretProvider(),
            new DefaultValidationProvider(),
            details,
            correlationHandler,
            e -> {},
            ConnectorsObjectMapperSupplier.getCopy(),
            new ActivityLogRegistry());

    var registry = new WebhookConnectorRegistry();
    registry.register(
        new RegisteredExecutable.Activated(
            executable, context, ExecutableId.fromDeduplicationId(details.deduplicationId())));

    MockMvc mockMvc =
        MockMvcBuilders.standaloneSetup(new InboundWebhookRestController(registry)).build();

    String formBody = "payload=%7B%22type%22%3A%22block_actions%22%7D";
    mockMvc.perform(
        post("/inbound/myPath")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .content(formBody));

    assertThat(rawBodyCaptor.get())
        .isNotNull()
        .isNotEmpty()
        .isEqualTo(formBody.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void shouldPreserveQueryParameterEncounterOrderAndFirstValue() throws Exception {
    var registration = registerWebhook("queryPath");
    var controller = new InboundWebhookRestController(registration.registry());
    var request = requestTo("queryPath", "");
    request.setQueryString("z=first&a=second&z=ignored");

    controller.inbound("queryPath", new HashMap<>(), request);

    var payloadCaptor = ArgumentCaptor.forClass(WebhookProcessingPayload.class);
    verify(registration.executable()).triggerWebhook(payloadCaptor.capture());
    assertThat(payloadCaptor.getValue().params())
        .containsExactly(entry("z", "first"), entry("a", "second"));
  }

  @Test
  void shouldRejectMalformedQueryEncodingWithoutInvokingConnector() throws Exception {
    var registration = registerWebhook("malformedQueryPath");
    var controller = new InboundWebhookRestController(registration.registry());
    var request = requestTo("malformedQueryPath", "");
    request.setQueryString("token=%");

    var response = controller.inbound("malformedQueryPath", new HashMap<>(), request);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    verifyNoInteractions(registration.executable());
  }

  @Test
  void shouldAcceptBodyExactlyAtSizeLimit() throws Exception {
    var registration = registerWebhook("sizePath");
    var controller = new InboundWebhookRestController(registration.registry());
    controller.maxRequestBodyBytes = 8;

    var response =
        controller.inbound("sizePath", new HashMap<>(), requestTo("sizePath", "12345678"));

    assertThat(response.getStatusCode().value()).isEqualTo(422);
  }

  @Test
  void shouldRejectOversizedBodyWithoutInvokingConnector() throws Exception {
    var registration = registerWebhook("oversizePath");
    var controller = new InboundWebhookRestController(registration.registry());
    controller.maxRequestBodyBytes = 8;

    var response =
        controller.inbound("oversizePath", new HashMap<>(), requestTo("oversizePath", "123456789"));

    assertThat(response.getStatusCode().value()).isEqualTo(413);
    verifyNoInteractions(registration.executable());
  }

  @Test
  void shouldReturnNotFoundWithoutReadingRawBodyForUnknownNonMultipartPath() throws Exception {
    var controller = new InboundWebhookRestController(new WebhookConnectorRegistry());

    var request = new ThrowingBodyMockHttpServletRequest();
    request.setRequestURI("/inbound/doesNotExist");
    request.setMethod("POST");
    request.setContent("irrelevant".getBytes(StandardCharsets.UTF_8));

    var response = controller.inbound("doesNotExist", new HashMap<>(), request);

    assertThat(response.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void shouldRejectOversizedMultipartAsPayloadTooLarge() throws Exception {
    var registration = registerWebhook("multipartSizePath");
    var controller = new InboundWebhookRestController(registration.registry());

    byte[] body = multipartBody("oversized");
    controller.maxMultipartRequestSize = (body.length - 1) + "B";
    var request = new MockHttpServletRequest();
    request.setMethod("POST");
    request.setRequestURI("/inbound/multipartSizePath");
    request.setContentType("multipart/form-data; boundary=x");
    request.setContent(body);

    var response = controller.inbound("multipartSizePath", new HashMap<>(), request);

    assertThat(response.getStatusCode().value()).isEqualTo(413);
    verifyNoInteractions(registration.executable());
  }

  @Test
  void shouldRejectMalformedMultipartWithoutInvokingConnector() throws Exception {
    var registration = registerWebhook("malformedMultipartPath");
    var controller = new InboundWebhookRestController(registration.registry());

    var request = new MockHttpServletRequest();
    request.setMethod("POST");
    request.setRequestURI("/inbound/malformedMultipartPath");
    request.setContentType("multipart/form-data; boundary=x");
    request.setContent(
        "--x\r\nContent-Disposition: form-data; name=\"field\"\r\n\r\nunterminated"
            .getBytes(StandardCharsets.UTF_8));

    var response = controller.inbound("malformedMultipartPath", new HashMap<>(), request);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    verifyNoInteractions(registration.executable());
  }

  @Test
  void shouldRejectMultipartWithInvalidSubmittedFilename() throws Exception {
    var registration = registerWebhook("invalidFilenamePath");
    var controller = new InboundWebhookRestController(registration.registry());

    var request = new MockHttpServletRequest();
    request.setMethod("POST");
    request.setRequestURI("/inbound/invalidFilenamePath");
    request.setContentType("multipart/form-data; boundary=x");
    request.setContent(multipartBodyWithFilename("invalid\u0000.txt", "content"));

    var response = controller.inbound("invalidFilenamePath", new HashMap<>(), request);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    verifyNoInteractions(registration.executable());
  }

  @Test
  void shouldRejectMultipartExceedingConfiguredPartCount() throws Exception {
    var registration = registerWebhook("partCountPath");
    var controller = new InboundWebhookRestController(registration.registry());
    controller.maxMultipartPartCount = 1;

    var request = new MockHttpServletRequest();
    request.setMethod("POST");
    request.setRequestURI("/inbound/partCountPath");
    request.setContentType("multipart/form-data; boundary=x");
    request.setContent(
        ("--x\r\n"
                + "Content-Disposition: form-data; name=\"first\"\r\n\r\n"
                + "first content\r\n"
                + "--x\r\n"
                + "Content-Disposition: form-data; name=\"second\"\r\n\r\n"
                + "second content\r\n"
                + "--x--\r\n")
            .getBytes(StandardCharsets.UTF_8));

    var response = controller.inbound("partCountPath", new HashMap<>(), request);

    assertThat(response.getStatusCode().value()).isEqualTo(413);
    verifyNoInteractions(registration.executable());
  }

  @Test
  void shouldCountNamelessMultipartSectionsAgainstConfiguredPartCount() throws Exception {
    var registration = registerWebhook("namelessPartCountPath");
    var controller = new InboundWebhookRestController(registration.registry());
    controller.maxMultipartPartCount = 1;

    var request = new MockHttpServletRequest();
    request.setMethod("POST");
    request.setRequestURI("/inbound/namelessPartCountPath");
    request.setContentType("multipart/form-data; boundary=x");
    request.setContent(
        ("--x\r\n"
                + "Content-Disposition: form-data\r\n\r\n"
                + "first content\r\n"
                + "--x\r\n"
                + "Content-Disposition: form-data\r\n\r\n"
                + "second content\r\n"
                + "--x--\r\n")
            .getBytes(StandardCharsets.UTF_8));

    var response = controller.inbound("namelessPartCountPath", new HashMap<>(), request);

    assertThat(response.getStatusCode().value()).isEqualTo(413);
    verifyNoInteractions(registration.executable());
  }

  @Test
  void shouldAcceptSmallMultipartWhenConfiguredRequestLimitExceedsTwoGibibytes() throws Exception {
    var registration = registerWebhook("largeConfiguredLimitPath");
    var controller = new InboundWebhookRestController(registration.registry());
    controller.maxMultipartRequestSize = "3GB";

    var request = new MockHttpServletRequest();
    request.setMethod("POST");
    request.setRequestURI("/inbound/largeConfiguredLimitPath");
    request.setContentType("multipart/form-data; boundary=x");
    request.setContent(multipartBody("content"));

    controller.inbound("largeConfiguredLimitPath", new HashMap<>(), request);

    verify(registration.executable()).triggerWebhook(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"multipart/related; boundary=x", "multipart/mixed; boundary=x"})
  void shouldPreserveRawBodyForNonFormMultipart(String contentType) throws Exception {
    var registration = registerWebhook("multipartRelatedPath");
    var controller = new InboundWebhookRestController(registration.registry());
    var body = "multipart-related-payload";
    var request = requestTo("multipartRelatedPath", body);
    request.setContentType(contentType);

    var response = controller.inbound("multipartRelatedPath", new HashMap<>(), request);

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    var payloadCaptor = ArgumentCaptor.forClass(WebhookProcessingPayload.class);
    verify(registration.executable()).triggerWebhook(payloadCaptor.capture());
    assertThat(payloadCaptor.getValue().rawBody()).isEqualTo(body.getBytes(StandardCharsets.UTF_8));
    assertThat(payloadCaptor.getValue().parts()).isEmpty();
  }

  @Test
  void shouldNotMapAmbiguousMultipartExceptionTo413WhenMultipartIsDisabled() throws Exception {
    var registration = registerWebhook("multipartDisabledPath");
    var controller = new InboundWebhookRestController(registration.registry());
    controller.multipartEnabled = false;

    var request = new MockHttpServletRequest();
    request.setMethod("POST");
    request.setRequestURI("/inbound/multipartDisabledPath");
    request.setContentType("multipart/form-data; boundary=x");
    request.setContent(multipartBody("content"));

    assertThatThrownBy(() -> controller.inbound("multipartDisabledPath", new HashMap<>(), request))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("spring.servlet.multipart.enabled=false");
    verifyNoInteractions(registration.executable());
  }

  @Test
  void shouldRateLimitSecondRequestToSamePath() throws Exception {
    var registration = registerWebhook("ratePath");
    var controller = new InboundWebhookRestController(registration.registry());
    controller.rateLimitEnabled = true;
    controller.rateLimitPermitsPerSecond = 0.0001;
    controller.validateWebhookConfig();

    var first = controller.inbound("ratePath", new HashMap<>(), requestTo("ratePath", "body"));
    var second = controller.inbound("ratePath", new HashMap<>(), requestTo("ratePath", "body"));

    assertThat(first.getStatusCode().value()).isEqualTo(422);
    assertThat(second.getStatusCode().value()).isEqualTo(429);
  }

  @Test
  void shouldApplyRateLimitAcrossDifferentWebhookPaths() throws Exception {
    var registry = new WebhookConnectorRegistry();
    registerWebhook(registry, "firstRatePath");
    registerWebhook(registry, "secondRatePath");
    var controller = new InboundWebhookRestController(registry);
    controller.rateLimitEnabled = true;
    controller.rateLimitPermitsPerSecond = 0.0001;
    controller.validateWebhookConfig();

    var first =
        controller.inbound("firstRatePath", new HashMap<>(), requestTo("firstRatePath", "body"));
    var second =
        controller.inbound("secondRatePath", new HashMap<>(), requestTo("secondRatePath", "body"));

    assertThat(first.getStatusCode().value()).isEqualTo(422);
    assertThat(second.getStatusCode().value()).isEqualTo(429);
  }

  @Test
  void shouldNotApplyGlobalRateLimitToUnknownWebhookPaths() throws Exception {
    var registration = registerWebhook("registeredRatePath");
    var controller = new InboundWebhookRestController(registration.registry());
    controller.rateLimitEnabled = true;
    controller.rateLimitPermitsPerSecond = 0.0001;
    controller.validateWebhookConfig();

    var unknownBeforeLimit =
        controller.inbound("unknownPath", new HashMap<>(), requestTo("unknownPath", "body"));
    var registered =
        controller.inbound(
            "registeredRatePath", new HashMap<>(), requestTo("registeredRatePath", "body"));
    var unknownAfterLimit =
        controller.inbound("unknownPath", new HashMap<>(), requestTo("unknownPath", "body"));
    var rateLimited =
        controller.inbound(
            "registeredRatePath", new HashMap<>(), requestTo("registeredRatePath", "body"));

    assertThat(unknownBeforeLimit.getStatusCode().value()).isEqualTo(404);
    assertThat(registered.getStatusCode().value()).isEqualTo(422);
    assertThat(unknownAfterLimit.getStatusCode().value()).isEqualTo(404);
    assertThat(rateLimited.getStatusCode().value()).isEqualTo(429);
  }

  @Test
  void shouldNotRateLimitWhenDisabled() throws Exception {
    var registration = registerWebhook("rateDisabledPath");
    var controller = new InboundWebhookRestController(registration.registry());
    controller.rateLimitEnabled = false;
    controller.rateLimitPermitsPerSecond = 0.0001;

    var first =
        controller.inbound(
            "rateDisabledPath", new HashMap<>(), requestTo("rateDisabledPath", "body"));
    var second =
        controller.inbound(
            "rateDisabledPath", new HashMap<>(), requestTo("rateDisabledPath", "body"));

    assertThat(first.getStatusCode().value()).isEqualTo(422);
    assertThat(second.getStatusCode().value()).isEqualTo(422);
  }

  private static MockHttpServletRequest requestTo(String path, String body) {
    var request = new MockHttpServletRequest();
    request.setRequestURI("/inbound/" + path);
    request.setMethod("POST");
    request.setContent(body.getBytes(StandardCharsets.UTF_8));
    return request;
  }

  private static byte[] multipartBody(String fileContent) {
    return multipartBodyWithFilename("test.txt", fileContent);
  }

  private static byte[] multipartBodyWithFilename(String filename, String fileContent) {
    return ("--x\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\""
            + filename
            + "\"\r\n"
            + "Content-Type: text/plain\r\n\r\n"
            + fileContent
            + "\r\n--x--\r\n")
        .getBytes(StandardCharsets.UTF_8);
  }

  private record WebhookRegistration(
      WebhookConnectorRegistry registry, WebhookConnectorExecutable executable) {}

  private static WebhookRegistration registerWebhook(String path) throws Exception {
    var registry = new WebhookConnectorRegistry();
    return new WebhookRegistration(registry, registerWebhook(registry, path));
  }

  private static WebhookConnectorExecutable registerWebhook(
      WebhookConnectorRegistry registry, String path) throws Exception {
    var executable = mock(WebhookConnectorExecutable.class);
    var webhookResult = mock(WebhookResult.class);
    when(webhookResult.request()).thenReturn(new MappedHttpRequest(Map.of(), Map.of(), Map.of()));
    when(executable.triggerWebhook(any(WebhookProcessingPayload.class))).thenReturn(webhookResult);

    var correlationHandler = mock(InboundCorrelationHandler.class);
    when(correlationHandler.correlate(anyList(), any()))
        .thenThrow(new ConnectorInputException("invalid input"));

    var details = webhookDefinition("processA", 1, path);
    var context =
        new InboundConnectorContextImpl(
            new NullSecretProvider(),
            new DefaultValidationProvider(),
            details,
            correlationHandler,
            e -> {},
            ConnectorsObjectMapperSupplier.getCopy(),
            new ActivityLogRegistry());

    registry.register(
        new RegisteredExecutable.Activated(
            executable, context, ExecutableId.fromDeduplicationId(details.deduplicationId())));
    return executable;
  }

  private static class ThrowingBodyMockHttpServletRequest extends MockHttpServletRequest {
    @Override
    public jakarta.servlet.ServletInputStream getInputStream() {
      throw new AssertionError("Request body must not be read for an unregistered webhook path");
    }
  }

  private static io.camunda.connector.api.inbound.Activity latestActivity(
      ActivityLogRegistry registry, ExecutableId executableId) {
    return registry.getLogs(executableId).stream().reduce((first, second) -> second).orElseThrow();
  }

  private static RegisteredExecutable.Activated buildConnector(
      ActivityLogRegistry activityLogRegistry) throws Exception {
    var executable = mock(WebhookConnectorExecutable.class);
    var webhookResult = mock(WebhookResult.class);
    when(webhookResult.request()).thenReturn(new MappedHttpRequest(Map.of(), Map.of(), Map.of()));
    when(executable.triggerWebhook(any(WebhookProcessingPayload.class))).thenReturn(webhookResult);

    var correlationHandler = mock(InboundCorrelationHandler.class);
    when(correlationHandler.correlate(anyList(), any()))
        .thenThrow(new ConnectorInputException("invalid input"));

    var details = webhookDefinition("processA", 1, "myPath");
    var context =
        new InboundConnectorContextImpl(
            new NullSecretProvider(),
            new DefaultValidationProvider(),
            details,
            correlationHandler,
            e -> {},
            ConnectorsObjectMapperSupplier.getCopy(),
            activityLogRegistry);

    return new RegisteredExecutable.Activated(
        executable, context, ExecutableId.fromDeduplicationId(details.deduplicationId()));
  }

  private static InboundConnectorDetails.ValidInboundConnectorDetails webhookDefinition(
      String bpmnProcessId, int version, String path) {
    return (InboundConnectorDetails.ValidInboundConnectorDetails)
        InboundConnectorDetails.of(
            bpmnProcessId + version + path,
            List.of(
                new InboundConnectorElement(
                    Map.of("inbound.type", "io.camunda:webhook:1", "inbound.context", path),
                    new StartEventCorrelationPoint(
                        bpmnProcessId, version, (bpmnProcessId + version).hashCode()),
                    new ProcessElementWithRuntimeData(
                        bpmnProcessId,
                        version,
                        (bpmnProcessId + version).hashCode(),
                        "testElement",
                        "<default>"))));
  }

  private static class NullSecretProvider implements SecretProvider {
    @Override
    public String getSecret(String name, SecretContext context) {
      return null;
    }
  }
}
