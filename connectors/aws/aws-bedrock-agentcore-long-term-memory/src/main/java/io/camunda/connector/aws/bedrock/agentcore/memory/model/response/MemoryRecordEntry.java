/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.agentcore.memory.model.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MemoryRecordEntry(
    String memoryRecordId,
    String content,
    String memoryStrategyId,
    List<String> namespaces,
    Instant createdAt,
    Double score,
    Map<String, Object> metadata) {}
