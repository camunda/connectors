/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel;

import io.camunda.connector.agenticai.aiagent.model.request.v1.V1ProviderConfiguration;

/** {@link ChatModelApiConfiguration} backed by a built-in {@link V1ProviderConfiguration}. */
public record V1ChatModelApiConfiguration(V1ProviderConfiguration providerConfiguration)
    implements ChatModelApiConfiguration {}
