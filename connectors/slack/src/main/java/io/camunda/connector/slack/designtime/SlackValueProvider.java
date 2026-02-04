/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.designtime;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.conversations.ConversationsListRequest;
import com.slack.api.methods.response.conversations.ConversationsListResponse;
import com.slack.api.model.Conversation;
import com.slack.api.model.ConversationType;
import io.camunda.connector.api.designtime.Option;
import io.camunda.connector.api.designtime.ValueProvider;
import io.camunda.connector.api.designtime.ValueProviderContext;
import io.camunda.connector.slack.outbound.SlackRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class SlackValueProvider implements ValueProvider {

  private final Slack slack;

  public SlackValueProvider() {
    this(Slack.getInstance());
  }

  public SlackValueProvider(final Slack slack) {
    this.slack = slack;
  }

  @Override
  public String getType() {
    return "io.camunda.connectors.Slack.v1";
  }

  @Override
  public String getName() {
    return "channels";
  }

  @Override
  public List<Option> getOptions(ValueProviderContext configuration) throws Exception {
    final var slackRequest = configuration.bindVariables(SlackRequest.class);
    return getAllChannels(slack.methods(slackRequest.token())).stream()
        .map(channel -> new Option(channel.getName(), channel.getId()))
        .toList();
  }

  public List<Conversation> getAllChannels(MethodsClient methodsClient) {
    String nextCursor = null;
    List<Conversation> channels = new ArrayList<>();
    do {
      List<ConversationType> allChannelType =
          Arrays.asList(
              ConversationType.PUBLIC_CHANNEL,
              ConversationType
                  .PRIVATE_CHANNEL); // we don't need IMs and MPIMs since they do not have a name
      ConversationsListRequest request =
          ConversationsListRequest.builder()
              .types(allChannelType)
              .limit(1000)
              .cursor(nextCursor)
              .build();
      try {
        ConversationsListResponse response = methodsClient.conversationsList(request);
        if (response.isOk()) {
          channels.addAll(response.getChannels());
          nextCursor = response.getResponseMetadata().getNextCursor();
        } else {
          throw new RuntimeException("Unable retrieve channels; message: " + response.getError());
        }
      } catch (SlackApiException slackApiException) {
        if (Objects.equals(slackApiException.getError().getError(), "ratelimited")) {
          throw new RuntimeException("Too many requests, rate limit reached");
        } else {
          throw new RuntimeException("Unable retrieve channels.", slackApiException);
        }
      } catch (Exception e) {
        throw new RuntimeException("Unexpected error. Unable retrieve channels.", e);
      }

    } while (nextCursor != null && !nextCursor.isBlank());

    return channels;
  }
}
