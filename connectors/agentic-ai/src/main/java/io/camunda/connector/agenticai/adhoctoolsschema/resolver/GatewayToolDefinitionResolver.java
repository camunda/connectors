/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.resolver;

import io.camunda.connector.agenticai.model.tool.GatewayToolDefinition;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import java.util.List;

public interface GatewayToolDefinitionResolver {
  List<GatewayToolDefinition> resolveGatewayToolDefinitions(List<FlowNode> elements);
}
