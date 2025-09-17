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
import io.camunda.connector.http.polling.model.PollingRequest;
import io.camunda.connector.http.polling.utils.PollingRequestMapper;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;

public class HttpRequestTask implements Runnable {

  private final HttpService httpService;
  private final ProcessInstanceContext processInstanceContext;

  private final InboundIntermediateConnectorContext context;
  private final PollingRequest config;
  private final PollingRequestMapper pollingRequestMapper =
      new PollingRequestMapper(ConnectorsObjectMapperSupplier.getCopy());

  public HttpRequestTask(
      final HttpService httpService,
      final ProcessInstanceContext processInstanceContext,
      final InboundIntermediateConnectorContext context,
      final PollingRequest config) {
    this.httpService = httpService;
    this.processInstanceContext = processInstanceContext;
    this.context = context;
    this.config = config;
  }

  @Override
  public void run() {
    try {
      try {
        HttpCommonRequest httpCommonRequest = pollingRequestMapper.toHttpCommonRequest(config);
        HttpCommonResult httpResponse = httpService.executeConnectorRequest(httpCommonRequest);
        processInstanceContext.correlate(httpResponse);
        this.context.log(
            activity ->
                activity
                    .withSeverity(Severity.INFO)
                    .withTag(config.getMethod().toString())
                    .withMessage("Polled url: " + config.getUrl()));
      } catch (Exception e) {
        this.context.log(
            activity ->
                activity
                    .withSeverity(Severity.ERROR)
                    .withTag(config.getMethod().toString())
                    .withMessage("Error executing http request: " + config.getUrl()));
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
