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
package io.camunda.connector.http.client.client.apache.builder.parts;

import com.google.api.client.http.GenericUrl;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UrlEncoder {
  private static final Logger LOG = LoggerFactory.getLogger(UrlEncoder.class);

  public static URI toEncodedUri(String requestUrl, Boolean skipEncoding) {
    try {
      if (skipEncoding) {
        return URI.create(requestUrl);
      }
      return new GenericUrl(requestUrl).toURI();
    } catch (Exception e) {
      LOG.error("Failed to parse URL {}, defaulting to requestUrl", requestUrl, e);
      return URI.create(requestUrl);
    }
  }
}
