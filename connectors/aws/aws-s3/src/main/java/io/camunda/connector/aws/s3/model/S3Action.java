package io.camunda.connector.aws.s3.model;

import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;

@TemplateDiscriminatorProperty(
    label = "Action",
    group = "action",
    name = "action",
    defaultValue = "uploadObject")
@TemplateSubType(id = "action", label = "Action")
public sealed interface S3Action permits DeleteS3Action, DownloadS3Action, UploadS3Action {}
