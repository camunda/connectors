/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.utils;

import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
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
      return ConnectorsObjectMapperSupplier.getCopy().readValue(rawBody, Map.class);
    }
  }
}
