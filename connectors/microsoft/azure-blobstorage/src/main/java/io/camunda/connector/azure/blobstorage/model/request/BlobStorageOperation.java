/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.azure.blobstorage.model.request;

import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;

@TemplateDiscriminatorProperty(
    label = "Operation",
    group = "operation",
    name = "operationDiscriminator",
    defaultValue = "uploadBlob")
@TemplateSubType(id = "operation", label = "Operation")
public sealed interface BlobStorageOperation permits DownloadBlob, UploadBlob {}
