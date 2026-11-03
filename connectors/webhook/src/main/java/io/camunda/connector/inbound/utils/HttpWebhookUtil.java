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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public class HttpWebhookUtil {

  private static final String MULTIPART_FORM_DATA_CONTENT_TYPE = "multipart/form-data";

  public static String extractContentType(Map<String, String> headers) {
    var caseInsensitiveMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    caseInsensitiveMap.putAll(headers);
    return caseInsensitiveMap.getOrDefault(HttpHeaders.CONTENT_TYPE, "").toString();
  }

  public static Object transformRawBodyToObject(byte[] rawBody, String contentTypeHeader) {
    if (rawBody == null || rawBody.length == 0) {
      return Collections.emptyMap();
    }
    if (isMultipartFormDataContentType(contentTypeHeader)) {
      return Collections.emptyMap();
    } else if (MediaType.FORM_DATA.toString().equalsIgnoreCase(contentTypeHeader)) {
      return parseFormUrlEncoded(new String(rawBody, StandardCharsets.UTF_8));
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

  /**
   * Parses an {@code application/x-www-form-urlencoded} body. The body is split into pairs on
   * {@code &} and each pair once on the first {@code =} <em>before</em> names and values are
   * URL-decoded, so encoded separators ({@code %26}, {@code %3D}) and literal {@code =} inside a
   * value are preserved. For a repeated name, the first value wins.
   */
  public static Map<String, String> parseFormUrlEncoded(String body) {
    Map<String, String> result = new LinkedHashMap<>();
    for (String pair : body.split("&")) {
      if (pair.isEmpty()) {
        continue;
      }
      String[] parts = pair.split("=", 2);
      result.putIfAbsent(
          URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
          parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
    }
    return result;
  }

  private static boolean isMultipartFormDataContentType(String contentType) {
    if (contentType == null) {
      return false;
    }
    int parameterStart = contentType.indexOf(';');
    String mediaType = parameterStart < 0 ? contentType : contentType.substring(0, parameterStart);
    return MULTIPART_FORM_DATA_CONTENT_TYPE.equalsIgnoreCase(mediaType.trim());
  }

  private static boolean isXmlContentType(String contentType) {
    if (contentType == null) {
      return false;
    }
    String lowerContentType = contentType.toLowerCase();
    return lowerContentType.contains("application/xml") || lowerContentType.contains("text/xml");
  }
}
