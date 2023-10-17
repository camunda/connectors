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
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * This class represents a Regular Expression Block that can be used to block URLs based on a
 * regular expression pattern.
 *
 * <p>The Regex Block is defined by a blocking regular expression pattern and a block name. If a URL
 * matches the regular expression pattern, it is considered blocked, and an exception is thrown.
 */
public record RegexBlock(String blockName, Pattern pattern) implements Block {

  /**
   * Creates a new instance of the RegexBlock class with the specified regular expression pattern
   * and block name.
   *
   * @param value The regular expression pattern used for blocking.
   * @param blockName The name of the block for identification.
   * @return An instance of RegexBlock.
   * @throws IllegalArgumentException if the provided regular expression pattern is invalid.
   */
  public static RegexBlock create(String value, String blockName) {
    Objects.requireNonNull(value, "value must not be null");
    Objects.requireNonNull(blockName, "blockName must not be null");
    try {
      Pattern pattern = Pattern.compile(value);
      return new RegexBlock(blockName, pattern);
    } catch (PatternSyntaxException e) {
      throw new IllegalArgumentException(
          "Invalid regular expression provided for block: " + blockName, e);
    }
  }

  /**
   * Validates a given URL against the blocking criteria.
   *
   * <p>If the URL matches the regular expression pattern, a {@link
   * io.camunda.connector.api.error.ConnectorInputException} is thrown, indicating that the URL is
   * blocked.
   *
   * @param url The URL to validate, encapsulated as a {@link GenericUrl}.
   * @throws io.camunda.connector.api.error.ConnectorInputException if the URL matches the block
   *     conditions.
   */
  @Override
  public void validate(GenericUrl url) {
    if (pattern.matcher(url.build()).matches()) {
      BlocklistExceptionHelper.throwBlocklistException("URL", blockName);
    }
  }
}
