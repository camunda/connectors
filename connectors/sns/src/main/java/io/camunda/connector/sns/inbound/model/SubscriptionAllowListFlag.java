/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.inbound.model;

// Any means that any topic can subscribe onto this process
// Specific means that the user has to specify explicit list of ARNs
public enum SubscriptionAllowListFlag {
  any,
  specific
}
