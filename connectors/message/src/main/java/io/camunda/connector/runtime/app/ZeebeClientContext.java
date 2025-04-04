/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.runtime.app;

import io.camunda.zeebe.client.ZeebeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class ZeebeClientContext implements ApplicationContextAware {

  private static ApplicationContext context;

  private static final Logger LOG = LoggerFactory.getLogger(ZeebeClientContext.class);

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    LOG.debug("Setting application context with Zeebe client");
    context = applicationContext;
  }

  public static ZeebeClient getZeebeClient() {
    LOG.debug("Access Zeebe Client");
    return context.getBean(ZeebeClient.class);
  }
}
