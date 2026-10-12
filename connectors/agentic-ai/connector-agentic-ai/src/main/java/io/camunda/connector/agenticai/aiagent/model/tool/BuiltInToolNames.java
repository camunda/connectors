/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

/**
 * The names of the tools the connector ships. They share one prefix by convention. The prefix is
 * not enforced: a tool of the process or a custom runtime tool may use any name, as long as it does
 * not equal the name of a runtime tool the agent offers (see {@code RuntimeToolRegistry}).
 */
public final class BuiltInToolNames {

  /** The prefix of the names of the tools the connector ships. */
  public static final String PREFIX = "c8__";

  private BuiltInToolNames() {}
}
