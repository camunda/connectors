/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model.auth;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotEmpty;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@TemplateSubType(id = BasicAuthentication.TYPE, label = "Basic")
public record BasicAuthentication(
    @FEEL @NotEmpty @TemplateProperty(group = "authentication") String username,
    @FEEL
        @TemplateProperty(
            group = "authentication",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String password)
    implements Authentication {

  @TemplateProperty(ignore = true)
  public static final String TYPE = "basic";
}
