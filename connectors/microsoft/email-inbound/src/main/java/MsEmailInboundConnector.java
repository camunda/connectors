/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.models.Subscription;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;

public class MsEmailInboundConnector implements WebhookConnectorExecutable {
  @Override
  public WebhookResult triggerWebhook(WebhookProcessingPayload payload) throws Exception {

    return null;
  }

  @Override
  public void activate(InboundConnectorContext context) throws Exception {
    final String clientId = "YOUR_CLIENT_ID";
    final String tenantId = "YOUR_TENANT_ID";
    final String clientSecret = "YOUR_CLIENT_SECRET";

    // The client credentials flow requires that you request the
    // /.default scope, and pre-configure your permissions on the
    // app registration in Azure. An administrator must grant consent
    // to those permissions beforehand.
    final String[] scopes = new String[] {"https://graph.microsoft.com/.default"};

    final ClientSecretCredential credential =
        new ClientSecretCredentialBuilder()
            .clientId(clientId)
            .tenantId(tenantId)
            .clientSecret(clientSecret)
            .build();

    if (null == scopes || null == credential) {
      throw new Exception("Unexpected error");
    }

    final GraphServiceClient graphClient = new GraphServiceClient(credential, scopes);
    final var subscription = new Subscription();
    subscription.setApplicationId("my-application-id");
    subscription.setChangeType("message");
    subscription.setLifecycleNotificationUrl("my-faked-url");
    final var liveSubscription = graphClient.subscriptions().post(subscription);
  }

  @Override
  public void deactivate() throws Exception {}
}
