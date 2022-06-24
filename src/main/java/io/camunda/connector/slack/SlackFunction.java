package io.camunda.connector.slack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.slack.api.Slack;
import io.camunda.connector.sdk.common.ConnectorContext;
import io.camunda.connector.sdk.common.ConnectorFunction;
import io.camunda.connector.sdk.common.ConnectorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlackFunction implements ConnectorFunction {
  private static final Logger LOGGER = LoggerFactory.getLogger(SlackFunction.class);

  private static final Slack SLACK = Slack.getInstance();

  private static final SlackRequestDeserializer DESERIALIZER =
      new SlackRequestDeserializer("method")
          .registerType("chat.postMessage", ChatPostMessageData.class);
  private static final Gson GSON =
      new GsonBuilder().registerTypeAdapter(SlackRequest.class, DESERIALIZER).create();

  @Override
  public Object service(ConnectorContext context) {

    final var slackRequest = context.getVariableAsType(SlackRequest.class);
    final var validator = new Validator();
    slackRequest.validate(validator);
    validator.validate();

    slackRequest.replaceSecrets(context.getSecretStore());

    try {
      return slackRequest.invoke(SLACK);
    } catch (final Exception e) {
      LOGGER.error("Failed to execute request: " + e.getMessage(), e);

      throw ConnectorResponse.failed(e);
    }
  }

}
