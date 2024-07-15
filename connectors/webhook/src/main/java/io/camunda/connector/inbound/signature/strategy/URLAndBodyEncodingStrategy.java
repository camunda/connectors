/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.signature.strategy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.net.MediaType;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.inbound.utils.HttpWebhookUtil;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public final class URLAndBodyEncodingStrategy implements HMACEncodingStrategy {

  private String extractSignatureData(final WebhookProcessingPayload payload) {
    return prepareForSignature(
        payload.rawBody(), HttpWebhookUtil.extractContentType(payload.headers()));
  }

  private String prepareForSignature(byte[] rawBody, String contentTypeHeader) {
    //Twilio-specific HMAC encryption. If this is an url encoded parameter or body that is a map
    //then this algo will be used
    //If not we will just return the object as a serialized String
    if (MediaType.FORM_DATA.toString().equalsIgnoreCase(contentTypeHeader)) {
      String bodyAsString =
          URLDecoder.decode(new String(rawBody, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
      Map<String, String> strMap =
          Arrays.stream(bodyAsString.split("&"))
              .filter(Objects::nonNull)
              .map(param -> param.split("="))
              .collect(
                  Collectors.toMap(param -> param[0], param -> param.length == 1 ? "" : param[1]));
      return extractSignatureFromMap(strMap);
    }
    Object o = mapBytesToObject(rawBody);
    if (o instanceof Map<?, ?> map) {
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (entry.getKey() instanceof String && entry.getValue() instanceof String) {
          return extractSignatureFromMap((Map<String, String>) map);
        }
      }
    } else {
      return mapObjectToString(o);
    }
    throw new RuntimeException("Can't extract signature data from body");
  }

  private String extractSignatureFromMap(Map<String, String> map) {
    return map.keySet().stream()
        .sorted()
        .map(key -> key.concat(map.get(key)))
        .reduce(String::concat)
        .orElse("");
  }

  private String mapObjectToString(Object o) {
    try {
      return ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.writeValueAsString(o);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private Object mapBytesToObject(byte[] rawBody) {
    try {
      return ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.readValue(rawBody, Object.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public byte[] getBytesToSign(final WebhookProcessingPayload payload) {
    return (payload.requestURL() + extractSignatureData(payload)).getBytes();
  }
}
