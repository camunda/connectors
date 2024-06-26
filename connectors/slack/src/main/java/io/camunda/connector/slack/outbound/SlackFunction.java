/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound;

import com.slack.api.Slack;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;

@ElementTemplate(
    id = "io.camunda.connectors.Slack.v1",
    name = "Slack Outbound Connector",
    description = "Create a channel or send a message to a channel or user",
    inputDataClass = SlackRequest.class,
    version = 4,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "method", label = "Method"),
      @ElementTemplate.PropertyGroup(id = "message", label = "Message"),
      @ElementTemplate.PropertyGroup(id = "channel", label = "Channel"),
      @ElementTemplate.PropertyGroup(id = "invite", label = "Invite"),
    },
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/slack/?slack=outbound",
    icon = "icon.svg")
@OutboundConnector(
    name = "Slack Outbound",
    inputVariables = {"token", "method", "data"},
    type = "io.camunda:slack:1")
public class SlackFunction implements OutboundConnectorFunction {

  private final Slack slack;

  public SlackFunction() {
    this(Slack.getInstance());
  }

  public SlackFunction(final Slack slack) {
    this.slack = slack;
  }

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {
    final var slackRequest = context.bindVariables(SlackRequest.class);
    return slackRequest.invoke(slack);
  }
}
