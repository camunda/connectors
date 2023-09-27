/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.polling;

import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.config.ConnectorConfigurationUtil;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.inbound.InboundIntermediateConnectorContext;
import io.camunda.connector.feel.ConnectorsObjectMapperSupplier;
import io.camunda.connector.http.base.components.HttpTransportComponentSupplier;
import io.camunda.connector.http.base.constants.Constants;
import io.camunda.connector.http.base.services.HttpService;
import io.camunda.connector.http.polling.service.SharedExecutorService;
import io.camunda.connector.http.polling.task.PollingOperateTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InboundConnector(name = "HTTP_POLLING", type = "io.camunda:http-polling:1")
public class HttpPollingConnector
    implements InboundConnectorExecutable<InboundIntermediateConnectorContext> {
  private static final Logger LOGGER = LoggerFactory.getLogger(HttpPollingConnector.class);

  private final HttpService httpService;
  private final SharedExecutorService executorService;

  private PollingOperateTask pollingOperateTask;

  public HttpPollingConnector() {
    this(ConnectorConfigurationUtil.getProperty(Constants.PROXY_FUNCTION_URL_ENV_NAME));
  }

  public HttpPollingConnector(final String proxyFunctionUrl) {
    this(
        new HttpService(
            ConnectorsObjectMapperSupplier.getCopy(),
            HttpTransportComponentSupplier.httpRequestFactoryInstance(),
            proxyFunctionUrl),
        SharedExecutorService.getInstance());
  }

  public HttpPollingConnector(
      final HttpService httpService, final SharedExecutorService executorService) {
    this.httpService = httpService;
    this.executorService = executorService;
  }

  @Override
  public void activate(final InboundIntermediateConnectorContext context) {
    pollingOperateTask = new PollingOperateTask(context, httpService, executorService);
    pollingOperateTask.start();
  }

  @Override
  public void deactivate() {
    LOGGER.debug("Deactivating the HttpPolling connector");
    pollingOperateTask.stop();
  }
}
