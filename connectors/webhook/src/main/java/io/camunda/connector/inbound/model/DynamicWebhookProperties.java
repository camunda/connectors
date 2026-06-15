/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model;

import io.camunda.connector.api.inbound.webhook.WebhookHttpResponse;
import io.camunda.connector.api.inbound.webhook.WebhookResultContext;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import java.util.function.Function;

/**
 * Element-scoped webhook properties.
 *
 * <p>Unlike {@link WebhookConnectorProperties}, these properties are <em>not</em> bound once at
 * connector activation. They are bound per activated element at correlation time and are excluded
 * from deduplication, so that webhook elements that differ only in their response expression are
 * deduplicated into a single executable while each still produces its own response.
 *
 * <p>This class is referenced from {@code @ElementTemplate(elementInputDataClass = ...)} so that
 * its properties are merged into the same element template as the connector-scoped properties. The
 * {@code inbound} wrapper keeps the generated property bindings (e.g. {@code
 * inbound.responseExpression}) identical to the previous layout.
 */
public record DynamicWebhookProperties(
    @TemplateProperty(
            id = "responseExpression",
            label = "Response expression",
            type = PropertyType.Text,
            group = "webhookResponse",
            description = "Expression used to generate the HTTP response",
            feel = FeelMode.required,
            optional = true)
        Function<WebhookResultContext, WebhookHttpResponse> responseExpression,
    @TemplateProperty(ignore = true)
        Function<WebhookResultContext, Object> responseBodyExpression) {

  public record DynamicWebhookPropertiesWrapper(DynamicWebhookProperties inbound) {}
}
