/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.utils;


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
    // Check if the URL already has query parameters
    int queryParamsIndex = urlString.indexOf('?');
    String query;
    if (queryParamsIndex == -1) {
      // No query parameters
      query = "?";
    } else {
      // Query parameters already exist let's add the new one
      query = "&";
    }
    query += paramName;
    // Value is optional
    if (paramValue != null) {
      query += "=" + paramValue;
    }
    // jdbc:mysql//localhost:3306?paramName=paramValue for instance is not detected as a regular
    // URI,
    // so we need to reconstruct the URI using the scheme and the scheme specific part
    return urlString + query;
  }
}
