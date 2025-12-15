/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email.model.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@TemplateDiscriminatorProperty(
    label = "Postprocessing configuration",
    group = "postprocessing",
    name = "processingOperationDiscriminator",
    defaultValue = EmailProcessingOperation.MarkAsReadOperation.TYPE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "processingOperationDiscriminator")
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
public sealed interface EmailProcessingOperation {

  @TemplateSubType(id = DeleteOperation.TYPE, label = "Delete")
  record DeleteOperation(
      @TemplateProperty(
              label = "Tick if email should be really deleted.",
              defaultValue = "false",
              defaultValueType = TemplateProperty.DefaultValueType.Boolean)
          @FEEL
          boolean force)
      implements EmailProcessingOperation {
    @TemplateProperty(ignore = true)
    public static final String TYPE = "delete";
  }

  @TemplateSubType(id = MarkAsReadOperation.TYPE, label = "Mark as Read")
  record MarkAsReadOperation() implements EmailProcessingOperation {
    @TemplateProperty(ignore = true)
    public static final String TYPE = "mark-read";
  }

  @TemplateSubType(id = MoveOperation.TYPE, label = "Move to other folder")
  record MoveOperation(@Valid @NotNull Folder targetFolder) implements EmailProcessingOperation {
    @TemplateProperty(ignore = true)
    public static final String TYPE = "move";
  }
}
