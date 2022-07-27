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
package io.camunda.connector.slack;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import io.camunda.connector.api.SecretStore;
import io.camunda.connector.api.Validator;
import java.io.IOException;
import java.util.Objects;

public class SlackRequest<T extends SlackRequestData> {

  private String token;
  private String method;

  private T data;

  public void validate(final Validator validator) {
    validator.require(token, "Slack API - Token");
    validator.require(method, "Slack API - Method");
    validator.require(data, "Slack API - Data");
    if (data != null) {
      data.validate(validator);
    }
  }

  public void replaceSecrets(final SecretStore secretStore) {
    token = secretStore.replaceSecret(token);
    if (data != null) {
      data.replaceSecrets(secretStore);
    }
  }

  public SlackResponse invoke(final Slack slack) throws SlackApiException, IOException {
    MethodsClient methods = slack.methods(token);
    return data.invoke(methods);
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public T getData() {
    return data;
  }

  public void setData(T data) {
    this.data = data;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SlackRequest<?> that = (SlackRequest<?>) o;
    return Objects.equals(token, that.token)
        && Objects.equals(method, that.method)
        && Objects.equals(data, that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(token, method, data);
  }

  @Override
  public String toString() {
    return "SlackRequest{"
        + "token='"
        + token
        + '\''
        + ", method='"
        + method
        + '\''
        + ", data="
        + data
        + '}';
  }
}
