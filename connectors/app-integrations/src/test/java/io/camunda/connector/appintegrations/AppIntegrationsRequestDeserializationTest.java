/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.appintegrations;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.appintegrations.model.AdditionalContent;
import io.camunda.connector.appintegrations.model.ChannelPlatform;
import io.camunda.connector.appintegrations.model.CreateChannelRequest;
import io.camunda.connector.appintegrations.model.Recipient;
import io.camunda.connector.appintegrations.model.SendMessageRequest;
import io.camunda.connector.appintegrations.model.SendMessageResult;
import io.camunda.connector.appintegrations.model.SlackTarget;
import io.camunda.connector.appintegrations.model.TeamsTarget;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import org.junit.jupiter.api.Test;

/**
 * Binds job variables the way the runtime does, from JSON shaped exactly as the element template's
 * {@code zeebe:input} names produce it. Every other test constructs the records directly, which
 * never exercises Jackson — and this model leans on it heavily: three sealed interfaces ({@code
 * CamundaExtra}, {@code TeamsExtra}, {@code SlackExtra}) that share the {@code None} and {@code
 * Form} leaves, each registering its own {@code @JsonSubTypes} subset, two levels of nesting, and
 * two zero-component records.
 *
 * <p>The JSON below is therefore the contract between the generated template and this model. If a
 * discriminator name or a nested field name changes on either side, this is what catches it.
 */
class AppIntegrationsRequestDeserializationTest {

  private static final ObjectMapper MAPPER = ConnectorsObjectMapperSupplier.getCopy();

  private static SendMessageRequest message(String json) throws Exception {
    return MAPPER.readValue(json, SendMessageRequest.class);
  }

  private static CreateChannelRequest channel(String json) throws Exception {
    return MAPPER.readValue(json, CreateChannelRequest.class);
  }

  @Test
  void camundaRecipientWithoutAdditionalContent() throws Exception {
    var request =
        message(
            """
            {"recipient":{"type":"camunda","email":"user@example.com",
                          "candidateUsers":["alice","bob"],"candidateGroups":["approvers"],
                          "camundaExtra":{"type":"none"}},
             "message":"Please review"}""");

    assertThat(request.message()).isEqualTo("Please review");
    assertThat(request.recipient()).isInstanceOf(Recipient.CamundaRecipient.class);
    var camunda = (Recipient.CamundaRecipient) request.recipient();
    assertThat(camunda.email()).isEqualTo("user@example.com");
    assertThat(camunda.candidateUsers()).containsExactly("alice", "bob");
    assertThat(request.recipient().additionalContent()).isInstanceOf(AdditionalContent.None.class);
  }

  @Test
  void camundaRecipientWithForm() throws Exception {
    var request =
        message(
            """
            {"recipient":{"type":"camunda","email":"user@example.com",
                          "camundaExtra":{"type":"form"}}}""");

    assertThat(request.message()).isNull();
    assertThat(request.recipient().additionalContent()).isInstanceOf(AdditionalContent.Form.class);
  }

  @Test
  void teamsChannelRecipientWithAdaptiveCard() throws Exception {
    var request =
        message(
            """
            {"recipient":{"type":"teams",
                          "teamsTarget":{"type":"channel","channelId":"19:abc@thread.tacv2"},
                          "teamsExtra":{"type":"adaptiveCard",
                                        "adaptiveCard":{"type":"AdaptiveCard","version":"1.5"}}},
             "message":"Deploy done"}""");

    assertThat(request.recipient()).isInstanceOf(Recipient.TeamsRecipient.class);
    var teams = (Recipient.TeamsRecipient) request.recipient();
    assertThat(((TeamsTarget.TeamsChannelTarget) teams.teamsTarget()).channelId())
        .isEqualTo("19:abc@thread.tacv2");
    var card = (AdditionalContent.AdaptiveCard) request.recipient().additionalContent();
    // Arrives as a parsed object, not a string: the engine evaluates the FEEL expression.
    assertThat(card.adaptiveCard().isObject()).isTrue();
    assertThat(card.adaptiveCard().get("type").asText()).isEqualTo("AdaptiveCard");
  }

  @Test
  void teamsUserAndConversationRecipients() throws Exception {
    var user =
        message(
            """
            {"recipient":{"type":"teams",
                          "teamsTarget":{"type":"user",
                                         "teamsUser":"6b1e0f9a-1f3d-4a2b-9d0e-4c1b2a3d4e5f"},
                          "teamsExtra":{"type":"none"}},
             "message":"Ping"}""");

    var userTarget =
        (TeamsTarget.TeamsUserTarget) ((Recipient.TeamsRecipient) user.recipient()).teamsTarget();
    assertThat(userTarget.teamsUser()).isEqualTo("6b1e0f9a-1f3d-4a2b-9d0e-4c1b2a3d4e5f");

    var conversation =
        message(
            """
            {"recipient":{"type":"teams",
                          "teamsTarget":{"type":"conversation",
                                         "conversationId":"19:abc@thread.tacv2;messageid=17123456789"},
                          "teamsExtra":{"type":"none"}},
             "message":"Following up"}""");

    var conversationTarget =
        (TeamsTarget.TeamsConversationTarget)
            ((Recipient.TeamsRecipient) conversation.recipient()).teamsTarget();
    assertThat(conversationTarget.conversationId())
        .isEqualTo("19:abc@thread.tacv2;messageid=17123456789");
  }

  @Test
  void slackChannelRecipientWithBlockKit() throws Exception {
    var request =
        message(
            """
            {"recipient":{"type":"slack",
                          "slackTarget":{"type":"channel","channelId":"C0123456789"},
                          "threadTs":"1712345678.000100",
                          "slackExtra":{"type":"blockKit","blocks":[{"type":"section"}]}},
             "message":"Deploy done"}""");

    var slack = (Recipient.SlackRecipient) request.recipient();
    assertThat(slack.slackTarget()).isInstanceOf(SlackTarget.SlackChannelTarget.class);
    assertThat(((SlackTarget.SlackChannelTarget) slack.slackTarget()).channelId())
        .isEqualTo("C0123456789");
    assertThat(slack.threadTs()).isEqualTo("1712345678.000100");
    var blockKit = (AdditionalContent.BlockKit) request.recipient().additionalContent();
    assertThat(blockKit.blocks().isArray()).isTrue();
  }

  @Test
  void slackUserRecipientWithForm() throws Exception {
    var request =
        message(
            """
            {"recipient":{"type":"slack",
                          "slackTarget":{"type":"user","user":"U0123456789"},
                          "slackExtra":{"type":"form"}}}""");

    var slack = (Recipient.SlackRecipient) request.recipient();
    assertThat(((SlackTarget.SlackUserTarget) slack.slackTarget()).user()).isEqualTo("U0123456789");
    assertThat(slack.threadTs()).isNull();
    assertThat(request.recipient().additionalContent()).isInstanceOf(AdditionalContent.Form.class);
  }

  @Test
  void createChannelForBothPlatforms() throws Exception {
    // displayName is bound as platform.displayName for both platforms, even though the template
    // gives
    // the two properties distinct IDs so each can carry its own length limit.
    var teams =
        channel(
            """
            {"platform":{"type":"teams","displayName":"Releases","teamId":"group-1",
                         "membershipType":"standard"},
             "description":"Automated releases"}""");
    var teamsPlatform = (ChannelPlatform.TeamsChannelPlatform) teams.platform();
    assertThat(teamsPlatform.displayName()).isEqualTo("Releases");
    assertThat(teamsPlatform.teamId()).isEqualTo("group-1");
    assertThat(teamsPlatform.membershipType()).isEqualTo("standard");
    assertThat(teams.description()).isEqualTo("Automated releases");

    var slack =
        channel(
            """
            {"platform":{"type":"slack","displayName":"releases","workspaceId":"T0123",
                         "isPrivate":true}}""");
    var slackPlatform = (ChannelPlatform.SlackChannelPlatform) slack.platform();
    assertThat(slackPlatform.displayName()).isEqualTo("releases");
    assertThat(slackPlatform.isPrivate()).isTrue();
  }

  @Test
  void sendMessageResultCarriesDeliveriesAndFailures() throws Exception {
    var result =
        MAPPER.readValue(
            """
            {"deliveries":[{"platform":"teams",
                            "conversation":"19:abc@thread.tacv2;messageid=17123456789",
                            "messageId":"17123456789",
                            "conversationKey":"teams:19:abc@thread.tacv2;messageid=17123456789"}],
             "failures":[{"platform":"slack","conversation":"C0123","reason":"not_in_channel"}]}""",
            SendMessageResult.class);

    assertThat(result.deliveries())
        .singleElement()
        .satisfies(
            delivery -> {
              assertThat(delivery.platform()).isEqualTo("teams");
              assertThat(delivery.conversation())
                  .isEqualTo("19:abc@thread.tacv2;messageid=17123456789");
              assertThat(delivery.messageId()).isEqualTo("17123456789");
              assertThat(delivery.conversationKey())
                  .isEqualTo("teams:19:abc@thread.tacv2;messageid=17123456789");
            });
    assertThat(result.failures())
        .singleElement()
        .satisfies(
            failure -> {
              assertThat(failure.conversation()).isEqualTo("C0123");
              assertThat(failure.reason()).isEqualTo("not_in_channel");
            });
  }

  @Test
  void unknownResponseFieldsAreIgnored() throws Exception {
    // FAIL_ON_UNKNOWN_PROPERTIES is disabled, so the backend can add response fields freely.
    var result =
        MAPPER.readValue(
            "{\"deliveries\":[],\"failures\":[],\"addedLater\":true}", SendMessageResult.class);

    assertThat(result.deliveries()).isEmpty();
    assertThat(result.failures()).isEmpty();
  }
}
