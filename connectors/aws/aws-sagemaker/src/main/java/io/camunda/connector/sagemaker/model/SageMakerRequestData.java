/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.sagemaker.model;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SageMakerRequestData(
    @TemplateProperty(
            label = "Inference type",
            group = "input",
            type = TemplateProperty.PropertyType.Dropdown,
            defaultValue = "SYNC",
            feel = FeelMode.disabled,
            choices = {
              @TemplateProperty.DropdownPropertyChoice(value = "SYNC", label = "Real-time"),
              @TemplateProperty.DropdownPropertyChoice(value = "ASYNC", label = "Asynchronous")
            },
            description = "Endpoint inference type")
        @NotNull
        SageMakerInvocationType invocationType,
    @NotBlank
        @TemplateProperty(
            group = "input",
            label = "Endpoint name",
            description =
                "The name of the endpoint. <a href=\"https://docs.aws.amazon.com/sagemaker/latest/APIReference/API_Operations_Amazon_SageMaker_Runtime.html\">Learn more</a>")
        String endpointName,
    @TemplateProperty(
            label = "Payload",
            group = "input",
            description =
                "Input data. <a href=\"https://docs.aws.amazon.com/sagemaker/latest/APIReference/API_Operations_Amazon_SageMaker_Runtime.html\">Learn more</a>",
            type = TemplateProperty.PropertyType.Text,
            constraints = @PropertyConstraints(notEmpty = true),
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.invocationType",
                    equals = "SYNC"))
        Object body,
    @NotBlank
        @TemplateProperty(
            group = "input",
            label = "Content type",
            description =
                "The MIME type of the input data in the request body. <a href=\"https://docs.aws.amazon.com/sagemaker/latest/APIReference/API_Operations_Amazon_SageMaker_Runtime.html\">Learn more</a>",
            constraints = @PropertyConstraints(notEmpty = true),
            defaultValue = "application/json")
        String contentType,
    @NotBlank
        @TemplateProperty(
            group = "input",
            label = "Accept",
            description =
                "The desired MIME type of the inference response from the model container. <a href=\"https://docs.aws.amazon.com/sagemaker/latest/APIReference/API_Operations_Amazon_SageMaker_Runtime.html\">Learn more</a>",
            constraints = @PropertyConstraints(notEmpty = true),
            defaultValue = "application/json")
        String accept,
    @TemplateProperty(
            group = "input",
            label = "Custom attributes",
            description =
                "Provides additional information about a request for an inference submitted to a model hosted at an Amazon SageMaker endpoint. <a href=\"https://docs.aws.amazon.com/sagemaker/latest/APIReference/API_Operations_Amazon_SageMaker_Runtime.html\">Learn more</a>",
            optional = true)
        String customAttributes,
    @TemplateProperty(
            group = "input",
            label = "Target model",
            description =
                "The model to request for inference when invoking a multi-model endpoint. <a href=\"https://docs.aws.amazon.com/sagemaker/latest/APIReference/API_Operations_Amazon_SageMaker_Runtime.html\">Learn more</a>",
            optional = true,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.invocationType",
                    equals = "SYNC"))
        String targetModel,
    @TemplateProperty(
            group = "input",
            label = "Target variant",
            description =
                "Specify the production variant to send the inference request to. <a href=\"https://docs.aws.amazon.com/sagemaker/latest/APIReference/API_Operations_Amazon_SageMaker_Runtime.html\">Learn more</a>",
            optional = true,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.invocationType",
                    equals = "SYNC"))
        String targetVariant,
    @TemplateProperty(
            group = "input",
            label = "Target invocation host name",
            description =
                "If the endpoint hosts multiple containers and is configured to use direct invocation, this parameter specifies the host name of the container to invoke. <a href=\"https://docs.aws.amazon.com/sagemaker/latest/APIReference/API_Operations_Amazon_SageMaker_Runtime.html\">Learn more</a>",
            optional = true,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.invocationType",
                    equals = "SYNC"))
        String targetContainerHostname,
    @TemplateProperty(
            group = "input",
            label = "Inference ID",
            description =
                "If you provide a value, it is added to the captured data when you enable data capture on the endpoint. <a href=\"https://docs.aws.amazon.com/sagemaker/latest/APIReference/API_Operations_Amazon_SageMaker_Runtime.html\">Learn more</a>",
            optional = true)
        String inferenceId,
    @TemplateProperty(
            label = "Enable explanations",
            group = "input",
            type = TemplateProperty.PropertyType.Dropdown,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.invocationType",
                    equals = "SYNC"),
            defaultValue = "NOT_SET",
            choices = {
              @TemplateProperty.DropdownPropertyChoice(value = "NOT_SET", label = "Not set"),
              @TemplateProperty.DropdownPropertyChoice(value = "YES", label = "True"),
              @TemplateProperty.DropdownPropertyChoice(value = "NO", label = "False")
            },
            description =
                "Whether request needs to be explained. <a href=\"https://docs.aws.amazon.com/sagemaker/latest/APIReference/API_Operations_Amazon_SageMaker_Runtime.html\">Learn more</a>")
        SageMakerEnableExplanations enableExplanations,
    @TemplateProperty(
            group = "input",
            label = "Inference component name",
            description =
                "If the endpoint hosts one or more inference components, this parameter specifies the name of inference component to invoke. <a href=\"https://docs.aws.amazon.com/sagemaker/latest/APIReference/API_Operations_Amazon_SageMaker_Runtime.html\">Learn more</a>",
            optional = true,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.invocationType",
                    equals = "SYNC"))
        String inferenceComponentName,
    @TemplateProperty(
            group = "input",
            label = "Input location",
            description =
                "The Amazon S3 URI where the inference request payload is stored. <a href=\"https://docs.aws.amazon.com/sagemaker/latest/APIReference/API_Operations_Amazon_SageMaker_Runtime.html\">Learn more</a>",
            constraints = @PropertyConstraints(notEmpty = true),
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.invocationType",
                    equals = "ASYNC"))
        String inputLocation,
    @TemplateProperty(
            group = "input",
            label = "Request time-to-leave in seconds",
            description =
                "Maximum age in seconds a request can be in the queue before it is marked as expired. The default is 21,600 seconds. <a href=\"https://docs.aws.amazon.com/sagemaker/latest/APIReference/API_Operations_Amazon_SageMaker_Runtime.html\">Learn more</a>",
            optional = true,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.invocationType",
                    equals = "ASYNC"))
        String requestTTLSeconds,
    @TemplateProperty(
            group = "input",
            label = "Invocation timeout in seconds",
            description =
                "Maximum amount of time in seconds a request can be processed before it is marked as expired. The default is 900 seconds. <a href=\"https://docs.aws.amazon.com/sagemaker/latest/APIReference/API_Operations_Amazon_SageMaker_Runtime.html\">Learn more</a>",
            optional = true,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "input.invocationType",
                    equals = "ASYNC"))
        String invocationTimeoutSeconds) {}
