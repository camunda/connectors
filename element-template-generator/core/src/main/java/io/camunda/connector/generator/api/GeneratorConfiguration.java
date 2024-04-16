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

import io.camunda.connector.generator.dsl.BpmnType;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/** Configuration for the element template generator */
public record GeneratorConfiguration(
    ConnectorMode connectorMode,
    String templateId,
    String templateName,
    Integer templateVersion,
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

  public enum GenerationFeature {
    INBOUND_DEDUPLICATION
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
