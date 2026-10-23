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
package io.camunda.connector.runtime.managedcode;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The script and optional dependency manifest resolved by the engine for one job. */
record LinkedResources(long scriptResourceKey, Optional<Long> dependenciesResourceKey) {

  static final String HEADER = "linkedResources";
  static final String SCRIPT_LINK = "script";
  static final String DEPENDENCIES_LINK = "dependencies";

  /**
   * Parses the {@code linkedResources} job header, a JSON array of {@code {"resourceKey",
   * "resourceType", "linkName"}} objects.
   *
   * @throws InvalidJobException when the header is missing, malformed or has no script link
   */
  static LinkedResources parse(Map<String, String> headers, ObjectMapper objectMapper) {
    final var header = headers.get(HEADER);
    if (header == null || header.isBlank()) {
      throw new InvalidJobException(
          "Missing '%s' header: the managed script is not linked to the task".formatted(HEADER));
    }
    final List<Link> links;
    try {
      links = objectMapper.readValue(header, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      throw new InvalidJobException("The '%s' header is not a valid JSON array".formatted(HEADER));
    }
    final var script =
        resourceKey(links, SCRIPT_LINK)
            .orElseThrow(
                () ->
                    new InvalidJobException(
                        "The '%s' header has no '%s' link".formatted(HEADER, SCRIPT_LINK)));
    return new LinkedResources(script, resourceKey(links, DEPENDENCIES_LINK));
  }

  private static Optional<Long> resourceKey(List<Link> links, String linkName) {
    final var matches =
        links == null
            ? List.<Link>of()
            : links.stream()
                .filter(link -> link != null && linkName.equals(link.linkName()))
                .toList();
    if (matches.isEmpty()) {
      return Optional.empty();
    }
    if (matches.size() > 1) {
      throw new InvalidJobException(
          "The '%s' header has more than one '%s' link".formatted(HEADER, linkName));
    }
    final var resourceKey = matches.getFirst().resourceKey();
    try {
      return Optional.of(Long.parseLong(resourceKey));
    } catch (NumberFormatException e) {
      throw new InvalidJobException(
          "The '%s' link has an invalid resourceKey '%s'".formatted(linkName, resourceKey));
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Link(String resourceKey, String resourceType, String linkName) {}
}
