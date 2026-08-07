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
  void teamsRecipientWithAdaptiveCard() throws Exception {
    var request =
        message(
            """
            {"recipient":{"type":"teams","channelId":"19:abc@thread.tacv2",
                          "teamsExtra":{"type":"adaptiveCard",
                                        "adaptiveCard":{"type":"AdaptiveCard","version":"1.5"}}},
             "message":"Deploy done"}""");

    assertThat(request.recipient()).isInstanceOf(Recipient.TeamsRecipient.class);
    assertThat(((Recipient.TeamsRecipient) request.recipient()).channelId())
        .isEqualTo("19:abc@thread.tacv2");
    var card = (AdditionalContent.AdaptiveCard) request.recipient().additionalContent();
    // Arrives as a parsed object, not a string: the engine evaluates the FEEL expression.
    assertThat(card.adaptiveCard().isObject()).isTrue();
    assertThat(card.adaptiveCard().get("type").asText()).isEqualTo("AdaptiveCard");
  }

  @Test
  void slackChannelRecipientWithBlockKit() throws Exception {
    var request =
        message(
            """
            {"recipient":{"type":"slack",
                          "slackTarget":{"type":"channel","channelId":"C0123456789"},
                          "slackExtra":{"type":"blockKit","blocks":[{"type":"section"}]}},
             "message":"Deploy done"}""");

    var slack = (Recipient.SlackRecipient) request.recipient();
    assertThat(slack.slackTarget()).isInstanceOf(SlackTarget.SlackChannelTarget.class);
    assertThat(((SlackTarget.SlackChannelTarget) slack.slackTarget()).channelId())
        .isEqualTo("C0123456789");
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
                         "membershipType":"private"},
             "description":"Automated releases"}""");
    var teamsPlatform = (ChannelPlatform.TeamsChannelPlatform) teams.platform();
    assertThat(teamsPlatform.displayName()).isEqualTo("Releases");
    assertThat(teamsPlatform.teamId()).isEqualTo("group-1");
    assertThat(teamsPlatform.membershipType()).isEqualTo("private");
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
  void unknownResponseFieldsAreIgnored() throws Exception {
    // FAIL_ON_UNKNOWN_PROPERTIES is disabled, so the backend can add response fields freely.
    var result =
        MAPPER.readValue(
            "{\"conversation\":\"conv-1\",\"addedLater\":true}", SendMessageResult.class);

    assertThat(result.conversation()).isEqualTo("conv-1");
  }
}
