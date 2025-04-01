/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agents.core;

public class AgentsApplicationContextHolder {
  private static final AgentsApplicationContextHolder INSTANCE =
      new AgentsApplicationContextHolder();

  private AgentsApplicationContext context;

  private AgentsApplicationContextHolder() {}

  private AgentsApplicationContext getContext() {
    return this.context;
  }

  private void setContext(AgentsApplicationContext context) {
    this.context = context;
  }

  public static AgentsApplicationContext currentContext() {
    return INSTANCE.getContext();
  }

  public static void setCurrentContext(AgentsApplicationContext context) {
    INSTANCE.setContext(context);
  }
}
