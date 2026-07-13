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
package io.camunda.connector.validator.core;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Maps a connector (by its {@code connectors/<name>} directory) to the connector name that its
 * native-operation descriptions must reference, overriding the name derived from the template's
 * top-level {@code name}.
 *
 * <p>Needed when the derived name is misleading — e.g. {@code camunda-message}'s templates are
 * named "Send Message Connector (…)", which derives to "Send Message", but the operations act on
 * Camunda, so their descriptions should reference "Camunda".
 */
public final class ConnectorNameOverrides {

  private ConnectorNameOverrides() {}

  private static final Map<String, String> OVERRIDES = Map.of("camunda-message", "Camunda");

  /** The override connector name for the connector owning {@code templateFile}, if any. */
  public static Optional<String> forFile(Path templateFile) {
    if (templateFile == null) {
      return Optional.empty();
    }
    for (Path segment : templateFile) {
      String override = OVERRIDES.get(segment.toString());
      if (override != null) {
        return Optional.of(override);
      }
    }
    return Optional.empty();
  }
}
