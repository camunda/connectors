/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.http.base.auth;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.api.client.http.HttpHeaders;
import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@TemplateDiscriminatorProperty(
    label = "API key",
    group = "authentication",
    name = "type",
    description = "Send API key in the header, or as parameter in the query parameters")
@TemplateSubType(id = ApiKeyAuthentication.TYPE, label = "API key")
public record ApiKeyAuthentication(
    @FEEL
        @NotNull
        @TemplateProperty(
            group = "authentication",
            type = TemplateProperty.PropertyType.Dropdown,
            defaultValue = "headers",
            choices = {
              @TemplateProperty.DropdownPropertyChoice(value = "headers", label = "Headers"),
              @TemplateProperty.DropdownPropertyChoice(value = "query", label = "Query parameters")
            },
            description = "Choose type: Send API key in header or as query parameter.")
        ApiKeyLocation apiKeyLocation,
    @FEEL @NotEmpty @TemplateProperty(group = "authentication", label = "API key name") String name,
    @FEEL @NotEmpty @TemplateProperty(group = "authentication", label = "API key value")
        String value)
    implements Authentication {
  @TemplateProperty(ignore = true)
  public static final String TYPE = "apiKey";

  @Override
  public void setHeaders(final HttpHeaders headers) {
    if (ApiKeyLocation.HEADERS == apiKeyLocation) {
      headers.set(name, value);
    }
  }

  public boolean isQueryLocationApiKeyAuthentication() {
    return ApiKeyLocation.QUERY == apiKeyLocation;
  }

  @Override
  public String toString() {
    return "ApiKeyAuthentication{"
        + "apiKeyLocation="
        + apiKeyLocation
        + ", name='"
        + name
        + "'"
        + ", value=[REDACTED]"
        + "}";
  }
}
