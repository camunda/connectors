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
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class represents a Port Block that can be used to block URLs based on specific port numbers.
 *
 * <p>The Port Block is defined by a comma-separated list of port numbers and a block name. If a
 * URL's port matches any of the specified port numbers, it is considered blocked, and an exception
 * is thrown.
 */
public record PortBlock(String blockName, Set<Integer> blockedPorts) implements Block {
  private static final Logger logger = LoggerFactory.getLogger(PortBlock.class);

  /**
   * Creates a new instance of the PortBlock class with the specified port numbers and block name.
   *
   * @param value The comma-separated list of port numbers used for blocking.
   * @param blockName The name of the block for identification.
   * @return An instance of PortBlock.
   * @throws IllegalArgumentException if the provided port numbers or format are invalid.
   * @throws NumberFormatException if the provided port numbers cannot be parsed as integers.
   * @throws NullPointerException if either 'value' or 'blockName' is null.
   */
  public static PortBlock create(String value, String blockName) {
    Objects.requireNonNull(value, "value must not be null");
    Objects.requireNonNull(blockName, "blockName must not be null");
    Set<Integer> blockedPorts =
        Arrays.stream(value.split(","))
            .map(String::trim)
            .map(
                portStr -> {
                  try {
                    int parsedPort = Integer.parseInt(portStr);
                    if (parsedPort < 0 || parsedPort > 65535) {
                      logger.warn(
                          "Invalid port number {} provided for block: {}", parsedPort, blockName);
                      return null;
                    }
                    return parsedPort;
                  } catch (NumberFormatException e) {
                    logger.warn(
                        "Invalid port format provided for block: {} - {}",
                        blockName,
                        e.getMessage());
                    return null;
                  }
                })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    if (blockedPorts.isEmpty()) {
      throw new IllegalArgumentException("No valid ports provided for block: " + blockName);
    }

    return new PortBlock(blockName, blockedPorts);
  }

  /**
   * Validates a given URL against the blocking criteria.
   *
   * <p>If the URL's port matches any of the specified port numbers, a {@link
   * io.camunda.connector.api.error.ConnectorInputException} is thrown, indicating that the URL is
   * blocked.
   *
   * @param url The URL to validate, encapsulated as a {@link GenericUrl}.
   * @throws io.camunda.connector.api.error.ConnectorInputException if the URL matches the block
   *     conditions.
   */
  @Override
  public void validate(GenericUrl url) {
    if (blockedPorts.contains(url.getPort())) {
      BlocklistExceptionHelper.throwBlocklistException("port", blockName);
    }
  }
}
