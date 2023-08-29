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
package io.camunda.connector.generator.core.example;

import io.camunda.connector.generator.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.annotation.TemplateProperty;
import io.camunda.connector.generator.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.generator.annotation.TemplateSubType;
import io.camunda.connector.generator.core.example.MyConnectorInput.Authorization.BasicAuth;
import io.camunda.connector.generator.core.example.MyConnectorInput.Authorization.TokenAuth;
import io.camunda.connector.generator.dsl.Property.FeelMode;

public record MyConnectorInput(
    Authorization authorization,
    @TemplateProperty(type = PropertyType.Text, group = "message") String message,
    String recipient) {

  @TemplateDiscriminatorProperty(id = "authType", label = "Auth type")
  sealed interface Authorization permits BasicAuth, TokenAuth {

    @TemplateSubType(id = "basic")
    record BasicAuth(
        @TemplateProperty(label = "Username", group = "auth", feel = FeelMode.optional)
            String username,
        @TemplateProperty(label = "Password", group = "auth", feel = FeelMode.optional)
            String password)
        implements Authorization {}

    @TemplateSubType(id = "token")
    record TokenAuth(@TemplateProperty(label = "Token", group = "auth") String token)
        implements Authorization {}
  }
}
