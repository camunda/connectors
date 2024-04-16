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

import com.google.api.client.http.HttpHeaders;
import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotEmpty;

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

  @Override
  public void setHeaders(final HttpHeaders headers) {
    String passwordForHeader = password;
    // checking against "SPEC_PASSWORD_EMPTY_PATTERN" to prevent breaking change
    if (password == null || password.equals("SPEC_PASSWORD_EMPTY_PATTERN")) {
      passwordForHeader = "";
    }
    headers.setBasicAuthentication(username, passwordForHeader);
  }

  @Override
  public String toString() {
    return "BasicAuthentication{" + "username='" + username + "'" + ", password=[REDACTED]" + "}";
  }
}
