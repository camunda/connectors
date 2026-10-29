/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import java.util.List;

/** The names of the features an agent instance can be created with, see {@link AgentContext}. */
public final class AgentFeatures {

  /**
   * Offers the runtime tools (see {@code RuntimeTool}) and reserves their names against the tools
   * of the process. An instance created without it offers no runtime tool and checks no tool name
   * against runtime tool names.
   */
  public static final String RUNTIME_TOOLS = "runtimeTools";

  /**
   * Keeps every document of the conversation in its messages as a document reference and shows the
   * model a tag for each of them. An instance created without it keeps the document handling it was
   * created with.
   */
  public static final String DOCUMENTS = "documents";

  private AgentFeatures() {}

  /** The features of an agent instance that is created now. */
  public static List<String> ofNewInstance() {
    return List.of(RUNTIME_TOOLS);
  }
}
