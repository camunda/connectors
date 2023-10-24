/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.automationanywhere.operations;

import io.camunda.connector.automationanywhere.model.AutomationAnywhereHttpRequestBuilder;
import io.camunda.connector.http.base.model.HttpMethod;
import io.camunda.connector.http.base.services.HttpService;
import java.util.Map;
import java.util.function.Function;

public record GetWorkItemOperation(
    Object queueId, Object workItemId, String controlRoomUrl, Integer timeoutInSeconds)
    implements Operation {

  private static final String GET_WORK_ITEM_URL_PATTERN = "%s/v3/wlm/queues/%s/workitems/list";
  private static final String FILTER_KEY = "filter";
  private static final String FIELD_KEY = "field";
  private static final String ID_FIELD = "id";
  private static final String VALUE_KEY = "value";
  private static final String OPERATOR_KEY = "operator";
  private static final String EQUALS_OPERATOR = "eq";
  private static final Function<Object, Object> createFilter =
      itemId ->
          Map.of(
              FILTER_KEY,
              Map.of(FIELD_KEY, ID_FIELD, VALUE_KEY, itemId, OPERATOR_KEY, EQUALS_OPERATOR));

  @Override
  public Object execute(
      final HttpService httpService, final Map<String, String> authenticationHeader)
      throws Exception {

    final var request =
        new AutomationAnywhereHttpRequestBuilder()
            .withMethod(HttpMethod.POST)
            .withUrl(getFullGetItemUrl())
            .withBody(createFilter.apply(workItemId))
            .withHeaders(authenticationHeader)
            .withTimeoutInSeconds(timeoutInSeconds)
            .build();
    return httpService.executeConnectorRequest(request);
  }

  private String getFullGetItemUrl() {
    return String.format(GET_WORK_ITEM_URL_PATTERN, controlRoomUrl, queueId);
  }
}
