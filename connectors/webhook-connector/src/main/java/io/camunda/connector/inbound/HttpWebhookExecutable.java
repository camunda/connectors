/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound;

import static io.camunda.connector.inbound.model.WebhookResponsePayloadImpl.DEFAULT_RESPONSE_STATUS_KEY;
import static io.camunda.connector.inbound.model.WebhookResponsePayloadImpl.DEFAULT_RESPONSE_STATUS_VALUE_FAIL;
import static io.camunda.connector.inbound.signature.HMACSignatureValidator.HMAC_ALGO_PROPERTY;
import static io.camunda.connector.inbound.signature.HMACSignatureValidator.HMAC_HEADER_PROPERTY;
import static io.camunda.connector.inbound.signature.HMACSignatureValidator.HMAC_SECRET_KEY_PROPERTY;
import static io.camunda.connector.inbound.signature.HMACSignatureValidator.HMAC_VALIDATION_DISABLED;
import static io.camunda.connector.inbound.signature.HMACSignatureValidator.HMAC_VALIDATION_ENABLED;
import static io.camunda.connector.inbound.signature.HMACSignatureValidator.HMAC_VALIDATION_ENABLED_PROPERTY;
import static io.camunda.connector.inbound.signature.HMACSignatureValidator.HMAC_VALIDATION_FAILED_KEY;
import static io.camunda.connector.inbound.signature.HMACSignatureValidator.HMAC_VALIDATION_FAILED_REASON_DIDNT_MATCH;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.MediaType;
import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorResult;
import io.camunda.connector.api.inbound.WebhookConnectorExecutable;
import io.camunda.connector.impl.inbound.WebhookRequestPayload;
import io.camunda.connector.impl.inbound.WebhookResponsePayload;
import io.camunda.connector.inbound.model.WebhookResponsePayloadImpl;
import io.camunda.connector.inbound.signature.HMACAlgoCustomerChoice;
import io.camunda.connector.inbound.signature.HMACSignatureValidator;
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

import io.camunda.connector.inbound.utils.ObjectMapperSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InboundConnector(name = "WEBHOOK_INBOUND", type = "io.camunda:webhook:1")
public class HttpWebhookExecutable implements WebhookConnectorExecutable {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpWebhookExecutable.class);
  private final ObjectMapper objectMapper;

  public HttpWebhookExecutable() {
    this(ObjectMapperSupplier.getMapperInstance());
  }
  
  public HttpWebhookExecutable(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public WebhookResponsePayload triggerWebhook(
          InboundConnectorContext context, 
          WebhookRequestPayload webhookRequestPayload) 
            throws NoSuchAlgorithmException, InvalidKeyException, IOException {
    WebhookResponsePayloadImpl response = new WebhookResponsePayloadImpl();
    context.replaceSecrets(context.getProperties());

    if (!webhookSignatureIsValid(context, webhookRequestPayload)) {
      response.setBody(Map.of(
              DEFAULT_RESPONSE_STATUS_KEY, DEFAULT_RESPONSE_STATUS_VALUE_FAIL,
              HMAC_VALIDATION_FAILED_KEY, HMAC_VALIDATION_FAILED_REASON_DIDNT_MATCH));
      return response;
    }
    
    InboundConnectorResult<?> result = correlate(context, webhookRequestPayload);
    response.setExecutionResult(result);
    
    if (!result.isActivated()) {
      response.setBody(Map.of(DEFAULT_RESPONSE_STATUS_KEY, DEFAULT_RESPONSE_STATUS_VALUE_FAIL));
      return response;
    }
    
    return response;
  }
  
  private InboundConnectorResult<?> correlate(InboundConnectorContext context, WebhookRequestPayload payload) 
          throws IOException {
    final var contentTypeHeader = "Content-Type";
    var caseInsensitiveMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    caseInsensitiveMap.putAll(payload.headers());
    var bodyAsMap = transformRawBodyToMap(payload.rawBody(), caseInsensitiveMap.get(contentTypeHeader).toString());
    return context.correlate(bodyAsMap, payload.headers(), payload.params());
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
  
  private boolean webhookSignatureIsValid(InboundConnectorContext context, WebhookRequestPayload payload)
          throws NoSuchAlgorithmException, InvalidKeyException, IOException {
    final String shouldValidateHmac = 
      Optional.ofNullable(context.getProperties().getProperty(HMAC_VALIDATION_ENABLED_PROPERTY))
              .orElse(HMAC_VALIDATION_DISABLED);
    if (HMAC_VALIDATION_ENABLED.equals(shouldValidateHmac)) {
      final HMACSignatureValidator hmacSignatureValidator =
              new HMACSignatureValidator(
                      payload.rawBody(),
                      payload.headers(),
                      context.getProperties().getProperty(HMAC_HEADER_PROPERTY),
                      context.getProperties().getProperty(HMAC_SECRET_KEY_PROPERTY),
                      HMACAlgoCustomerChoice.valueOf(
                              context.getProperties().getProperty(HMAC_ALGO_PROPERTY)));
      return hmacSignatureValidator.isRequestValid();
    }
    return true;
  }
}
