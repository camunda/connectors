/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound;

import static io.camunda.connector.inbound.signature.HMACSwitchCustomerChoice.disabled;
import static io.camunda.connector.inbound.signature.HMACSwitchCustomerChoice.enabled;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingResult;
import io.camunda.connector.inbound.model.WebhookConnectorProperties;
import io.camunda.connector.inbound.model.WebhookProcessingResultImpl;
import io.camunda.connector.inbound.signature.HMACAlgoCustomerChoice;
import io.camunda.connector.inbound.signature.HMACSignatureValidator;
import io.camunda.connector.inbound.utils.HttpMethods;
import io.camunda.connector.inbound.utils.ObjectMapperSupplier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InboundConnector(name = "WEBHOOK_INBOUND", type = "io.camunda:webhook:1")
public class HttpWebhookExecutable implements WebhookConnectorExecutable {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpWebhookExecutable.class);
  private final ObjectMapper objectMapper;

  private WebhookConnectorProperties props;

  public HttpWebhookExecutable() {
    this(ObjectMapperSupplier.getMapperInstance());
  }

  public HttpWebhookExecutable(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public WebhookProcessingResult triggerWebhook(WebhookProcessingPayload payload)
      throws NoSuchAlgorithmException, InvalidKeyException, IOException {
    LOGGER.trace(
        "Triggered webhook with context " + props.getContext() + " and payload " + payload);

    if (!HttpMethods.any.name().equalsIgnoreCase(props.getMethod())
        && !payload.method().equalsIgnoreCase(props.getMethod())) {
      throw new IOException("Webhook failed: method not supported");
    }

    WebhookProcessingResultImpl response = new WebhookProcessingResultImpl();

    if (!webhookSignatureIsValid(props, payload)) {
      throw new IOException("Webhook failed: HMAC signature check didn't pass");
    }

    response.setBody(
        transformRawBodyToMap(payload.rawBody(), extractContentType(payload.headers())));
    response.setHeaders(payload.headers());
    response.setParams(payload.params());

    return response;
  }

  private String extractContentType(Map<String, String> headers) {
    var caseInsensitiveMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    caseInsensitiveMap.putAll(headers);
    return caseInsensitiveMap.get(HttpHeaders.CONTENT_TYPE).toString();
  }

  private Map transformRawBodyToMap(byte[] rawBody, String contentTypeHeader) throws IOException {
    if (rawBody == null) {
      return Collections.emptyMap();
    }

    if (MediaType.FORM_DATA.toString().equalsIgnoreCase(contentTypeHeader)) {
      String bodyAsString = new String(rawBody, StandardCharsets.UTF_8);
      return Arrays.stream(bodyAsString.split("&"))
          .filter(Objects::nonNull)
          .map(param -> param.split("="))
          .filter(param -> param.length > 1)
          .collect(Collectors.toMap(param -> param[0], param -> param[1]));
    } else {
      // Do our best to parse to JSON (throws exception otherwise)
      return objectMapper.readValue(rawBody, Map.class);
    }
  }

  private boolean webhookSignatureIsValid(
      WebhookConnectorProperties context, WebhookProcessingPayload payload)
      throws NoSuchAlgorithmException, InvalidKeyException, IOException {
    final String shouldValidateHmac =
        Optional.ofNullable(context.getShouldValidateHmac()).orElse(disabled.name());
    if (enabled.name().equals(shouldValidateHmac)) {
      final HMACSignatureValidator hmacSignatureValidator =
          new HMACSignatureValidator(
              payload.rawBody(),
              payload.headers(),
              context.getHmacHeader(),
              context.getHmacSecret(),
              HMACAlgoCustomerChoice.valueOf(context.getHmacAlgorithm()));
      return hmacSignatureValidator.isRequestValid();
    }
    return true;
  }

  @Override
  public void activate(InboundConnectorContext context) throws Exception {
    if (context == null) {
      throw new Exception("Inbound connector context cannot be null");
    }
    props = new WebhookConnectorProperties(context.getProperties());
    context.replaceSecrets(props);
  }
}
