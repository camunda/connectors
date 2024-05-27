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

/**
 * This is a sealed interface that represents a Block of a URL, Port, or Regex type. Classes that
 * implement this interface are permitted to validate a URL against a particular set of blocking
 * criteria.
 *
 * <p><strong>Note:</strong> Constructors of implementing classes can throw IllegalArgumentException
 * or other exceptions if the provided value does not meet the expected criteria.
 *
 * <p>Permitted subtypes: {@link UrlBlock}, {@link PortBlock}, {@link RegexBlock}
 *
 * @see UrlBlock
 * @see PortBlock
 * @see RegexBlock
 */
public sealed interface Block permits UrlBlock, PortBlock, RegexBlock {
  /**
   * Validates a given URL against the blocking criteria. Implementing classes should throw an
   * appropriate exception if the URL is found to match the block conditions.
   *
   * @param url The URL to validate.
   * @throws io.camunda.connector.api.error.ConnectorInputException if the URL matches the block
   *     conditions.
   */
  void validate(String url);
}
