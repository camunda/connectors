/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive.model.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = FolderResource.class, name = "folder"),
  @JsonSubTypes.Type(value = FileResource.class, name = "file"),
  @JsonSubTypes.Type(value = UploadResource.class, name = "upload"),
  @JsonSubTypes.Type(value = DownloadResource.class, name = "download")
})
@TemplateDiscriminatorProperty(
    label = "Operation",
    group = "operation",
    name = "type",
    defaultValue = "folder",
    description = "Select the Google Drive operation to perform")
public sealed interface Resource
    permits FolderResource, FileResource, UploadResource, DownloadResource {}
