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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = BasicAuthentication.class, name = BasicAuthentication.TYPE),
  @JsonSubTypes.Type(value = NoAuthentication.class, name = NoAuthentication.TYPE),
  @JsonSubTypes.Type(value = OAuthAuthentication.class, name = OAuthAuthentication.TYPE),
  @JsonSubTypes.Type(value = BearerAuthentication.class, name = BearerAuthentication.TYPE),
  @JsonSubTypes.Type(value = ApiKeyAuthentication.class, name = ApiKeyAuthentication.TYPE)
})
@TemplateDiscriminatorProperty(
    label = "Type",
    group = "authentication",
    name = "type",
    defaultValue = NoAuthentication.TYPE,
    description = "Choose the authentication type. Select 'None' if no authentication is necessary")
public sealed interface Authentication
    permits ApiKeyAuthentication,
        BasicAuthentication,
        BearerAuthentication,
        NoAuthentication,
        OAuthAuthentication {}
