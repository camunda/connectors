/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack;

import com.google.gson.Gson;
import com.slack.api.Slack;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;

@OutboundConnector(
    name = "SLACK",
    inputVariables = {"token", "method", "data"},
    type = "io.camunda:slack:1")
public class SlackFunction implements OutboundConnectorFunction {

  private final Gson gson;
  private final Slack slack;

  public SlackFunction() {
    this(Slack.getInstance(), GsonSupplier.getGson());
  }

  public SlackFunction(final Slack slack, final Gson gson) {
    this.slack = slack;
    this.gson = gson;
  }

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {

    final var variables = context.getVariables();
    final var slackRequest = gson.fromJson(variables, SlackRequest.class);

    context.validate(slackRequest);
    context.replaceSecrets(slackRequest);

    return slackRequest.invoke(slack);
  }
}
