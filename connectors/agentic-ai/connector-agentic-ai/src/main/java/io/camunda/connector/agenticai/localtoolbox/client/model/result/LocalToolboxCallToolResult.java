/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.localtoolbox.client.model.result;

import org.jspecify.annotations.Nullable;

/**
 * Result of a {@code tools/call} operation: the toolbox process instance's {@code toolCallResult}
 * output variable, after the router drove exactly one tool element to completion.
 */
public record LocalToolboxCallToolResult(String name, @Nullable Object content)
    implements LocalToolboxClientResult {}
