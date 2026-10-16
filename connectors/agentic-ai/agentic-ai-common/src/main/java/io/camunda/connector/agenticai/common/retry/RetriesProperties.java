/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.common.retry;

import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

public record RetriesProperties(
    @DefaultValue("4") @PositiveOrZero Integer maxRetries,
    @DefaultValue("PT1S") Duration initialRetryDelay) {}
