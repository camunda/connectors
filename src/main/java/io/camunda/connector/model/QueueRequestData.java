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

import com.amazonaws.services.sqs.model.MessageAttributeValue;
import io.camunda.connector.api.annotation.Secret;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.Objects;

public class QueueRequestData {

  @NotEmpty @Secret private String url;
  @NotEmpty @Secret private String region;

  @NotNull
  private Object messageBody; // we don't need to know the customer message as we will pass it as-is

  private Map<String, MessageAttributeValue> messageAttributes;

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public Object getMessageBody() {
    return messageBody;
  }

  public void setMessageBody(Object messageBody) {
    this.messageBody = messageBody;
  }

  public Map<String, MessageAttributeValue> getMessageAttributes() {
    return messageAttributes;
  }

  public void setMessageAttributes(Map<String, MessageAttributeValue> messageAttributes) {
    this.messageAttributes = messageAttributes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    QueueRequestData that = (QueueRequestData) o;
    return url.equals(that.url)
        && region.equals(that.region)
        && messageBody.equals(that.messageBody)
        && Objects.equals(messageAttributes, that.messageAttributes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(url, region, messageBody, messageAttributes);
  }

  @Override
  public String toString() {
    return "QueueRequestData{"
        + "url='"
        + url
        + '\''
        + ", region='"
        + region
        + '\''
        + ", messageBody="
        + messageBody
        + ", messageAttributes="
        + messageAttributes
        + '}';
  }
}
