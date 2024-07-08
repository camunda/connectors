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
