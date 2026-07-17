/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.api;

/**
 * Neutral, connector-agnostic descriptor a {@link ChatModelApiFactory} inspects to decide whether
 * it can serve a request and to build a {@link ChatModelApi}. Implementations are contributed by
 * connector variants and custom providers.
 */
public interface ChatModelApiConfiguration {}
