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
package io.camunda.connector.http.base.blocklist;

import com.google.api.client.http.GenericUrl;

/**
 * This interface defines the contract for an HTTP Blocklist Manager. Implementing classes should
 * provide logic for validating a URL against a set of blocklist criteria.
 */
public interface HttpBlockListManager {

  /**
   * Validates the given URL against a blocklist. Implementing classes should define what being
   * "blocked" means (e.g., blocked domains, url, regex patterns, etc.)
   *
   * <p><strong>Configuration Example:</strong>
   *
   * <pre>{@code
   * // Set these environment variables to configure the blocklist
   * CAMUNDA_CONNECTOR_HTTP_BLOCK_URL_BadUrl=http://bad.url
   * CAMUNDA_CONNECTOR_HTTP_BLOCK_PORT_BadPort=8080,8081,8082
   * CAMUNDA_CONNECTOR_HTTP_BLOCK_REGEX_BadPattern=.*badpattern.*
   * }</pre>
   *
   * @param url The URL to validate, encapsulated as a {@link GenericUrl}.
   * @throws io.camunda.connector.api.error.ConnectorInputException if the URL is found to be in the
   *     blocklist.
   */
  void validateUrlAgainstBlocklist(GenericUrl url);
}
