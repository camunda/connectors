/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ComprehendSyncRequestData.class, name = "sync"),
  @JsonSubTypes.Type(value = ComprehendAsyncRequestData.class, name = "async")
})
@JsonIgnoreProperties(ignoreUnknown = true)
@TemplateDiscriminatorProperty(name = "type", group = "input", label = "Execution type")
public sealed interface ComprehendRequestData
    permits ComprehendSyncRequestData, ComprehendAsyncRequestData {}
