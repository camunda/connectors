package io.camunda.connector.slack;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.slack.api.Slack;
import io.camunda.connector.gcp.ConnectorBridgeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlackFunction implements HttpFunction {
  private static final Logger LOGGER = LoggerFactory.getLogger(SlackFunction.class);

  private static final Slack SLACK = Slack.getInstance();

  private static final SlackRequestDeserializer DESERIALIZER =
      new SlackRequestDeserializer("method")
          .registerType("chat.postMessage", ChatPostMessageData.class);
  private static final Gson GSON =
      new GsonBuilder().registerTypeAdapter(SlackRequest.class, DESERIALIZER).create();

  @Override
  public void service(HttpRequest httpRequest, HttpResponse httpResponse) throws Exception {
    final ConnectorBridgeResponse response = new ConnectorBridgeResponse();
    try {
      final SlackRequest<?> slackRequest =
          GSON.fromJson(httpRequest.getReader(), SlackRequest.class);
      final Validator validator = new Validator();
      slackRequest.validate(validator);
      validator.validate();

      LOGGER.info("Received request from cluster {}", slackRequest.getClusterId());

      final var secretStore = new SecretStore(GSON, slackRequest.getClusterId());
      slackRequest.replaceSecrets(secretStore);

      SlackResponse slackResponse = slackRequest.invoke(SLACK);
      httpResponse.setStatusCode(200);
      response.setResult(slackResponse);
    } catch (final Exception e) {
      LOGGER.error("Failed to execute request: " + e.getMessage(), e);
      httpResponse.setStatusCode(500);
      response.setError(e.getMessage());
    }

    httpResponse.setContentType("application/json");
    GSON.toJson(response, httpResponse.getWriter());
  }
}
