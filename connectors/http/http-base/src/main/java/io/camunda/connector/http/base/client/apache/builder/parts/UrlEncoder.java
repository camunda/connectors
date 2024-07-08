/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.http.base.client.apache.builder.parts;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UrlEncoder {
  private static final Logger LOG = LoggerFactory.getLogger(ApacheRequestUriBuilder.class);

  public static URI toEncodedUri(String requestUrl) {
    try {
      // We try to decode the URL first, because it might be encoded already
      // which would lead to double encoding. Decoding is safe here, because it does nothing if
      // the URL is not encoded.
      var decodedUrl = URLDecoder.decode(requestUrl, StandardCharsets.UTF_8);
      var url = new URL(decodedUrl);
      // Only this URI constructor escapes the URL properly
      return new URI(
          url.getProtocol(),
          url.getUserInfo(),
          url.getHost(),
          url.getPort(),
          url.getPath(),
          url.getQuery(),
          null);
    } catch (MalformedURLException | URISyntaxException e) {
      LOG.error("Failed to parse URL {}, defaulting to requestUrl", requestUrl, e);
      return URI.create(requestUrl);
    }
  }
}
