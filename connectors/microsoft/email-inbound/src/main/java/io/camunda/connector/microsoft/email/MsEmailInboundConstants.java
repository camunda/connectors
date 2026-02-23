/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class MsEmailInboundConstants {

  private MsEmailInboundConstants() {
    // Utility class
  }

  // Shutdown configuration
  public static final Duration SHUTDOWN_TIMEOUT = Duration.ofMillis(800);

  // Microsoft Graph API
  public static final String GRAPH_API_DEFAULT_SCOPE = "https://graph.microsoft.com/.default";
  public static final String[] GRAPH_API_SCOPES = new String[] {GRAPH_API_DEFAULT_SCOPE};

  // HTTP headers
  public static final String PREFER_HEADER = "Prefer";
  public static final String PREFER_TEXT_BODY = "outlook.body-content-type=\"text\"";

  // OData query parameters (URL-encoded)
  public static final String ODATA_FILTER_PARAM =
      URLEncoder.encode("$filter", StandardCharsets.UTF_8);
  public static final String ODATA_SELECT_PARAM =
      URLEncoder.encode("$select", StandardCharsets.UTF_8);
  public static final String ODATA_TOP_PARAM = URLEncoder.encode("$top", StandardCharsets.UTF_8);

  // Pagination
  public static final int PAGE_SIZE = 10;
}
