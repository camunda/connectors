/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.automationanywhere.model.request.operation;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = AddWorkItemOperationData.class, name = "addWorkItemsToTheQueue"),
  @JsonSubTypes.Type(value = GetWorkItemOperationData.class, name = "listWorkItemsInQueue")
})
@TemplateDiscriminatorProperty(label = "Type", group = "operation", name = "type")
public sealed interface OperationData permits AddWorkItemOperationData, GetWorkItemOperationData {}
