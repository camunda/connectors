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
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

@TemplateDiscriminatorProperty(
    label = "Folder Identifier Type",
    description =
        "Prefer using a folder ID if you are using a well known folder otherwise use the folder name",
    group = "pollingConfig",
    name = "folderSpecification",
    defaultValue = Folder.FolderById.TYPE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "folderSpecification")
@JsonSubTypes({
  @JsonSubTypes.Type(value = Folder.FolderById.class, name = Folder.FolderById.TYPE),
  @JsonSubTypes.Type(value = Folder.FolderByName.class, name = Folder.FolderByName.TYPE)
})
public sealed interface Folder {

  @TemplateSubType(id = FolderById.TYPE, label = "Folder ID")
  record FolderById(
      @TemplateProperty(
              label = "Folder ID",
              defaultValue = "inbox",
              feel = Property.FeelMode.optional,
              tooltip =
                  "The well-known folder ID or custom folder ID. <a href='https://learn.microsoft.com/en-us/graph/api/resources/mailfolder?view=graph-rest-1.0#properties' target='_blank'>See folder properties in the API</a>")
          @NotBlank
          @FEEL
          String folderId)
      implements Folder {
    @TemplateProperty(ignore = true)
    public static final String TYPE = "byId";
  }

  @TemplateSubType(id = FolderByName.TYPE, label = "Folder Name")
  record FolderByName(
      @TemplateProperty(
              label = "Folder Name",
              feel = Property.FeelMode.optional,
              tooltip = "The display name of the folder. Must be unique within the mailbox.")
          @NotBlank
          @FEEL
          String folderName)
      implements Folder {
    @TemplateProperty(ignore = true)
    public static final String TYPE = "byName";
  }
}
