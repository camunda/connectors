/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import static io.camunda.connector.agenticai.aiagent.model.request.v2.CustomProviderConfiguration.CUSTOM_ID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({@JsonSubTypes.Type(value = CustomProviderConfiguration.class, name = CUSTOM_ID)})
@TemplateDiscriminatorProperty(
    label = "Provider",
    group = "provider",
    name = "type",
    description = "Specify the LLM provider to use.",
    defaultValue = CUSTOM_ID)
public sealed interface ProviderConfiguration extends ChatModelConfiguration
    permits CustomProviderConfiguration {

  @Override
  String provider();

  @Override
  String model();
}
