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

import io.camunda.connector.api.error.ConnectorInputException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.apache.hc.core5.net.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UrlEncoder {

  private static final Logger LOG = LoggerFactory.getLogger(ApacheRequestUriBuilder.class);

  private static String chooseEncoded(
      String input,
      Function<URL, String> getUrlPart,
      Function<URI, String> get,
      Function<URI, String> getRaw,
      BiFunction<URIBuilder, String, URIBuilder> setPart)
      throws MalformedURLException {
    URL url = new URL(input);
    try {
      URI uri = url.toURI();
      String rawPart = getRaw.apply(uri);
      String part = get.apply(uri);

      String fromDecoded = getRaw.apply(setPart.apply(new URIBuilder(), part).build());
      String fromRaw = get.apply(setPart.apply(new URIBuilder(), rawPart).build());

      return Objects.equals(fromDecoded, fromRaw) ? part : rawPart;
    } catch (URISyntaxException e) { // if it is not a valid URI, we need to encode it
      return getUrlPart.apply(url);
    }
  }

  public static URI toEncodedUri(String requestUrl, Boolean skipEncoding) {
    try {
      // We try to decode the URL first, because it might be encoded already
      // which would lead to double encoding. Decoding is safe here, because it does nothing if
      // the URL is not encoded.
      if (skipEncoding) {
        try {
          return URI.create(requestUrl);
        } catch (IllegalArgumentException e) {
          throw new ConnectorInputException(
              "Provided URL must be valid, when setting skipEncoding to true", e);
        }
      }

      URL url = new URL(requestUrl);
      return new URIBuilder()
          .setScheme(url.getProtocol())
          .setUserInfo(
              chooseEncoded(
                  requestUrl,
                  URL::getUserInfo,
                  URI::getUserInfo,
                  URI::getRawUserInfo,
                  URIBuilder::setUserInfo))
          .setHost(url.getHost())
          .setPort(url.getPort())
          .setPath(
              chooseEncoded(
                  requestUrl, URL::getPath, URI::getPath, URI::getRawPath, URIBuilder::setPath))
          .setCustomQuery(
              chooseEncoded(
                  requestUrl,
                  URL::getQuery,
                  URI::getQuery,
                  URI::getRawQuery,
                  URIBuilder::setCustomQuery))
          .setFragment(
              chooseEncoded(
                  requestUrl,
                  URL::getRef,
                  URI::getFragment,
                  URI::getRawFragment,
                  URIBuilder::setFragment))
          .build();
    } catch (MalformedURLException | URISyntaxException e) {
      LOG.error("Failed to parse URL {}, ", requestUrl, e);
      throw new ConnectorInputException("Invalid URL: " + requestUrl, e);
    }
  }
}
