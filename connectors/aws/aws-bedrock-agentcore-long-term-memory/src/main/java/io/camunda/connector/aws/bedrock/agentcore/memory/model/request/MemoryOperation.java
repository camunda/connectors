/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.agentcore.memory.model.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "operationDiscriminator")
@JsonSubTypes({
  @JsonSubTypes.Type(value = RetrieveOperation.class, name = "retrieve"),
  @JsonSubTypes.Type(value = ListOperation.class, name = "list"),
})
@TemplateDiscriminatorProperty(
    label = "Operation",
    group = "operation",
    name = "operationDiscriminator",
    defaultValue = "retrieve")
public sealed interface MemoryOperation permits RetrieveOperation, ListOperation {}
