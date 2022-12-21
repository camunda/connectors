/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.chat;

import com.microsoft.graph.requests.ChatMessageCollectionPage;
import com.microsoft.graph.requests.ChatMessageCollectionRequest;
import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.model.OrderBy;
import io.camunda.connector.model.request.MSTeamsRequestData;
import java.util.Objects;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import okhttp3.Request;

public class ListMessagesInChat extends MSTeamsRequestData {

  @NotBlank @Secret private String chatId;
  @Secret private String filter;
  @NotNull private OrderBy orderBy;

  @Pattern(regexp = "^([1-9])|([1-4][0-9])|(50)$")
  @Secret
  private String top;

  @Override
  public Object invoke(final GraphServiceClient<Request> graphClient) {
    ChatMessageCollectionRequest request = graphClient.chats(chatId).messages().buildRequest();
    if (orderBy != OrderBy.withoutOrdering) {
      request.orderBy(orderBy.getValue());
    }
    if (filter != null) {
      request.filter(filter);
    }
    if (top != null) {
      request.top(Integer.parseInt(top));
    }
    ChatMessageCollectionPage chatMessageCollectionPage = request.get();
    System.out.println(chatMessageCollectionPage);
    return chatMessageCollectionPage;
  }

  public String getChatId() {
    return chatId;
  }

  public void setChatId(final String chatId) {
    this.chatId = chatId;
  }

  public String getFilter() {
    return filter;
  }

  public void setFilter(final String filter) {
    this.filter = filter;
  }

  public OrderBy getOrderBy() {
    return orderBy;
  }

  public void setOrderBy(final OrderBy orderBy) {
    this.orderBy = orderBy;
  }

  public String getTop() {
    return top;
  }

  public void setTop(final String top) {
    this.top = top;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ListMessagesInChat that = (ListMessagesInChat) o;
    return Objects.equals(chatId, that.chatId)
        && Objects.equals(filter, that.filter)
        && orderBy == that.orderBy
        && Objects.equals(top, that.top);
  }

  @Override
  public int hashCode() {
    return Objects.hash(chatId, filter, orderBy, top);
  }

  @Override
  public String toString() {
    return "ListMessagesInChat{"
        + "chatId='"
        + chatId
        + "'"
        + ", filter='"
        + filter
        + "'"
        + ", orderBy="
        + orderBy
        + ", top='"
        + top
        + "'"
        + "}";
  }
}
