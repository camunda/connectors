/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel;

/**
 * Neutral, connector-agnostic descriptor a {@link ChatModelFactory} inspects to decide whether it
 * can serve a request and to build a {@link ChatModel}. Implementations are contributed by
 * connector variants and custom providers.
 */
public interface ChatModelConfiguration {}
