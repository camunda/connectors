/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email.model.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = EmailProcessingOperation.DeleteOperation.class,
      name = EmailProcessingOperation.DeleteOperation.TYPE),
  @JsonSubTypes.Type(
      value = EmailProcessingOperation.MarkAsReadOperation.class,
      name = EmailProcessingOperation.MarkAsReadOperation.TYPE),
  @JsonSubTypes.Type(
      value = EmailProcessingOperation.MoveOperation.class,
      name = EmailProcessingOperation.MoveOperation.TYPE),
})
@TemplateDiscriminatorProperty(
    label = "Postprocessing action",
    group = "postprocessing",
    name = "type",
    defaultValue = "simple",
    description = "Specify the Email postprocessing strategy.")
public sealed interface EmailProcessingOperation {

  @TemplateSubType(id = DeleteOperation.TYPE, label = "Delete")
  record DeleteOperation(boolean force) implements EmailProcessingOperation {
    public static final String TYPE = "delete";
  }

  @TemplateSubType(id = MarkAsReadOperation.TYPE, label = "Mark as Read")
  record MarkAsReadOperation() implements EmailProcessingOperation {
    public static final String TYPE = "mark-read";
  }

  @TemplateSubType(id = MoveOperation.TYPE, label = "Move to other folder")
  record MoveOperation(String targetFolder) implements EmailProcessingOperation {
    public static final String TYPE = "move";
  }
}
