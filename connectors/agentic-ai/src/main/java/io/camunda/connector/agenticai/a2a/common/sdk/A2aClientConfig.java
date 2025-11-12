/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.common.sdk;

import io.camunda.connector.agenticai.model.AgenticAiRecord;
import javax.annotation.Nullable;

@AgenticAiRecord
public record A2aClientConfig(int historyLength, @Nullable Boolean supportPolling) {}
