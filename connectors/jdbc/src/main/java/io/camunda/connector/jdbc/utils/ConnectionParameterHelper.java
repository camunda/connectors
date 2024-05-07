/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.camunda.connector.jdbc.utils;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Helper class to add parameters to a URL. Parameters values can be added to the URL as well, but
 * it is optional.
 *
 * @see ConnectionParameterHelperTest for usage examples.
 */
public class ConnectionParameterHelper {

  public static String addQueryParameterToURL(String urlString, String paramName)
      throws URISyntaxException {
    return addQueryParameterToURL(urlString, paramName, null);
  }

  public static String addQueryParameterToURL(String urlString, String paramName, String paramValue)
      throws URISyntaxException {
    URI uri = new URI(urlString);
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
    return new URI(uri.getScheme() + ":" + uri.getSchemeSpecificPart() + query).toString();
  }
}
