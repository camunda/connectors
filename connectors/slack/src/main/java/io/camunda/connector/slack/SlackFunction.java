package io.camunda.connector.slack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.slack.api.Slack;
import io.camunda.connector.api.ConnectorContext;
import io.camunda.connector.api.ConnectorFunction;
import io.camunda.connector.api.Validator;

public class SlackFunction implements ConnectorFunction {
  private static final Slack SLACK = Slack.getInstance();

  private static final SlackRequestDeserializer DESERIALIZER =
      new SlackRequestDeserializer("method")
          .registerType("chat.postMessage", ChatPostMessageData.class);
  private static final Gson GSON =
      new GsonBuilder().registerTypeAdapter(SlackRequest.class, DESERIALIZER).create();

  @Override
  public Object execute(ConnectorContext context) throws Exception {

    final var variables = context.getVariables();

    final var slackRequest = GSON.fromJson(variables, SlackRequest.class);

    final var validator = new Validator();
    slackRequest.validate(validator);
    validator.validate();

    slackRequest.replaceSecrets(context.getSecretStore());

    return slackRequest.invoke(SLACK);
  }
}
