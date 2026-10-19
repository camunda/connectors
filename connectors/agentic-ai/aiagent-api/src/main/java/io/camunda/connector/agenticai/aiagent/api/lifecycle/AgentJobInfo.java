/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.lifecycle;

import io.camunda.connector.agenticai.aiagent.api.Experimental;
import org.jspecify.annotations.Nullable;

/**
 * Identity of the job activation. The lease token is a fencing credential and is never exposed;
 * {@code attempt} is a short, non-reversible discriminator derived from it, absent when the job was
 * activated without a lease.
 */
@Experimental
public record AgentJobInfo(
    long jobKey,
    long processInstanceKey,
    long processDefinitionKey,
    long elementInstanceKey,
    String elementId,
    String bpmnProcessId,
    String tenantId,
    @Nullable String attempt) {}
