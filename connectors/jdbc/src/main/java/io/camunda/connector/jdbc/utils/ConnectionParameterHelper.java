/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.utils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Helper class to add parameters to a URL. Parameters values can be added to the URL as well, but
 * it is optional.
 *
 * <p>See ConnectionParameterHelperTest for usage examples.
 */
public class ConnectionParameterHelper {

  public static String addQueryParameterToURL(String urlString, String paramName) {
    return addQueryParameterToURL(urlString, paramName, null);
  }

  public static String addQueryParameterToURL(
      String urlString, String paramName, String paramValue) {
    String encodedValue =
        paramValue != null ? URLEncoder.encode(paramValue, StandardCharsets.UTF_8) : null;
    String separator = urlString.contains("?") ? "&" : "?";
    return urlString + separator + paramName + (encodedValue != null ? "=" + encodedValue : "");
  }
}
