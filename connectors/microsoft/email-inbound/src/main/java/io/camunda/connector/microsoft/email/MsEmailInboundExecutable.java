/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email;

import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MsEmailInboundExecutable
    implements InboundConnectorExecutable<InboundConnectorContext> {
  private static final Logger LOGGER = LoggerFactory.getLogger(MsEmailInboundExecutable.class);

  private EmailPollingWorker worker;
  private InboundConnectorContext context;

  @Override
  public void activate(InboundConnectorContext context) throws Exception {
    worker = new EmailPollingWorker(context);
  }

  @Override
  public void deactivate() throws Exception {
    worker.shutdown();
    context.reportHealth(Health.down());
    Thread.sleep(Duration.ofMillis(800));
    if (!worker.isShutdown()) {
      LOGGER.debug("Executor service did not terminate gracefully, forcing shutdown");
      worker.forceShutdown();
    }
  }
}
