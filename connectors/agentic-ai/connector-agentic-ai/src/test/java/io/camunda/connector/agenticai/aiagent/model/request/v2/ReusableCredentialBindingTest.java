/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.request.v1.shared.TimeoutConfiguration;
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
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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
  void redactsReusableCredentialsAndNestedAuthentication() {
    String secret = "do-not-log-this-key";
    var bedrockCredential =
        new AgenticAiCredentialConfigurations.BedrockApiKeyCredential(secret, "eu-west-1");
    var inlineIam = new AwsIamInlineAuthentication.StaticCredentials(secret, secret);
    List<Object> credentials =
        List.of(
            new AgenticAiCredentialConfigurations.AnthropicApiCredential(secret),
            new AgenticAiCredentialConfigurations.OpenAiApiCredential(secret, null, null),
            new AgenticAiCredentialConfigurations.AiGatewayCredential(
                "https://gateway.example",
                new OpenAiCustomEndpointAuthentication.ApiKeyAuthentication(secret)),
            new AgenticAiCredentialConfigurations.AiGatewayCredential(
                "https://gateway.example",
                new OAuthClientCredentialsAuthentication(
                    "https://auth.example/token", secret, secret, null, null, null)),
            bedrockCredential,
            new AgenticAiCredentialConfigurations.GoogleGeminiApiCredential(secret),
            new AgenticAiCredentialConfigurations.MicrosoftFoundryCredential(
                "https://foundry.example",
                new OpenAiChatModelConfiguration.OpenAiBackend.FoundryAuthentication
                    .ApiKeyAuthentication(secret)),
            new AgenticAiCredentialConfigurations.VertexAiCredential(
                "project",
                "region",
                new GeminiChatModelConfiguration.GeminiBackend.GoogleVertexAiAuthentication
                    .ServiceAccountCredentialsAuthentication(secret)),
            new AwsAuthentication.BedrockApiKeyCredentialAuthentication(bedrockCredential),
            new BedrockApiKeyAuthentication(bedrockCredential, secret),
            new BedrockApiKeyAuthentication(null, secret),
            inlineIam,
            new AwsIamAuthentication(null, inlineIam));

    assertThat(credentials)
        .allSatisfy(
            credential ->
                assertThat(credential.toString()).doesNotContain(secret).contains("[REDACTED]"));
  }

  @Test
  void preservesInlineOAuthAuthentication() throws Exception {
    List<ProviderConfiguration> configurations =
        List.of(
            read(
                """
                {"type":"anthropic","anthropic":{"backend":{"type":"custom","custom":{"endpoint":"https://gateway.example","authentication":{"type":"oauth-client-credentials-flow","oauthTokenEndpoint":"https://auth.example/token","clientId":"client","clientSecret":"secret"}}},"model":{"model":"claude"}}}
                """),
            read(
                """
                {"type":"openai","openai":{"api":{"type":"responses"},"backend":{"type":"custom","custom":{"endpoint":"https://gateway.example","authentication":{"type":"oauth-client-credentials-flow","oauthTokenEndpoint":"https://auth.example/token","clientId":"client","clientSecret":"secret"}}},"model":{"model":"gpt"}}}
                """));

    configurations.forEach(
        configuration -> assertThat(validator.validate(configuration)).isEmpty());
    assertThat(
            ((AnthropicCustomBackend)
                    ((AnthropicChatModelConfiguration) configurations.getFirst())
                        .anthropic()
                        .backend())
                .custom()
                .authentication())
        .isInstanceOf(OAuthClientCredentialsAuthentication.class);
    assertThat(
            ((OpenAiCustomBackend)
                    ((OpenAiChatModelConfiguration) configurations.getLast()).openai().backend())
                .custom()
                .authentication())
        .isInstanceOf(OAuthClientCredentialsAuthentication.class);
  }

  @Test
  void credentialTakesPrecedenceOverIncompleteInlineOAuthAuthentication() throws Exception {
    List<ProviderConfiguration> configurations =
        List.of(
            read(
                """
                {"type":"anthropic","anthropic":{"backend":{"type":"custom","custom":{"authentication":{"type":"oauth-client-credentials-flow"},"credential":{"endpoint":"https://gateway.example","authentication":{"type":"apiKey","apiKey":"credential-key"}}}},"model":{"model":"claude"}}}
                """),
            read(
                """
                {"type":"openai","openai":{"api":{"type":"responses"},"backend":{"type":"custom","custom":{"authentication":{"type":"oauth-client-credentials-flow"},"credential":{"endpoint":"https://gateway.example","authentication":{"type":"apiKey","apiKey":"credential-key"}}}},"model":{"model":"gpt"}}}
                """));

    configurations.forEach(
        configuration -> assertThat(validator.validate(configuration)).isEmpty());
  }

  @ParameterizedTest
  @EnumSource(OAuthClientCredentialsAuthentication.ClientAuthenticationMethod.class)
  void bindsOAuthGatewayCredentialsWithoutInlineFields(
      OAuthClientCredentialsAuthentication.ClientAuthenticationMethod clientAuthentication)
      throws Exception {
    var credential =
        new AgenticAiCredentialConfigurations.AiGatewayCredential(
            "https://gateway.example",
            new OAuthClientCredentialsAuthentication(
                "https://auth.example/token",
                "client",
                "secret",
                "https://api.example",
                clientAuthentication,
                "models:read inference:write"));
    String credentialJson = mapper.writeValueAsString(credential);
    AnthropicChatModelConfiguration anthropic =
        read(
            """
            {"type":"anthropic","anthropic":{"backend":{"type":"custom","custom":{"credential":%s}},"model":{"model":"claude"}}}
            """
                .formatted(credentialJson));
    OpenAiChatModelConfiguration openAi =
        read(
            """
            {"type":"openai","openai":{"api":{"type":"responses"},"backend":{"type":"custom","custom":{"credential":%s}},"model":{"model":"gpt"}}}
            """
                .formatted(credentialJson));

    assertThat(validator.validate(anthropic)).isEmpty();
    assertThat(validator.validate(openAi)).isEmpty();
    var anthropicBackend = ((AnthropicCustomBackend) anthropic.anthropic().backend()).custom();
    var openAiBackend = ((OpenAiCustomBackend) openAi.openai().backend()).custom();
    assertThat(anthropicBackend.endpoint()).isEqualTo(credential.endpoint());
    assertThat(openAiBackend.endpoint()).isEqualTo(credential.endpoint());
    assertThat(anthropicBackend.authentication()).isEqualTo(credential.authentication());
    assertThat(openAiBackend.authentication()).isEqualTo(credential.authentication());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "null",
        "{\"type\":\"apiKey\",\"apiKey\":\"\"}",
        "{\"type\":\"oauth-client-credentials-flow\"}",
        "{\"type\":\"oauth-client-credentials-flow\",\"oauthTokenEndpoint\":\"invalid\",\"clientId\":\"client\",\"clientSecret\":\"secret\"}"
      })
  void rejectsInvalidGatewayCredentialAuthentication(String authentication) throws Exception {
    for (String provider : List.of("anthropic", "openai")) {
      ProviderConfiguration configuration =
          read(
              """
              {"type":"%1$s","%1$s":{"api":{"type":"responses"},"backend":{"type":"custom","custom":{"authentication":{"type":"apiKey","apiKey":"inline-key"},"credential":{"endpoint":"https://gateway.example","authentication":%2$s}}},"model":{"model":"test-model"}}}
              """
                  .formatted(provider, authentication));

      assertThat(validator.validate(configuration))
          .extracting(violation -> violation.getPropertyPath().toString())
          .anyMatch(path -> path.contains(".credential.authentication"));
    }
  }

  @Test
  void reportsMissingGatewayEndpointAsValidationFailure() throws Exception {
    OpenAiChatModelConfiguration configuration =
        read(
            """
            {"type":"openai","openai":{"api":{"type":"responses"},"backend":{"type":"custom","custom":{"authentication":{"type":"apiKey","apiKey":"key"}}},"model":{"model":"gpt"}}}
            """);

    assertThat(validator.validate(configuration))
        .extracting(ConstraintViolation::getMessage)
        .contains("An AI Gateway endpoint is required from the credential or element template");
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
            {"type":"anthropic","anthropic":{"backend":{"type":"custom","custom":{"endpoint":"","authentication":{"type":"apiKey"},"credential":{"endpoint":"https://gateway.example","authentication":{"type":"apiKey","apiKey":"gateway-key"}}}},"model":{"model":"claude"}}}
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

    AwsAuthentication authentication = configuration.bedrock().authentication();
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
            {"type":"openai","openai":{"api":{"type":"responses"},"backend":{"type":"custom","custom":{"endpoint":"https://request.example","credential":{"endpoint":"https://gateway.example","authentication":{"type":"apiKey","apiKey":"gateway-key"}}}},"model":{"model":"gpt"}}}
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
                {"type":"openai","openai":{"api":{"type":"responses"},"backend":{"type":"custom","custom":{"endpoint":"","authentication":{"type":"apiKey"},"credential":{"endpoint":"https://gateway.example","authentication":{"type":"apiKey","apiKey":"gateway-key"}}}},"model":{"model":"gpt"}}}
                """),
            read(
                """
                {"type":"anthropic","anthropic":{"backend":{"type":"custom","custom":{"endpoint":"","authentication":{"type":"apiKey"},"credential":{"endpoint":"https://gateway.example","authentication":{"type":"apiKey","apiKey":"gateway-key"}}}},"model":{"model":"claude"}}}
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
                {"type":"anthropic","anthropic":{"backend":{"type":"custom","custom":{"credential":{"endpoint":"https://gateway.example","authentication":{"type":"apiKey","apiKey":"gateway-key"}}}},"model":{"model":"claude"}}}
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
                {"type":"openai","openai":{"api":{"type":"responses"},"backend":{"type":"custom","custom":{"credential":{"endpoint":"https://gateway.example","authentication":{"type":"apiKey","apiKey":"gateway-key"}}}},"model":{"model":"gpt"}}}
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
                {"type":"openai","openai":{"api":{"type":"responses"},"backend":{"type":"custom","custom":{"endpoint":"https://inline.example","authentication":{"type":"apiKey","apiKey":"inline-key"},"credential":{"endpoint":"","authentication":{"type":"apiKey","apiKey":""}}}},"model":{"model":"gpt"}}}
                """),
            read(
                """
                {"type":"anthropic","anthropic":{"backend":{"type":"custom","custom":{"endpoint":"https://inline.example","authentication":{"type":"apiKey","apiKey":"inline-key"},"credential":{"endpoint":"","authentication":{"type":"apiKey","apiKey":""}}}},"model":{"model":"claude"}}}
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

  @ParameterizedTest
  @EnumSource(BedrockProvider.class)
  void reportsMissingBedrockAuthenticationAsValidationFailure(BedrockProvider provider)
      throws Exception {
    environment.set(ConnectorUtils.CONNECTOR_RUNTIME_SAAS_ENV_VARIABLE, "true");

    for (String authentication :
        List.of("null", "{\"type\":\"awsCredential\"}", "{\"type\":\"bedrockApiKeyCredential\"}")) {
      var configuration = readBedrock(provider, authentication, "");
      assertThat(validator.validate(configuration))
          .extracting(ConstraintViolation::getMessage)
          .contains("must not be null");
    }
  }

  @ParameterizedTest
  @EnumSource(BedrockProvider.class)
  void legacyBedrockApiKeyCredentialSuppliesRegion(BedrockProvider provider) throws Exception {
    var configuration =
        readBedrock(
            provider,
            """
            {"type":"bedrockApiKeyCredential","bedrockApiKeyCredential":{"apiKey":"key","region":"eu-west-1"}}
            """,
            "");

    assertThat(validator.validate(configuration)).isEmpty();
    String region =
        switch (provider) {
          case CONVERSE ->
              ((BedrockConverseChatModelConfiguration) configuration).bedrock().region();
          case MANTLE ->
              ((AnthropicAwsBedrockMantleBackend)
                      ((AnthropicChatModelConfiguration) configuration).anthropic().backend())
                  .awsBedrockMantle()
                  .region();
        };
    assertThat(region).isEqualTo("eu-west-1");
  }

  @Test
  void retainsBedrockAuthenticationConstructorAndAccessorSignatures() throws Exception {
    var converse = BedrockConverseChatModelConfiguration.BedrockConverseConnection.class;
    var mantle = AnthropicAwsBedrockMantleBackend.AwsBedrockMantleBackend.class;

    assertThat(converse.getMethod("authentication").getReturnType())
        .isEqualTo(AwsAuthentication.class);
    assertThat(mantle.getMethod("authentication").getReturnType())
        .isEqualTo(AwsAuthentication.class);
    assertThat(
            converse.getConstructor(
                String.class,
                String.class,
                AwsAuthentication.class,
                Map.class,
                Map.class,
                Map.class,
                TimeoutConfiguration.class,
                BedrockConverseChatModelConfiguration.BedrockConverseModel.class))
        .isNotNull();
    assertThat(
            mantle.getConstructor(
                String.class,
                String.class,
                AwsAuthentication.class,
                Map.class,
                Map.class,
                Map.class))
        .isNotNull();
  }

  private ProviderConfiguration readBedrock(BedrockProvider provider, String authentication)
      throws Exception {
    return readBedrock(provider, authentication, "eu-central-1");
  }

  private ProviderConfiguration readBedrock(
      BedrockProvider provider, String authentication, String region) throws Exception {
    String json =
        switch (provider) {
          case CONVERSE ->
              """
              {"type":"bedrock","bedrock":{"region":"%s","authentication":%s,"model":{"model":"nova"}}}
              """;
          case MANTLE ->
              """
              {"type":"anthropic","anthropic":{"backend":{"type":"aws-bedrock-mantle","awsBedrockMantle":{"region":"%s","authentication":%s}},"model":{"model":"claude"}}}
              """;
        };
    return mapper.readValue(json.formatted(region, authentication), ProviderConfiguration.class);
  }

  private enum BedrockProvider {
    CONVERSE,
    MANTLE
  }

  private <T extends ProviderConfiguration> T read(String json) throws Exception {
    return (T) mapper.readValue(json, ProviderConfiguration.class);
  }
}
