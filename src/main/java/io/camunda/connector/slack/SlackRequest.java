package io.camunda.connector.slack;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import io.camunda.connector.sdk.common.SecretStore;
import io.camunda.connector.sdk.common.Validator;
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
