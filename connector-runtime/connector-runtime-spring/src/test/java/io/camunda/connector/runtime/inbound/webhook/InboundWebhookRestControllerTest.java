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
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

class InboundWebhookRestControllerTest extends WebhookTestsBase {

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
  void shouldAcceptBodyExactlyAtSizeLimit() throws Exception {
    var registration = registerWebhook("sizePath");
    var controller = new InboundWebhookRestController(registration.registry());
    controller.maxRequestBodyBytes = 8;

    var response =
        controller.inbound("sizePath", new HashMap<>(), requestTo("sizePath", "12345678"));

    assertThat(response.getStatusCode().value()).isNotEqualTo(413);
    verify(registration.executable()).triggerWebhook(anyPayload());
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
  void shouldReturnNotFoundWithoutReadingRawBodyForUnknownPath() throws Exception {
    var controller = new InboundWebhookRestController(new WebhookConnectorRegistry());
    var request = new ThrowingBodyMockHttpServletRequest();
    request.setRequestURI("/inbound/doesNotExist");
    request.setMethod("POST");

    var response = controller.inbound("doesNotExist", new HashMap<>(), request);

    assertThat(response.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void shouldRejectOversizedMultipartAsPayloadTooLarge() throws Exception {
    var registration = registerWebhook("multipartSizePath");
    var controller = new InboundWebhookRestController(registration.registry());
    var request = new SizeExceededMultipartMockHttpServletRequest();
    request.setMethod("POST");
    request.setRequestURI("/inbound/multipartSizePath");
    request.setContentType("multipart/form-data; boundary=x");

    var response = controller.inbound("multipartSizePath", new HashMap<>(), request);

    assertThat(response.getStatusCode().value()).isEqualTo(413);
    verifyNoInteractions(registration.executable());
  }

  @ParameterizedTest
  @ValueSource(strings = {"multipart/related; boundary=x", "multipart/mixed; boundary=x"})
  void shouldPreserveRawBodyForNonFormMultipart(String contentType) throws Exception {
    var registration = registerWebhook("multipartRelatedPath");
    var controller = new InboundWebhookRestController(registration.registry());
    var body = "multipart-related-payload";
    var request = requestTo("multipartRelatedPath", body);
    request.setContentType(contentType);

    controller.inbound("multipartRelatedPath", new HashMap<>(), request);

    var payloadCaptor = ArgumentCaptor.forClass(WebhookProcessingPayload.class);
    verify(registration.executable()).triggerWebhook(payloadCaptor.capture());
    assertThat(payloadCaptor.getValue().rawBody()).isEqualTo(body.getBytes(StandardCharsets.UTF_8));
    assertThat(payloadCaptor.getValue().parts()).isEmpty();
  }

  @Test
  void shouldNotMapAmbiguousMultipartExceptionTo413WhenMultipartIsDisabled() {
    var registration = registerWebhook("multipartDisabledPath");
    var controller = new InboundWebhookRestController(registration.registry());
    controller.multipartEnabled = false;
    var request = new SizeExceededMultipartMockHttpServletRequest();
    request.setMethod("POST");
    request.setRequestURI("/inbound/multipartDisabledPath");
    request.setContentType("multipart/form-data; boundary=x");

    assertThatThrownBy(() -> controller.inbound("multipartDisabledPath", new HashMap<>(), request))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("spring.servlet.multipart.enabled=false");
    verifyNoInteractions(registration.executable());
  }

  @Test
  void shouldRateLimitSecondRequestToSamePath() throws Exception {
    var registration = registerWebhook("ratePath");
    var controller = rateLimitedController(registration.registry());

    var first = controller.inbound("ratePath", new HashMap<>(), requestTo("ratePath", "body"));
    var second = controller.inbound("ratePath", new HashMap<>(), requestTo("ratePath", "body"));

    assertThat(first.getStatusCode().value()).isNotEqualTo(429);
    assertThat(second.getStatusCode().value()).isEqualTo(429);
  }

  @Test
  void shouldApplyRateLimitAcrossDifferentWebhookPaths() throws Exception {
    var registry = new WebhookConnectorRegistry();
    var firstRegistration = registerWebhook(registry, "firstRatePath");
    var secondRegistration = registerWebhook(registry, "secondRatePath");
    var controller = rateLimitedController(registry);

    var first =
        controller.inbound("firstRatePath", new HashMap<>(), requestTo("firstRatePath", "body"));
    var second =
        controller.inbound("secondRatePath", new HashMap<>(), requestTo("secondRatePath", "body"));

    assertThat(first.getStatusCode().value()).isNotEqualTo(429);
    assertThat(second.getStatusCode().value()).isEqualTo(429);
    verify(firstRegistration.executable()).triggerWebhook(anyPayload());
    verifyNoInteractions(secondRegistration.executable());
  }

  @Test
  void shouldNotApplyGlobalRateLimitToUnknownWebhookPaths() throws Exception {
    var registration = registerWebhook("registeredRatePath");
    var controller = rateLimitedController(registration.registry());

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
    assertThat(registered.getStatusCode().value()).isNotEqualTo(429);
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

    assertThat(first.getStatusCode().value()).isNotEqualTo(429);
    assertThat(second.getStatusCode().value()).isNotEqualTo(429);
    verify(registration.executable(), times(2)).triggerWebhook(anyPayload());
  }

  @Test
  void shouldParseQueryWithoutConsumingFormBody() throws Exception {
    var registration = registerWebhook("formPath");
    var controller = new InboundWebhookRestController(registration.registry());
    var request = requestTo("formPath", "payload=%7B%22type%22%3A%22block_actions%22%7D");
    request.setContentType("application/x-www-form-urlencoded");
    request.setQueryString("token=a%20b");

    controller.inbound("formPath", Map.of(), request);

    var payloadCaptor = ArgumentCaptor.forClass(WebhookProcessingPayload.class);
    verify(registration.executable()).triggerWebhook(payloadCaptor.capture());
    assertThat(payloadCaptor.getValue().params()).containsEntry("token", "a b");
    assertThat(payloadCaptor.getValue().rawBody())
        .isEqualTo(
            "payload=%7B%22type%22%3A%22block_actions%22%7D".getBytes(StandardCharsets.UTF_8));
  }

  private static InboundWebhookRestController rateLimitedController(
      WebhookConnectorRegistry registry) {
    var controller = new InboundWebhookRestController(registry);
    controller.rateLimitEnabled = true;
    controller.rateLimitPermitsPerSecond = 0.0001;
    controller.validateWebhookConfig();
    return controller;
  }

  private static MockHttpServletRequest requestTo(String path, String body) {
    var request = new MockHttpServletRequest();
    request.setRequestURI("/inbound/" + path);
    request.setMethod("POST");
    request.setContent(body.getBytes(StandardCharsets.UTF_8));
    return request;
  }

  private static WebhookRegistration registerWebhook(String path) {
    var registry = new WebhookConnectorRegistry();
    return registerWebhook(registry, path);
  }

  private static WebhookRegistration registerWebhook(
      WebhookConnectorRegistry registry, String path) {
    var connector = buildConnector("processA", 1, path);
    registry.register(connector);
    var executable = (WebhookConnectorExecutable) connector.executable();
    clearInvocations(executable);
    return new WebhookRegistration(registry, executable);
  }

  private static WebhookProcessingPayload anyPayload() {
    return org.mockito.ArgumentMatchers.any(WebhookProcessingPayload.class);
  }

  private record WebhookRegistration(
      WebhookConnectorRegistry registry, WebhookConnectorExecutable executable) {}

  private static class ThrowingBodyMockHttpServletRequest extends MockHttpServletRequest {
    @Override
    public jakarta.servlet.ServletInputStream getInputStream() {
      throw new AssertionError("Request body must not be read for an unregistered webhook path");
    }
  }

  private static class SizeExceededMultipartMockHttpServletRequest extends MockHttpServletRequest {
    @Override
    public java.util.Collection<jakarta.servlet.http.Part> getParts() {
      throw new IllegalStateException(
          "Simulated: multipart size limit exceeded (or no multipart config)");
    }
  }
}
