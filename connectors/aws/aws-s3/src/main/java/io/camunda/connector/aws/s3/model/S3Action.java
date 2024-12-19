/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.s3.model;

import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;

@TemplateDiscriminatorProperty(
    label = "Action",
    group = "action",
    name = "actionDiscriminator",
    defaultValue = "uploadObject")
@TemplateSubType(id = "action", label = "Action")
public sealed interface S3Action permits DeleteS3Action, DownloadS3Action, UploadS3Action {}
