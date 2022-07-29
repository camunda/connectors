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
package io.camunda.connector.sendgrid;

import io.camunda.connector.api.SecretStore;
import io.camunda.connector.api.Validator;
import java.util.Objects;

public class SendGridContent {
  private String subject;
  private String type;
  private String value;

  public void validateWith(final Validator validator) {
    validator.require(subject, "Email Content - Subject");
    validator.require(type, "Email Content - Content Type");
    validator.require(value, "Email Content - Body");
  }

  public void replaceSecrets(final SecretStore secretStore) {
    subject =
        secretStore.replaceSecret(
            Objects.requireNonNull(subject, "Field 'content.subject' required in request"));
    type =
        secretStore.replaceSecret(
            Objects.requireNonNull(type, "Field 'content.type' required in request"));
    value =
        secretStore.replaceSecret(
            Objects.requireNonNull(value, "Field 'content.value' required in request"));
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(final String subject) {
    this.subject = subject;
  }

  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public String getValue() {
    return value;
  }

  public void setValue(final String value) {
    this.value = value;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SendGridContent that = (SendGridContent) o;
    return Objects.equals(subject, that.subject)
        && Objects.equals(type, that.type)
        && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(subject, type, value);
  }

  @Override
  public String toString() {
    return "SendGridContent{"
        + "subject='"
        + subject
        + '\''
        + ", type='"
        + type
        + '\''
        + ", value='"
        + value
        + '\''
        + '}';
  }
}
