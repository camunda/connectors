/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.inbound.polling.model;

import io.camunda.connector.generator.java.annotation.NestedProperties;

public record A2aPollingRequest(
    @NestedProperties(addNestedPath = false) A2aPollingActivationProperties activation,
    @NestedProperties(addNestedPath = false) A2aPollingRuntimeProperties runtime) {}
