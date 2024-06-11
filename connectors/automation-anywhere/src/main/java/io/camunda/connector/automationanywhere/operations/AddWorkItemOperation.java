/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.automationanywhere.operations;

import io.camunda.connector.automationanywhere.model.AutomationAnywhereHttpRequestBuilder;
import io.camunda.connector.http.base.HttpService;
import io.camunda.connector.http.base.model.HttpMethod;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public record AddWorkItemOperation(
    Object queueId, Object itemData, String controlRoomUrl, Integer timeoutInSeconds)
    implements Operation {

  private static final String ADD_WORK_ITEM_URL_PATTERN = "%s/v3/wlm/queues/%s/workitems";
  private static final String WORK_ITEMS_KEY = "workItems";
  private static final String JSON_KEY = "json";

  private static final Function<Object, Map<String, List<Map<String, Object>>>> createWorkItemMap =
      itemData -> Map.of(WORK_ITEMS_KEY, List.of(Map.of(JSON_KEY, itemData)));

  @Override
  public Object execute(
      final HttpService httpService, final Map<String, String> authenticationHeader) {
    final var request =
        new AutomationAnywhereHttpRequestBuilder()
            .withMethod(HttpMethod.POST)
            .withUrl(getFullAddItemUrl())
            .withBody(createWorkItemMap.apply(itemData))
            .withHeaders(authenticationHeader)
            .withTimeoutInSeconds(timeoutInSeconds)
            .build();
    return httpService.executeConnectorRequest(request);
  }

  private String getFullAddItemUrl() {
    return String.format(ADD_WORK_ITEM_URL_PATTERN, controlRoomUrl, queueId);
  }
}
