/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.utils;

import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.inbound.model.HMACScope;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class HttpWebhookUtil {

  public static String extractContentType(Map<String, String> headers) {
    var caseInsensitiveMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    caseInsensitiveMap.putAll(headers);
    return caseInsensitiveMap.getOrDefault(HttpHeaders.CONTENT_TYPE, "").toString();
  }

  public static Map transformRawBodyToMap(byte[] rawBody, String contentTypeHeader)
      throws IOException {
    if (rawBody == null) {
      return Collections.emptyMap();
    }

    if (MediaType.FORM_DATA.toString().equalsIgnoreCase(contentTypeHeader)) {
      String bodyAsString =
          URLDecoder.decode(new String(rawBody, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
      return Arrays.stream(bodyAsString.split("&"))
          .filter(Objects::nonNull)
          .map(param -> param.split("="))
          .collect(Collectors.toMap(param -> param[0], param -> param.length == 1 ? "" : param[1]));
    } else {
      // Do our best to parse to JSON (throws exception otherwise)
      return ObjectMapperSupplier.getMapperInstance().readValue(rawBody, Map.class);
    }
  }

  // this method and child methods can be improved and expanded, this version is for the current
  // cases available
  public static byte[] extractSignatureData(
      final WebhookProcessingPayload payload, final HMACScope[] hmacScopes) throws IOException {
    var hmacScopeHashSet = new HashSet<>(Arrays.asList(hmacScopes));
    if (HttpMethods.get.name().equalsIgnoreCase(payload.method())) {
      if (hmacScopeHashSet.containsAll(List.of(HMACScope.URL, HMACScope.PARAMETERS))) {
        return (payload.requestURL() + "?" + extractSignatureDataFromParams(payload.params()))
            .getBytes();
      }
    } else {
      switch (hmacScopes.length) {
        case 1:
          if (hmacScopes[0] == HMACScope.BODY) {
            return payload.rawBody();
          }
        case 2:
          if (hmacScopeHashSet.containsAll(List.of(HMACScope.URL, HMACScope.BODY))) {
            return (payload.requestURL() + extractSignatureDataFromBody(payload)).getBytes();
          }
          if (Arrays.asList(hmacScopes).containsAll(List.of(HMACScope.URL, HMACScope.PARAMETERS))) {
            return (payload.requestURL() + "?" + extractSignatureDataFromParams(payload.params()))
                .getBytes();
          }
        case 3:
          // case for method 'any' and 'post for hmac scopes 'url' and 'body' or 'parameters'
          if (hmacScopeHashSet.containsAll(List.of(HMACScope.URL, HMACScope.BODY))) {
            return (payload.requestURL() + extractSignatureDataFromBody(payload)).getBytes();
          }
      }
    }
    throw new UnsupportedOperationException(
        "The current pattern of HMAC scopes is not supported : " + Arrays.toString(hmacScopes));
  }

  private static String extractSignatureDataFromParams(final Map<String, String> params) {
    return params.entrySet().stream()
        .map(
            entry ->
                String.format(
                    "%s=%s",
                    URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8),
                    URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)))
        .collect(Collectors.joining("&"));
  }

  private static String extractSignatureDataFromBody(final WebhookProcessingPayload payload)
      throws IOException {
    Map<String, String> signatureData =
        transformRawBodyToMap(payload.rawBody(), extractContentType(payload.headers()));

    StringBuilder builder = new StringBuilder();
    List<String> sortedKeys = new ArrayList(signatureData.keySet());
    Collections.sort(sortedKeys);

    for (String key : sortedKeys) {
      builder.append(key);
      String value = signatureData.get(key);
      builder.append(value == null ? "" : value);
    }
    return builder.toString();
  }
}
