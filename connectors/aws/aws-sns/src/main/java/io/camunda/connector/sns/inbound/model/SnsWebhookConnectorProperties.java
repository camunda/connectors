/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.inbound.model;

import java.util.List;
import java.util.Map;

public record SnsWebhookConnectorProperties(
    Map<String, String> genericProperties,
    String context,
    SubscriptionAllowListFlag subscriptionAllowListFlag,
    List<String> subscriptionAllowList) {}
