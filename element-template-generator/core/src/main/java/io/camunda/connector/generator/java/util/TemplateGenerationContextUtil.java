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
package io.camunda.connector.generator.java.util;

import static io.camunda.connector.util.reflection.ReflectionUtil.getRequiredAnnotation;

import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.generator.api.GeneratorConfiguration;
import io.camunda.connector.generator.java.annotation.BpmnType;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.util.TemplateGenerationContext.Inbound;
import io.camunda.connector.generator.java.util.TemplateGenerationContext.Outbound;
import java.util.Set;

public class TemplateGenerationContextUtil {

  private static final Set<BpmnType> OUTBOUND_SUPPORTED_ELEMENT_TYPES =
      Set.of(
          BpmnType.SERVICE_TASK,
          BpmnType.SEND_TASK,
          BpmnType.INTERMEDIATE_THROW_EVENT,
          BpmnType.SCRIPT_TASK,
          BpmnType.MESSAGE_END_EVENT);

  private static final Set<BpmnType> INBOUND_SUPPORTED_ELEMENT_TYPES =
      Set.of(
          BpmnType.START_EVENT,
          BpmnType.INTERMEDIATE_CATCH_EVENT,
          BpmnType.MESSAGE_START_EVENT,
          BpmnType.BOUNDARY_EVENT,
          BpmnType.RECEIVE_TASK);
  private static final GeneratorConfiguration.ConnectorElementType OUTBOUND_DEFAULT_ELEMENT_TYPE =
      new GeneratorConfiguration.ConnectorElementType(
          Set.of(BpmnType.TASK), BpmnType.SERVICE_TASK, null, null);

  private static final Set<GeneratorConfiguration.ConnectorElementType>
      INBOUND_DEFAULT_ELEMENT_TYPES =
          Set.of(
              new GeneratorConfiguration.ConnectorElementType(
                  Set.of(BpmnType.START_EVENT), BpmnType.START_EVENT, null, null),
              new GeneratorConfiguration.ConnectorElementType(
                  Set.of(BpmnType.INTERMEDIATE_CATCH_EVENT, BpmnType.INTERMEDIATE_THROW_EVENT),
                  BpmnType.INTERMEDIATE_CATCH_EVENT,
                  null,
                  null),
              new GeneratorConfiguration.ConnectorElementType(
                  Set.of(BpmnType.MESSAGE_START_EVENT), BpmnType.MESSAGE_START_EVENT, null, null),
              new GeneratorConfiguration.ConnectorElementType(
                  Set.of(BpmnType.BOUNDARY_EVENT), BpmnType.BOUNDARY_EVENT, null, null));

  public static TemplateGenerationContext createContext(
      Class<?> connectorDefinition, GeneratorConfiguration configuration) {

    var outboundAnnotation = connectorDefinition.getAnnotation(OutboundConnector.class);
    var inboundAnnotation = connectorDefinition.getAnnotation(InboundConnector.class);

    if (outboundAnnotation != null && inboundAnnotation != null) {
      throw new IllegalArgumentException(
          "Connector definition must be annotated with either @InboundConnector or @OutboundConnector, not both");
    }

    if (outboundAnnotation != null) {
      return createOutboundContext(connectorDefinition, configuration, outboundAnnotation);
    } else {
      return createInboundContext(connectorDefinition, configuration, inboundAnnotation);
    }
  }

  private static Outbound createOutboundContext(
      Class<?> connectorDefinition,
      GeneratorConfiguration configuration,
      OutboundConnector connector) {
    var template = getRequiredAnnotation(connectorDefinition, ElementTemplate.class);

    GeneratorConfiguration mergedConfig = ConfigurationUtil.fromAnnotation(template, configuration);
    var elementTypes = mergedConfig.elementTypes();
    if (elementTypes.isEmpty()) {
      elementTypes = Set.of(OUTBOUND_DEFAULT_ELEMENT_TYPE);
    }
    elementTypes.stream()
        .filter(t -> !OUTBOUND_SUPPORTED_ELEMENT_TYPES.contains(t.elementType()))
        .findFirst()
        .ifPresent(
            t -> {
              throw new IllegalArgumentException(
                  "Unsupported element type " + t.elementType() + " for outbound connector");
            });
    return new Outbound(connector.type(), elementTypes);
  }

  private static Inbound createInboundContext(
      Class<?> connectorDefinition,
      GeneratorConfiguration configuration,
      InboundConnector connector) {
    var template = getRequiredAnnotation(connectorDefinition, ElementTemplate.class);

    GeneratorConfiguration mergedConfig = ConfigurationUtil.fromAnnotation(template, configuration);
    var elementTypes = mergedConfig.elementTypes();
    if (elementTypes.isEmpty()) {
      elementTypes = INBOUND_DEFAULT_ELEMENT_TYPES;
    }
    elementTypes.stream()
        .filter(t -> !INBOUND_SUPPORTED_ELEMENT_TYPES.contains(t.elementType()))
        .findFirst()
        .ifPresent(
            t -> {
              throw new IllegalArgumentException(
                  "Unsupported element type " + t.elementType() + " for inbound connector");
            });
    return new Inbound(connector.type(), elementTypes);
  }
}
