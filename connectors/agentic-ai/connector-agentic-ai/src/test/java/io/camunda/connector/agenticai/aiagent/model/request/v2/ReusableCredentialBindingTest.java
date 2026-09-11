/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicAwsBedrockMantleBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicCustomBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiVertexAiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiCustomBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiFoundryBackend;
import io.camunda.connector.agenticai.aiagent.util.ConnectorUtils;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith({SpringExtension.class, SystemStubsExtension.class})
@Import(ValidationAutoConfiguration.class)
class ReusableCredentialBindingTest {

  private final ObjectMapper mapper = ConnectorsObjectMapperSupplier.getCopy();
  @Autowired private Validator validator;
  @SystemStub private EnvironmentVariables environment;

  @BeforeEach
  void setUp() {
    environment.set(ConnectorUtils.CONNECTOR_RUNTIME_SAAS_ENV_VARIABLE, null);
  }

  @Test
  void bindsAnthropicApiAndGatewayCredentials() throws Exception {
    AnthropicChatModelConfiguration api =
        read(
            """
            {"type":"anthropic","anthropic":{"backend":{"type":"anthropic-api","anthropic":{"credential":{"apiKey":"credential-key"}}},"model":{"model":"claude"}}}
            """);
    AnthropicChatModelConfiguration gateway =
        read(
            """
            {"type":"anthropic","anthropic":{"backend":{"type":"custom","custom":{"endpoint":"","authentication":{"type":"apiKey"},"credential":{"endpoint":"https://gateway.example","apiKey":"gateway-key"}}},"model":{"model":"claude"}}}
            """);

    assertThat(((AnthropicApiBackend) api.anthropic().backend()).anthropic().apiKey())
        .isEqualTo("credential-key");
    var custom = ((AnthropicCustomBackend) gateway.anthropic().backend()).custom();
    assertThat(custom.endpoint()).isEqualTo("https://gateway.example");
    assertThat(custom.authentication())
        .isEqualTo(new AnthropicCustomEndpointAuthentication.ApiKeyAuthentication("gateway-key"));
  }

  @Test
  void bindsBedrockApiKeyAndSharedAwsCredentials() throws Exception {
    BedrockConverseChatModelConfiguration apiKey =
        read(
            """
            {"type":"bedrock","bedrock":{"region":"","authentication":{"type":"bedrockApiKey","bedrockApiKeyCredential":{"apiKey":"bedrock-key","region":"eu-west-1"}},"model":{"model":"nova"}}}
            """);
    AnthropicChatModelConfiguration aws =
        read(
            """
            {"type":"anthropic","anthropic":{"backend":{"type":"aws-bedrock-mantle","awsBedrockMantle":{"region":"","authentication":{"type":"awsIam","awsCredential":{"authentication":{"type":"credentials","accessKey":"key","secretKey":"secret"},"region":"us-east-1"}}}},"model":{"model":"claude"}}}
            """);

    assertThat(apiKey.bedrock().region()).isEqualTo("eu-west-1");
    assertThat(apiKey.bedrock().authentication()).isInstanceOf(BedrockApiKeyAuthentication.class);
    var mantle = ((AnthropicAwsBedrockMantleBackend) aws.anthropic().backend()).awsBedrockMantle();
    assertThat(mantle.region()).isEqualTo("us-east-1");
    assertThat(mantle.authentication()).isInstanceOf(AwsIamAuthentication.class);
  }

  @Test
  void fallsBackToInlineAwsIamAuthenticationWhenTheChooserIsEmpty() throws Exception {
    BedrockConverseChatModelConfiguration configuration =
        read(
            """
            {"type":"bedrock","bedrock":{"region":"eu-central-1","authentication":{"type":"awsIam","inlineAuthentication":{"type":"credentials","accessKey":"key","secretKey":"secret"}},"model":{"model":"nova"}}}
            """);

    BedrockAuthentication authentication = configuration.bedrock().authentication();
    assertThat(authentication).isInstanceOf(AwsIamAuthentication.class);
    assertThat(authentication.awsCredentialConfiguration()).isNull();
    assertThat(authentication.effectiveIamAuthentication())
        .isEqualTo(new AwsAuthentication.AwsStaticCredentialsAuthentication("key", "secret"));
  }

  @Test
  void bindsOpenAiAndFoundryCredentials() throws Exception {
    OpenAiChatModelConfiguration openAi =
        read(
            """
            {"type":"openai","openai":{"api":{"type":"responses"},"backend":{"type":"openai-api","openai":{"credential":{"apiKey":"openai-key","organizationId":"org","projectId":"project"}}},"model":{"model":"gpt"}}}
            """);
    OpenAiChatModelConfiguration foundry =
        read(
            """
            {"type":"openai","openai":{"api":{"type":"responses"},"backend":{"type":"foundry","foundry":{"credential":{"endpoint":"https://resource.openai.azure.com","authentication":{"type":"apiKey","apiKey":"foundry-key"}}}},"model":{"model":"gpt"}}}
            """);

    var api = ((OpenAiApiBackend) openAi.openai().backend()).openai();
    assertThat(api.apiKey()).isEqualTo("openai-key");
    assertThat(api.organizationId()).isEqualTo("org");
    assertThat(api.projectId()).isEqualTo("project");
    var foundryConnection = ((OpenAiFoundryBackend) foundry.openai().backend()).foundry();
    assertThat(foundryConnection.endpoint()).isEqualTo("https://resource.openai.azure.com");
    assertThat(foundryConnection.authentication())
        .isEqualTo(
            new OpenAiChatModelConfiguration.OpenAiBackend.FoundryAuthentication
                .ApiKeyAuthentication("foundry-key"));
  }

  @Test
  void localGatewayEndpointOverridesTheCredential() throws Exception {
    OpenAiChatModelConfiguration configuration =
        read(
            """
            {"type":"openai","openai":{"api":{"type":"responses"},"backend":{"type":"custom","custom":{"endpoint":"https://request.example","credential":{"endpoint":"https://gateway.example","apiKey":"gateway-key"}}},"model":{"model":"gpt"}}}
            """);

    var gateway = ((OpenAiCustomBackend) configuration.openai().backend()).custom();
    assertThat(gateway.endpoint()).isEqualTo("https://request.example");
    assertThat(gateway.authentication())
        .isEqualTo(new OpenAiCustomEndpointAuthentication.ApiKeyAuthentication("gateway-key"));
  }

  @Test
  void bindsGeminiApiAndVertexCredentials() throws Exception {
    GeminiChatModelConfiguration gemini =
        read(
            """
            {"type":"google-gemini","googleGemini":{"backend":{"type":"google-gemini-api","googleGeminiApi":{"credential":{"apiKey":"gemini-key"}}},"model":{"model":"gemini"}}}
            """);
    GeminiChatModelConfiguration vertex =
        read(
            """
            {"type":"google-gemini","googleGemini":{"backend":{"type":"google-vertex-ai","googleVertexAi":{"credential":{"projectId":"project","region":"us-central1","authentication":{"type":"serviceAccountCredentials","jsonKey":"{}"}}}},"model":{"model":"gemini"}}}
            """);

    assertThat(((GeminiApiBackend) gemini.googleGemini().backend()).googleGeminiApi().apiKey())
        .isEqualTo("gemini-key");
    var vertexConnection =
        ((GeminiVertexAiBackend) vertex.googleGemini().backend()).googleVertexAi();
    assertThat(vertexConnection.projectId()).isEqualTo("project");
    assertThat(vertexConnection.region()).isEqualTo("us-central1");
  }

  @Test
  void validatesCredentialSourcesInsteadOfModelerHiddenInlineDefaults() throws Exception {
    List<ProviderConfiguration> configurations =
        List.of(
            read(
                """
                {"type":"openai","openai":{"api":{"type":"responses"},"backend":{"type":"foundry","foundry":{"endpoint":"","authentication":{"type":"apiKey"},"credential":{"endpoint":"https://resource.openai.azure.com","authentication":{"type":"apiKey","apiKey":"foundry-key"}}}},"model":{"model":"gpt"}}}
                """),
            read(
                """
                {"type":"openai","openai":{"api":{"type":"responses"},"backend":{"type":"custom","custom":{"endpoint":"","authentication":{"type":"apiKey"},"credential":{"endpoint":"https://gateway.example","apiKey":"gateway-key"}}},"model":{"model":"gpt"}}}
                """),
            read(
                """
                {"type":"anthropic","anthropic":{"backend":{"type":"custom","custom":{"endpoint":"","authentication":{"type":"apiKey"},"credential":{"endpoint":"https://gateway.example","apiKey":"gateway-key"}}},"model":{"model":"claude"}}}
                """),
            read(
                """
                {"type":"google-gemini","googleGemini":{"backend":{"type":"google-vertex-ai","googleVertexAi":{"projectId":"","region":"","authentication":{"type":"serviceAccountCredentials"},"credential":{"projectId":"project","region":"us-central1","authentication":{"type":"serviceAccountCredentials","jsonKey":"{}"}}}},"model":{"model":"gemini"}}}
                """));

    configurations.forEach(
        configuration -> assertThat(validator.validate(configuration)).isEmpty());
  }

  @Test
  void validatesCredentialsWithoutAnyInlineAuthenticationFields() throws Exception {
    List<ProviderConfiguration> configurations =
        List.of(
            read(
                """
                {"type":"anthropic","anthropic":{"backend":{"type":"anthropic-api","anthropic":{"credential":{"apiKey":"credential-key"}}},"model":{"model":"claude"}}}
                """),
            read(
                """
                {"type":"anthropic","anthropic":{"backend":{"type":"custom","custom":{"credential":{"endpoint":"https://gateway.example","apiKey":"gateway-key"}}},"model":{"model":"claude"}}}
                """),
            read(
                """
                {"type":"openai","openai":{"api":{"type":"responses"},"backend":{"type":"openai-api","openai":{"credential":{"apiKey":"credential-key"}}},"model":{"model":"gpt"}}}
                """),
            read(
                """
                {"type":"openai","openai":{"api":{"type":"responses"},"backend":{"type":"foundry","foundry":{"credential":{"endpoint":"https://resource.openai.azure.com","authentication":{"type":"apiKey","apiKey":"foundry-key"}}}},"model":{"model":"gpt"}}}
                """),
            read(
                """
                {"type":"openai","openai":{"api":{"type":"responses"},"backend":{"type":"custom","custom":{"credential":{"endpoint":"https://gateway.example","apiKey":"gateway-key"}}},"model":{"model":"gpt"}}}
                """),
            read(
                """
                {"type":"google-gemini","googleGemini":{"backend":{"type":"google-gemini-api","googleGeminiApi":{"credential":{"apiKey":"credential-key"}}},"model":{"model":"gemini"}}}
                """),
            read(
                """
                {"type":"google-gemini","googleGemini":{"backend":{"type":"google-vertex-ai","googleVertexAi":{"credential":{"projectId":"project","region":"us-central1","authentication":{"type":"serviceAccountCredentials","jsonKey":"{}"}}}},"model":{"model":"gemini"}}}
                """));

    configurations.forEach(
        configuration -> assertThat(validator.validate(configuration)).isEmpty());
  }

  @Test
  void rejectsInvalidInlineAuthenticationWithoutCredential() throws Exception {
    List<ProviderConfiguration> configurations =
        List.of(
            read(
                """
                {"type":"openai","openai":{"api":{"type":"responses"},"backend":{"type":"foundry","foundry":{"endpoint":"https://resource.openai.azure.com","authentication":{"type":"apiKey","apiKey":""}}},"model":{"model":"gpt"}}}
                """),
            read(
                """
                {"type":"openai","openai":{"api":{"type":"responses"},"backend":{"type":"custom","custom":{"endpoint":"https://gateway.example","authentication":{"type":"apiKey","apiKey":""}}},"model":{"model":"gpt"}}}
                """),
            read(
                """
                {"type":"anthropic","anthropic":{"backend":{"type":"custom","custom":{"endpoint":"https://gateway.example","authentication":{"type":"apiKey","apiKey":""}}},"model":{"model":"claude"}}}
                """),
            read(
                """
                {"type":"google-gemini","googleGemini":{"backend":{"type":"google-vertex-ai","googleVertexAi":{"projectId":"project","region":"us-central1","authentication":{"type":"serviceAccountCredentials","jsonKey":""}}},"model":{"model":"gemini"}}}
                """));

    configurations.forEach(
        configuration -> assertThat(validator.validate(configuration)).isNotEmpty());
  }

  @Test
  void rejectsInvalidSelectedCredentialInsteadOfFallingBackToInlineAuthentication()
      throws Exception {
    List<ProviderConfiguration> configurations =
        List.of(
            read(
                """
                {"type":"openai","openai":{"api":{"type":"responses"},"backend":{"type":"foundry","foundry":{"endpoint":"https://inline.openai.azure.com","authentication":{"type":"apiKey","apiKey":"inline-key"},"credential":{"endpoint":"","authentication":{"type":"apiKey","apiKey":""}}}},"model":{"model":"gpt"}}}
                """),
            read(
                """
                {"type":"openai","openai":{"api":{"type":"responses"},"backend":{"type":"custom","custom":{"endpoint":"https://inline.example","authentication":{"type":"apiKey","apiKey":"inline-key"},"credential":{"endpoint":"","apiKey":""}}},"model":{"model":"gpt"}}}
                """),
            read(
                """
                {"type":"anthropic","anthropic":{"backend":{"type":"custom","custom":{"endpoint":"https://inline.example","authentication":{"type":"apiKey","apiKey":"inline-key"},"credential":{"endpoint":"","apiKey":""}}},"model":{"model":"claude"}}}
                """),
            read(
                """
                {"type":"google-gemini","googleGemini":{"backend":{"type":"google-vertex-ai","googleVertexAi":{"projectId":"inline-project","region":"us-central1","authentication":{"type":"serviceAccountCredentials","jsonKey":"{}"},"credential":{"projectId":"","region":"","authentication":{"type":"serviceAccountCredentials","jsonKey":""}}}},"model":{"model":"gemini"}}}
                """));

    configurations.forEach(
        configuration -> assertThat(validator.validate(configuration)).isNotEmpty());
  }

  @Test
  void rejectsCredentialSuppliedApplicationDefaultCredentialsOnSaaS() throws Exception {
    environment.set(ConnectorUtils.CONNECTOR_RUNTIME_SAAS_ENV_VARIABLE, "true");
    ProviderConfiguration configuration =
        read(
            """
            {"type":"google-gemini","googleGemini":{"backend":{"type":"google-vertex-ai","googleVertexAi":{"projectId":"","region":"","authentication":{"type":"serviceAccountCredentials"},"credential":{"projectId":"project","region":"us-central1","authentication":{"type":"applicationDefaultCredentials"}}}},"model":{"model":"gemini"}}}
            """);

    assertThat(validator.validate(configuration))
        .extracting(ConstraintViolation::getMessage)
        .contains(
            "Application default credentials for Enterprise Agent Platform (Vertex AI) are not supported on SaaS");
  }

  @ParameterizedTest
  @EnumSource(BedrockProvider.class)
  void validatesSharedAwsCredentialWithHiddenInlineDefaults(BedrockProvider provider)
      throws Exception {
    var configuration =
        readBedrock(
            provider,
            """
            {
              "type": "awsIam",
              "awsCredential": {
                "authentication": {
                  "type": "credentials",
                  "accessKey": "chosen-access",
                  "secretKey": "chosen-secret"
                }
              },
              "inlineAuthentication": {"type": "credentials"}
            }
            """);

    assertThat(validator.validate(configuration)).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(BedrockProvider.class)
  void rejectsIncompleteInlineIamAuthenticationWithoutCredential(BedrockProvider provider)
      throws Exception {
    var configuration =
        readBedrock(
            provider,
            """
            {"type": "awsIam", "inlineAuthentication": {"type": "credentials"}}
            """);

    assertThat(validator.validate(configuration)).isNotEmpty();
  }

  @ParameterizedTest
  @EnumSource(BedrockProvider.class)
  void rejectsInvalidSharedAwsCredentialDespiteValidInlineAuthentication(BedrockProvider provider)
      throws Exception {
    var configuration =
        readBedrock(
            provider,
            """
            {
              "type": "awsIam",
              "awsCredential": {
                "authentication": {
                  "type": "credentials",
                  "accessKey": "chosen-access",
                  "secretKey": ""
                }
              },
              "inlineAuthentication": {
                "type": "credentials",
                "accessKey": "inline-access",
                "secretKey": "inline-secret"
              }
            }
            """);

    assertThat(validator.validate(configuration)).isNotEmpty();
  }

  @ParameterizedTest
  @EnumSource(BedrockProvider.class)
  void rejectsSharedAwsCredentialWithoutAuthentication(BedrockProvider provider) throws Exception {
    var configuration =
        readBedrock(
            provider,
            """
            {
              "type": "awsIam",
              "awsCredential": {"region": "eu-central-1"},
              "inlineAuthentication": {
                "type": "credentials",
                "accessKey": "inline-access",
                "secretKey": "inline-secret"
              }
            }
            """);

    assertThat(validator.validate(configuration)).isNotEmpty();
  }

  @ParameterizedTest
  @EnumSource(BedrockProvider.class)
  void rejectsInlineIamDefaultCredentialsOnSaaS(BedrockProvider provider) throws Exception {
    environment.set(ConnectorUtils.CONNECTOR_RUNTIME_SAAS_ENV_VARIABLE, "true");
    var configuration =
        readBedrock(
            provider,
            """
            {"type": "awsIam", "inlineAuthentication": {"type": "defaultCredentialsChain"}}
            """);

    assertThat(validator.validate(configuration))
        .extracting(ConstraintViolation::getMessage)
        .contains("AWS default credentials chain is not supported on SaaS");
  }

  @ParameterizedTest
  @EnumSource(BedrockProvider.class)
  void rejectsCredentialIamDefaultCredentialsOnSaaS(BedrockProvider provider) throws Exception {
    environment.set(ConnectorUtils.CONNECTOR_RUNTIME_SAAS_ENV_VARIABLE, "true");
    var configuration =
        readBedrock(
            provider,
            """
            {
              "type": "awsIam",
              "awsCredential": {"authentication": {"type": "defaultCredentialsChain"}},
              "inlineAuthentication": {
                "type": "credentials",
                "accessKey": "inline-access",
                "secretKey": "inline-secret"
              }
            }
            """);

    assertThat(validator.validate(configuration))
        .extracting(ConstraintViolation::getMessage)
        .contains("AWS default credentials chain is not supported on SaaS");
  }

  @ParameterizedTest
  @EnumSource(BedrockProvider.class)
  void ignoresInlineDefaultCredentialsWhenStaticCredentialSelected(BedrockProvider provider)
      throws Exception {
    environment.set(ConnectorUtils.CONNECTOR_RUNTIME_SAAS_ENV_VARIABLE, "true");
    var configuration =
        readBedrock(
            provider,
            """
            {
              "type": "awsIam",
              "awsCredential": {
                "authentication": {
                  "type": "credentials",
                  "accessKey": "chosen-access",
                  "secretKey": "chosen-secret"
                }
              },
              "inlineAuthentication": {"type": "defaultCredentialsChain"}
            }
            """);

    assertThat(validator.validate(configuration)).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(BedrockProvider.class)
  void acceptsInlineIamDefaultCredentialsOnSelfManaged(BedrockProvider provider) throws Exception {
    var configuration =
        readBedrock(
            provider,
            """
            {"type": "awsIam", "inlineAuthentication": {"type": "defaultCredentialsChain"}}
            """);

    assertThat(validator.validate(configuration)).isEmpty();
  }

  private ProviderConfiguration readBedrock(BedrockProvider provider, String authentication)
      throws Exception {
    String json =
        switch (provider) {
          case CONVERSE ->
              """
              {"type":"bedrock","bedrock":{"region":"eu-central-1","authentication":%s,"model":{"model":"nova"}}}
              """;
          case MANTLE ->
              """
              {"type":"anthropic","anthropic":{"backend":{"type":"aws-bedrock-mantle","awsBedrockMantle":{"region":"eu-central-1","authentication":%s}},"model":{"model":"claude"}}}
              """;
        };
    return mapper.readValue(json.formatted(authentication), ProviderConfiguration.class);
  }

  private enum BedrockProvider {
    CONVERSE,
    MANTLE
  }

  private <T extends ProviderConfiguration> T read(String json) throws Exception {
    return (T) mapper.readValue(json, ProviderConfiguration.class);
  }
}
