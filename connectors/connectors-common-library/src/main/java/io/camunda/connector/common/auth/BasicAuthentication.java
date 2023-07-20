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

import com.google.api.client.http.HttpHeaders;
import com.google.common.base.Objects;
import jakarta.validation.constraints.NotEmpty;
import java.util.function.Function;

public class BasicAuthentication extends Authentication {
  private static final String SPEC_PASSWORD_EMPTY_PATTERN = "SPEC_PASSWORD_EMPTY_PATTERN";
  private static final Function<String, String> SPEC_PASSWORD =
      (psw) -> psw.equals(SPEC_PASSWORD_EMPTY_PATTERN) ? "" : psw;

  @NotEmpty private String username;
  @NotEmpty private String password;

  @Override
  public void setHeaders(final HttpHeaders headers) {
    headers.setBasicAuthentication(username, SPEC_PASSWORD.apply(password));
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(final String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    BasicAuthentication that = (BasicAuthentication) o;
    return Objects.equal(username, that.username) && Objects.equal(password, that.password);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(super.hashCode(), username, password);
  }

  @Override
  public String toString() {
    return "BasicAuthentication {"
        + "username='[REDACTED]'"
        + ", password='[REDACTED]'"
        + "}; Super: "
        + super.toString();
  }
}
