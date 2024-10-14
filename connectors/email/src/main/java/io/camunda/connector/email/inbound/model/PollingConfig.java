/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.inbound.model;

import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;

@TemplateDiscriminatorProperty(
    label = "Polling configuration",
    group = "listenerInfos",
    name = "data.pollingConfigDiscriminator",
    defaultValue = "unseenPollingConfig")
@TemplateSubType(id = "data.pollingConfigDiscriminator", label = "Polling Configuration")
public sealed interface PollingConfig permits PollUnseen, PollAll {
  HandlingStrategy handlingStrategy();

  String targetFolder();
}
