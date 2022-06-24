import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProviderBuilder;

import java.util.HashMap;

public class Main {
  private static final String zeebeAPI = "[Zeebe API]";
  private static final String clientId = "[Client ID]";
  private static final String clientSecret = "[Client Secret]";
  private static final String oAuthAPI = "[OAuth API] ";

  public static void main(String[] args) {
    var credentialsProvider =
        new OAuthCredentialsProviderBuilder()
            .authorizationServerUrl(oAuthAPI)
            .audience(zeebeAPI)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .build();

    try (var client = ZeebeClient.newClientBuilder()
        .gatewayAddress(zeebeAPI)
        .credentialsProvider(credentialsProvider)
        .build()) {

      var join = client.newTopologyRequest().send().join();

      client.newWorker().jobType("foo").handler(new ExampleJobHandler());
    }
  }

  private static class ExampleJobHandler implements JobHandler {

    @Override
    public void handle(final JobClient client, final ActivatedJob job) {
      var variables = new HashMap<>();

      variables.put("Foo", "BAR");

      client.newCompleteCommand(job).variables(variables).send().join();
    }
  }
}
