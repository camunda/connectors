/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.polling.task;

import io.camunda.connector.api.inbound.InboundIntermediateConnectorContext;
import io.camunda.connector.api.inbound.ProcessInstanceContext;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.http.base.HttpService;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.polling.model.PollingRuntimeProperties;
import io.camunda.connector.http.polling.utils.PollingRequestMapper;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;

public class HttpRequestTask implements Runnable {

  private final HttpService httpService;
  private final ProcessInstanceContext processInstanceContext;

  private final InboundIntermediateConnectorContext context;
  private final PollingRequestMapper pollingRequestMapper =
      new PollingRequestMapper(ConnectorsObjectMapperSupplier.getCopy());

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
      PollingRuntimeProperties pollingRuntimeProperties =
          processInstanceContext.bind(PollingRuntimeProperties.class);
      try {
        HttpCommonRequest httpCommonRequest =
            pollingRequestMapper.toHttpCommonRequest(pollingRuntimeProperties);
        HttpCommonResult httpResponse = httpService.executeConnectorRequest(httpCommonRequest);
        processInstanceContext.correlate(httpResponse);
        this.context.log(
            activity ->
                activity
                    .withSeverity(Severity.INFO)
                    .withTag(pollingRuntimeProperties.getMethod().toString())
                    .withMessage("Polled url: " + pollingRuntimeProperties.getUrl()));
      } catch (Exception e) {
        this.context.log(
            activity ->
                activity
                    .withSeverity(Severity.ERROR)
                    .withTag(pollingRuntimeProperties.getMethod().toString())
                    .withMessage(
                        "Error executing http request: " + pollingRuntimeProperties.getUrl()));
      }
    } catch (Exception e) {
      this.context.log(
          activity ->
              activity
                  .withSeverity(Severity.ERROR)
                  .withTag("http-request")
                  .withMessage("Error binding properties for HTTP request"));
    }
  }
}
