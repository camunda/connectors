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
package io.camunda.connector.generator.api;

import io.camunda.connector.generator.java.annotation.BpmnType;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/** Configuration for the element template generator */
public record GeneratorConfiguration(
    ConnectorMode connectorMode,
    String templateId,
    String templateName,
    Long templateVersion,
    Set<ConnectorElementType> elementTypes,
    Map<GenerationFeature, Boolean> features) {

  /**
   * Connectors in hybrid mode have a configurable task definition type (for outbound), or a
   * configurable connector type (for inbound) property. This allows to run multiple connector
   * runtimes against the same Camunda cluster and distinguish between them on the BPMN level.
   */
  public enum ConnectorMode {
    NORMAL,
    HYBRID
  }

  public static String HYBRID_TEMPLATE_ID_SUFFIX = "-hybrid";
  public static String HYBRID_TEMPLATE_NAME_PREFIX = "Hybrid ";

  public enum GenerationFeature {
    INBOUND_DEDUPLICATION,
    ACKNOWLEDGEMENT_STRATEGY_SELECTION,
    /**
     * When enabled, adds a synchronous response toggle to inbound connector element templates.
     * Supports both synchronous process instance creation with result (start events) and
     * synchronous message correlation (message events).
     */
    SYNCHRONOUS_RESPONSE,

    /**
     * When enabled, the HTTP-family generators (OpenAPI, Postman) fall back to the legacy inline
     * {@code authentication.*} {@code zeebe:input} properties instead of the default
     * credential-only "Authentication credential" chooser that references the shared {@code
     * io.camunda.connectors:rest-authentication:1} configuration template.
     *
     * <p>Absent (the default) means {@code false}, i.e. the new credential-only behavior. This
     * flag, and the legacy inline path it guards, are retained only for callers that cannot yet
     * consume configuration-template credentials (e.g. an older Modeler/Hub) and are slated for
     * removal once that support is generally available.
     *
     * @see <a href="https://github.com/camunda/connectors/issues/8113">#8113</a>
     */
    LEGACY_INLINE_AUTHENTICATION
  }

  public static final GeneratorConfiguration DEFAULT =
      new GeneratorConfiguration(
          ConnectorMode.NORMAL, null, null, null, Collections.emptySet(), Collections.emptyMap());

  public record ConnectorElementType(
      Set<BpmnType> appliesTo,
      BpmnType elementType,
      String templateNameOverride,
      String templateIdOverride) {}
}
