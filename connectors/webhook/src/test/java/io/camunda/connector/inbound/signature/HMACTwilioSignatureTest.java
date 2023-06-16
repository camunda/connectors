/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.signature;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingResult;
import io.camunda.connector.impl.inbound.InboundConnectorProperties;
import io.camunda.connector.impl.inbound.correlation.MessageCorrelationPoint;
import io.camunda.connector.inbound.HttpWebhookExecutable;
import io.camunda.connector.inbound.utils.HttpMethods;
import io.camunda.connector.inbound.utils.ObjectMapperSupplier;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class HMACTwilioSignatureTest {
  private static final String SUCCESS_CASES_RESOURCE_PATH =
      "src/test/resources/hmac/twilio-webhook-request.json";
  private HttpWebhookExecutable httpWebhookExecutable;
  @Mock private InboundConnectorContext inboundConnectorContext;
  private Map<String, String> mapOfProperties;

  @BeforeEach
  public void init() {
    httpWebhookExecutable = new HttpWebhookExecutable();
    mapOfProperties =
        new HashMap<>(
            Map.of(
                "inbound.context",
                "",
                "inbound.shouldValidateHmac",
                "enabled",
                "inbound.hmacAlgorithm",
                "sha_1",
                "inbound.hmacSecret",
                "mySecretAuthKey",
                "inbound.hmacHeader",
                "x-twilio-signature",
                "inbound.method",
                "any"));
    InboundConnectorProperties properties =
        new InboundConnectorProperties(
            new MessageCorrelationPoint(""), mapOfProperties, "bool", 0, 0, "id");
    when(inboundConnectorContext.getProperties()).thenReturn(properties);
    doNothing().when(inboundConnectorContext).replaceSecrets(any());
  }

  @ParameterizedTest
  @MethodSource("successCases")
  public void hmacSignatureVerificationParametrizedTest_shouldPassHMACValidation(
      final WebhookProcessingPayload webhookProcessingPayload) throws Exception {
    // Given
    if (webhookProcessingPayload.method().equalsIgnoreCase(HttpMethods.get.name())) {
      mapOfProperties.put("inbound.hmacScopes", "=[\"url\",\"parameters\"]");
    } else if (webhookProcessingPayload.method().equalsIgnoreCase(HttpMethods.post.name())) {
      mapOfProperties.put("inbound.hmacScopes", "=[\"url\",\"body\"]");
    }
    httpWebhookExecutable.activate(inboundConnectorContext);
    // When
    WebhookProcessingResult webhookProcessingResult =
        httpWebhookExecutable.triggerWebhook(webhookProcessingPayload);
    // Then assert that result not null, and validation pass done, without exceptions
    assertThat(webhookProcessingResult).isNotNull();
  }

  private static Stream<WebhookProcessingPayloadTest> successCases() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_CASES_RESOURCE_PATH);
  }

  protected static Stream<WebhookProcessingPayloadTest> loadTestCasesFromResourceFile(
      final String fileWithTestCasesUri) throws IOException {
    final String cases = readString(new File(fileWithTestCasesUri).toPath(), UTF_8);
    final ObjectMapper objectMapper = ObjectMapperSupplier.getMapperInstance();
    return objectMapper
        .readValue(cases, new TypeReference<List<WebhookProcessingPayloadTest>>() {})
        .stream();
  }

  public static class WebhookProcessingPayloadTest implements WebhookProcessingPayload {

    private String requestURL;
    private String method;
    private Map<String, String> headers;
    private Map<String, String> params;
    private String body;
    private byte[] rawBody;

    @Override
    public String requestURL() {
      return requestURL;
    }

    @Override
    public String method() {
      return method;
    }

    @Override
    public Map<String, String> headers() {
      return headers;
    }

    @Override
    public Map<String, String> params() {
      return params;
    }

    @Override
    public byte[] rawBody() {
      return body == null ? null : body.getBytes();
    }

    public void setBody(final String body) {
      this.body = body;
    }

    public void setRequestURL(final String requestURL) {
      this.requestURL = requestURL;
    }

    public void setMethod(final String method) {
      this.method = method;
    }

    public void setHeaders(final Map<String, String> headers) {
      this.headers = headers;
    }

    public void setParams(final Map<String, String> params) {
      this.params = params;
    }

    public void setRawBody(final byte[] rawBody) {
      this.rawBody = rawBody;
    }

    @Override
    public String toString() {
      return "WebhookProcessingPayloadTest{"
          + "requestURL='"
          + requestURL
          + "'"
          + ", method='"
          + method
          + "'"
          + ", headers="
          + headers
          + ", params="
          + params
          + ", rawBody="
          + Arrays.toString(rawBody)
          + "}";
    }
  }
}
