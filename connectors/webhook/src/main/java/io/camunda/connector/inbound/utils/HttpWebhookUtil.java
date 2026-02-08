/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.utils;

import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
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

  public static Object transformRawBodyToObject(byte[] rawBody, String contentTypeHeader) {
    if (rawBody == null || rawBody.length == 0) {
      return Collections.emptyMap();
    }
    if (MediaType.FORM_DATA.toString().equalsIgnoreCase(contentTypeHeader)) {
      String bodyAsString =
          URLDecoder.decode(new String(rawBody, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
      return Arrays.stream(bodyAsString.split("&"))
          .filter(Objects::nonNull)
          .map(param -> param.split("="))
          .collect(Collectors.toMap(param -> param[0], param -> param.length == 1 ? "" : param[1]));
    } else if (isXmlContentType(contentTypeHeader)) {
      return new String(rawBody, StandardCharsets.UTF_8);
    } else {
      // Do our best to parse to JSON (throws exception otherwise)
      try {
        return ConnectorsObjectMapperSupplier.getCopy().readValue(rawBody, Object.class);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static boolean isXmlContentType(String contentType) {
    if (contentType == null) {
      return false;
    }
    String lowerContentType = contentType.toLowerCase();
    return lowerContentType.contains("application/xml") || lowerContentType.contains("text/xml");
  }
}
