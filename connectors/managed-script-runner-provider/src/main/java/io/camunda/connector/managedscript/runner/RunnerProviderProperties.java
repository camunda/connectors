/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.runner;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration of the runner provider. camunda-operator sets it from the status of the {@code
 * ScriptRunner} of the cluster.
 *
 * @param url base URL of the runner, {@code http} or {@code https}, with no user, query or fragment
 * @param tokenPath file of the projected service account token that the runner accepts; the kubelet
 *     replaces it before the token expires
 * @param runtimes the runtime of each language, by {@link
 *     io.camunda.connector.managedscript.api.ScriptLanguage#language()}, for example {@code python}
 *     to {@code python-3.13}; a language without a runtime cannot run
 */
@ConfigurationProperties(prefix = "camunda.connector.managed-code.runner")
public record RunnerProviderProperties(String url, String tokenPath, Map<String, String> runtimes) {

  private static final String PREFIX = "camunda.connector.managed-code.runner.";

  public RunnerProviderProperties {
    url = url == null ? "" : url.strip();
    if (!isPlainHttpUrl(url)) {
      // The URL is not quoted: a URL with a user part holds a password.
      throw new IllegalArgumentException(
          PREFIX + "url must be an http or https URL of a host, with no user, query or fragment");
    }
    tokenPath = tokenPath == null ? "" : tokenPath.strip();
    if (tokenPath.isEmpty()) {
      throw new IllegalArgumentException(PREFIX + "token-path must be set");
    }
    runtimes = runtimes == null ? Map.of() : Map.copyOf(runtimes);
  }

  private static boolean isPlainHttpUrl(String url) {
    try {
      final var uri = new URI(url);
      return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
          && uri.getHost() != null
          && uri.getRawUserInfo() == null
          && uri.getRawQuery() == null
          && uri.getRawFragment() == null;
    } catch (URISyntaxException e) {
      return false;
    }
  }
}
