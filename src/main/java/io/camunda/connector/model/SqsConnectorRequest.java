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
package io.camunda.connector.model;

import io.camunda.connector.api.SecretStore;
import io.camunda.connector.api.Validator;
import java.util.Objects;

public class SqsConnectorRequest {

  private String queueUrl;

  private String queueRegion;

  private String accessKey;

  private String secretKey;

  private String messageBody;

  public void validateWith(final Validator validator) {
    validator.require(queueUrl, "sqs queue url");
    validator.require(queueRegion, "sqs queue region");
    validator.require(accessKey, "Access key");
    validator.require(secretKey, "Secret Key");
    validator.require(messageBody, "message body");
  }

  public void replaceSecrets(final SecretStore secretStore) {
    queueUrl = secretStore.replaceSecret(queueUrl);
    queueRegion = secretStore.replaceSecret(queueRegion);
    accessKey = secretStore.replaceSecret(accessKey);
    secretKey = secretStore.replaceSecret(secretKey);
  }

  public String getQueueUrl() {
    return queueUrl;
  }

  public void setQueueUrl(String queueUrl) {
    this.queueUrl = queueUrl;
  }

  public String getQueueRegion() {
    return queueRegion;
  }

  public void setQueueRegion(String queueRegion) {
    this.queueRegion = queueRegion;
  }

  public String getAccessKey() {
    return accessKey;
  }

  public void setAccessKey(String accessKey) {
    this.accessKey = accessKey;
  }

  public String getSecretKey() {
    return secretKey;
  }

  public void setSecretKey(String secretKey) {
    this.secretKey = secretKey;
  }

  public String getMessageBody() {
    return messageBody;
  }

  public void setMessageBody(String messageBody) {
    this.messageBody = messageBody;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SqsConnectorRequest that = (SqsConnectorRequest) o;
    return Objects.equals(queueUrl, that.queueUrl)
        && Objects.equals(queueRegion, that.queueRegion)
        && Objects.equals(accessKey, that.accessKey)
        && Objects.equals(secretKey, that.secretKey)
        && Objects.equals(messageBody, that.messageBody);
  }

  @Override
  public int hashCode() {
    return Objects.hash(queueUrl, queueRegion, accessKey, secretKey, messageBody);
  }

  @Override
  public String toString() {
    return "SqsConnectorRequest{"
        + "queueUrl='"
        + queueUrl
        + '\''
        + ", queueRegion='"
        + queueRegion
        + '\''
        + ", accessKey='"
        + accessKey
        + '\''
        + ", secretKey='"
        + secretKey
        + '\''
        + ", messageBody='"
        + messageBody
        + '\''
        + '}';
  }
}
