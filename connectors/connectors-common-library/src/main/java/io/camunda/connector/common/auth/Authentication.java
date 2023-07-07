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
package io.camunda.connector.common.auth;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.api.client.http.HttpHeaders;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = BasicAuthentication.class, name = "basic"),
  @JsonSubTypes.Type(value = NoAuthentication.class, name = "noAuth"),
  @JsonSubTypes.Type(value = CustomAuthentication.class, name = "credentialsInBody"),
  @JsonSubTypes.Type(value = OAuthAuthentication.class, name = "oauth-client-credentials-flow"),
  @JsonSubTypes.Type(value = BearerAuthentication.class, name = "bearer")
})
public abstract class Authentication {

  public abstract void setHeaders(HttpHeaders headers);
}
