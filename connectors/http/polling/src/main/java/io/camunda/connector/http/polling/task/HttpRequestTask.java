/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.polling.task;

import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.InboundIntermediateConnectorContext;
import io.camunda.connector.api.inbound.ProcessInstanceContext;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.base.services.HttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpRequestTask implements Runnable {
  private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequestTask.class);

  private final HttpService httpService;
  private final ProcessInstanceContext processInstanceContext;

  private final InboundIntermediateConnectorContext context;

  public HttpRequestTask(
      final HttpService httpService,
      final ProcessInstanceContext processInstanceContext,
      final InboundIntermediateConnectorContext context) {
    this.httpService = httpService;
    this.processInstanceContext = processInstanceContext;
    this.context = context;
  }

  @Override
  public void run() {
    try {
      HttpCommonRequest httpRequest = processInstanceContext.bind(HttpCommonRequest.class);
      if (httpRequest != null) {
        try {
          HttpCommonResult httpResponse = httpService.executeConnectorRequest(httpRequest);
          processInstanceContext.correlate(httpResponse);
          this.context.log(
              Activity.level(Severity.INFO)
                  .tag(httpRequest.getMethod().toString())
                  .message("Polled url: " + httpRequest.getUrl()));
        } catch (Exception e) {
          LOGGER.warn(
              "Exception encountered while executing HTTP request for process instance {}: {}",
              processInstanceContext,
              e.getMessage());
        }

      } else {
        LOGGER.debug(
            "No HTTP request binding found for process instance {}", processInstanceContext);
      }
    } catch (Exception e) {
      LOGGER.warn(
          "Error occurred while binding properties for processInstanceKey {}: {}",
          processInstanceContext.getKey(),
          e.getMessage());
      context.log(
          Activity.level(Severity.ERROR)
              .tag("error")
              .message("Error occurred while binding properties"));
    }
  }
}
