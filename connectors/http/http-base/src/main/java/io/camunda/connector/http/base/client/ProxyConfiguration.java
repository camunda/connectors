/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.http.base.client;

import java.util.Optional;

public class ProxyConfiguration {
  private static final String HTTP_PROXY_URL = "HTTP_PROXY_URL";
  private final String httpProxyUrl = System.getenv(HTTP_PROXY_URL);

  public Optional<String> getHttpProxyUrl() {
    return Optional.ofNullable(httpProxyUrl);
  }
}
