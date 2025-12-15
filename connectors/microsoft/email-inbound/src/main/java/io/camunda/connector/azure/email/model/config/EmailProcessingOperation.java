/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.azure.email.model.config;

import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;

@TemplateDiscriminatorProperty(
    label = "Postprocessing configuration",
    group = "postprocessing",
    name = "data.processingOperationDiscriminator",
    defaultValue = EmailProcessingOperation.MarkAsReadOperation.TYPE)
public sealed interface EmailProcessingOperation {

  @TemplateSubType(id = DeleteOperation.TYPE, label = "Delete")
  record DeleteOperation(
      @TemplateProperty(label = "Tick if email should be really deleted.", defaultValue = "false") boolean force)
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
  record MoveOperation(String targetFolder) implements EmailProcessingOperation {
    @TemplateProperty(ignore = true)
    public static final String TYPE = "move";
  }
}
