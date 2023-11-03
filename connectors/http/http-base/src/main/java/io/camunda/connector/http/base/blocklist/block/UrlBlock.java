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
package io.camunda.connector.http.base.blocklist.block;

import com.google.api.client.http.GenericUrl;
import io.camunda.connector.http.base.blocklist.util.BlocklistExceptionHelper;

/**
 * This class represents a URL Block that can be used to block specific URLs based on a substring
 * match.
 *
 * <p>The URL Block is defined by a blocking value and a block name. If a URL contains the blocking
 * value as a substring, it is considered blocked, and an exception is thrown.
 *
 * <p>For example, if the value is "example.com" and a URL contains "http://www.example.com", it
 * will be blocked.
 */
public record UrlBlock(String value, String blockName) implements Block {
  /**
   * Validates a given URL against the blocking criteria.
   *
   * <p>If the URL contains the blocking value as a substring, a {@link
   * io.camunda.connector.api.error.ConnectorInputException} is thrown, indicating that the URL is
   * blocked.
   *
   * @param url The URL to validate, encapsulated as a {@link GenericUrl}.
   * @throws io.camunda.connector.api.error.ConnectorInputException if the URL matches the block
   *     conditions.
   */
  public void validate(GenericUrl url) {
    if (url.build().contains(value)) {
      BlocklistExceptionHelper.throwBlocklistException("URL", blockName);
    }
  }
}
