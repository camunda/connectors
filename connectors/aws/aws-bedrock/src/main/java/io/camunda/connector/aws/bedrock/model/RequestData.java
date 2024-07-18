/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@TemplateDiscriminatorProperty(
    label = "Action",
    group = "action",
    name = "action",
    defaultValue = "invokeModel")
@TemplateSubType(id = "action", label = "Action")
public sealed interface RequestData permits InvokeModelData, ConverseData {
  BedrockResponse execute(BedrockRuntimeClient bedrockRuntimeClient, ObjectMapper mapperInstance);
}
