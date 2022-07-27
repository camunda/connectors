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

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.users.UsersListRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.users.UsersListResponse;
import com.slack.api.model.User;
import io.camunda.connector.api.SecretStore;
import io.camunda.connector.api.Validator;
import java.io.IOException;
import java.util.Objects;
import org.apache.commons.text.StringEscapeUtils;

public class ChatPostMessageData implements SlackRequestData {

  private String channel;
  private String text;

  @Override
  public void validate(Validator validator) {
    validator.require(channel, "Slack - Chat Post Message - Channel");
    validator.require(text, "Slack - Chat Post Message - Text");
  }

  @Override
  public void replaceSecrets(SecretStore secretStore) {
    channel = secretStore.replaceSecret(channel);
    text = secretStore.replaceSecret(text);
  }

  @Override
  public SlackResponse invoke(MethodsClient methodsClient) throws SlackApiException, IOException {
    if (channel.startsWith("@")) {
      channel = getUserId(channel.substring(1), methodsClient);
    }

    ChatPostMessageRequest request =
        ChatPostMessageRequest.builder()
            .channel(channel)
            // Temporary workaround related to camunda/zeebe#9859
            .text(StringEscapeUtils.unescapeJson(text))
            .linkNames(true) // Enables message formatting
            .build();

    ChatPostMessageResponse chatPostMessageResponse = methodsClient.chatPostMessage(request);
    if (chatPostMessageResponse.isOk()) {
      return new ChatPostMessageSlackResponse(chatPostMessageResponse);
    } else {
      throw new RuntimeException(chatPostMessageResponse.getError());
    }
  }

  private String getUserId(String userName, MethodsClient methodsClient) {
    String userId = null;
    String nextCursor = null;

    do {
      UsersListRequest request = UsersListRequest.builder().limit(100).cursor(nextCursor).build();

      try {
        UsersListResponse response = methodsClient.usersList(request);
        if (response.isOk()) {
          userId =
              response.getMembers().stream()
                  .filter(user -> userName.equals(user.getRealName()))
                  .map(User::getId)
                  .findFirst()
                  .orElse(null);
          nextCursor = response.getResponseMetadata().getNextCursor();
        } else {
          throw new RuntimeException(
              "Unable to find user with name: " + userName + "; message: " + response.getError());
        }
      } catch (Exception e) {
        throw new RuntimeException("Unable to find user with name: " + userName, e);
      }

    } while (userId == null && nextCursor != null && !nextCursor.isBlank());

    if (userId == null) {
      throw new RuntimeException("Unable to find user with name: " + userName);
    }

    return userId;
  }

  public String getChannel() {
    return channel;
  }

  public void setChannel(String channel) {
    this.channel = channel;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ChatPostMessageData that = (ChatPostMessageData) o;
    return Objects.equals(channel, that.channel) && Objects.equals(text, that.text);
  }

  @Override
  public int hashCode() {
    return Objects.hash(channel, text);
  }

  @Override
  public String toString() {
    return "ChatPostMessageData{" + "channel='" + channel + '\'' + ", text='" + text + '\'' + '}';
  }
}
