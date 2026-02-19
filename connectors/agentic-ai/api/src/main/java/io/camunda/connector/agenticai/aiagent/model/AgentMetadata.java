/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.model.AgenticAiRecord;

/**
 * Metadata about an AI Agent's execution context, used for detecting process definition migrations.
 *
 * @param processDefinitionKey The key of the process definition this agent was initialized with
 * @param processInstanceKey The key of the process instance this agent is executing in
 */
@AgenticAiRecord
public record AgentMetadata(Long processDefinitionKey, Long processInstanceKey)
    implements AgentMetadataBuilder.With {}
