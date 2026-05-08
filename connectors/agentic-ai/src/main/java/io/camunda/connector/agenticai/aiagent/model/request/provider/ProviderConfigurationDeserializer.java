/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.provider;

import static io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration.ANTHROPIC_ID;
import static io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration.BEDROCK_ID;
import static io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleGenAiProviderConfiguration.GOOGLE_GENAI_ID;
import static io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OPENAI_ID;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;

/**
 * Migration deserializer for {@link ProviderConfiguration}.
 *
 * <p>Rewrites legacy serialized provider configuration shapes to the current canonical form before
 * standard Jackson polymorphic dispatch takes over. This handles backward-compatibility for process
 * instances that were saved with older configuration shapes, including:
 *
 * <ul>
 *   <li>Bedrock configurations with Anthropic model IDs → AnthropicProviderConfiguration with
 *       backend=bedrock
 *   <li>googleVertexAi discriminator → googleGenAi with backend=vertex
 *   <li>anthropic without backend field → inject backend=direct
 *   <li>anthropic without auth type discriminator → inject type=apiKey
 *   <li>openai without backend/apiFamily → inject backend=openai, apiFamily=completions
 *   <li>azureOpenAi → openai/foundry with field mapping
 *   <li>openaiCompatible → openai/custom with field mapping
 * </ul>
 *
 * <p>Dispatch to concrete subtypes via {@code mapper.treeToValue(migrated, SubType.class)} does NOT
 * re-enter this deserializer because Jackson's BeanDeserializer for the resolved concrete subtype
 * takes over directly.
 *
 * <p>This deserializer is permanent infrastructure — kept indefinitely so that stale process
 * variables remain readable.
 */
public class ProviderConfigurationDeserializer extends StdDeserializer<ProviderConfiguration> {

  private static final String GOOGLE_VERTEX_AI_LEGACY_ID = "googleVertexAi";
  private static final String AZURE_OPENAI_LEGACY_ID = "azureOpenAi";
  private static final String OPENAI_COMPATIBLE_LEGACY_ID = "openaiCompatible";

  private static final String FIELD_TYPE = "type";
  private static final String FIELD_BACKEND = "backend";
  private static final String FIELD_API_FAMILY = "apiFamily";
  private static final String FIELD_ENDPOINT = "endpoint";
  private static final String FIELD_AUTHENTICATION = "authentication";
  private static final String FIELD_MODEL = "model";
  private static final String FIELD_TIMEOUTS = "timeouts";
  private static final String FIELD_HEADERS = "headers";
  private static final String FIELD_QUERY_PARAMETERS = "queryParameters";
  private static final String FIELD_API_KEY = "apiKey";
  private static final String FIELD_DEPLOYMENT_NAME = "deploymentName";

  public ProviderConfigurationDeserializer() {
    super(ProviderConfiguration.class);
  }

  @Override
  public ProviderConfiguration deserialize(JsonParser jp, DeserializationContext ctxt)
      throws IOException {
    ObjectMapper mapper = (ObjectMapper) jp.getCodec();
    ObjectNode node = (ObjectNode) mapper.readTree(jp);

    // The `type` property may be absent from the node when Jackson's AsPropertyTypeDeserializer
    // has already consumed it before calling this deserializer. In that case, infer the original
    // type discriminator from the node's top-level keys (e.g. "azureOpenAi", "anthropic").
    String type = node.has(FIELD_TYPE) ? node.path(FIELD_TYPE).asText(null) : inferType(node);

    ObjectNode migrated = migrate(node, type, mapper);
    String newType =
        migrated.has(FIELD_TYPE) ? migrated.path(FIELD_TYPE).asText(null) : inferType(migrated);

    // Dispatch by extracting the inner connection sub-object and deserializing it directly as the
    // concrete connection type (e.g. AnthropicConnection, not AnthropicProviderConfiguration).
    // This avoids routing through ObjectMapper.treeToValue(ProviderConfiguration subtype), which
    // would re-trigger the interface-level @JsonTypeInfo + @JsonDeserialize and cause recursion.
    JsonNode rawInner = newType != null ? migrated.path(newType) : mapper.createObjectNode();
    if (!rawInner.isObject()) {
      throw JsonMappingException.from(
          jp, "Provider configuration is missing connection object for type: " + newType);
    }
    ObjectNode innerNode = (ObjectNode) rawInner;

    if (ANTHROPIC_ID.equals(newType)) {
      return new AnthropicProviderConfiguration(
          mapper.treeToValue(innerNode, AnthropicProviderConfiguration.AnthropicConnection.class));
    } else if (BEDROCK_ID.equals(newType)) {
      return new BedrockProviderConfiguration(
          mapper.treeToValue(innerNode, BedrockProviderConfiguration.BedrockConnection.class));
    } else if (GOOGLE_GENAI_ID.equals(newType)) {
      return new GoogleGenAiProviderConfiguration(
          mapper.treeToValue(
              innerNode, GoogleGenAiProviderConfiguration.GoogleGenAiConnection.class));
    } else if (OPENAI_ID.equals(newType)) {
      return new OpenAiProviderConfiguration(
          mapper.treeToValue(innerNode, OpenAiProviderConfiguration.OpenAiConnection.class));
    } else {
      throw new JsonMappingException(jp, "Unknown provider type: " + newType);
    }
  }

  /**
   * Infers the provider type discriminator from the node's top-level keys when the {@code type}
   * property has been consumed by Jackson's polymorphic type resolver before reaching this
   * deserializer.
   */
  private String inferType(ObjectNode node) {
    if (node.has(ANTHROPIC_ID)) return ANTHROPIC_ID;
    if (node.has(BEDROCK_ID)) return BEDROCK_ID;
    if (node.has(GOOGLE_GENAI_ID)) return GOOGLE_GENAI_ID;
    if (node.has(OPENAI_ID)) return OPENAI_ID;
    if (node.has(GOOGLE_VERTEX_AI_LEGACY_ID)) return GOOGLE_VERTEX_AI_LEGACY_ID;
    if (node.has(AZURE_OPENAI_LEGACY_ID)) return AZURE_OPENAI_LEGACY_ID;
    if (node.has(OPENAI_COMPATIBLE_LEGACY_ID)) return OPENAI_COMPATIBLE_LEGACY_ID;
    return null;
  }

  /**
   * Applies all migration rules in order and returns the (possibly rewritten) node.
   *
   * <p>Rules are applied in order; some rules may change the type discriminator, which causes
   * subsequent rules to operate on the updated type.
   */
  private ObjectNode migrate(ObjectNode node, String type, ObjectMapper mapper) {
    // Rule 1 & 2: bedrock with Anthropic model → rewrite to anthropic/bedrock; non-Anthropic
    // model → passthrough
    if (BEDROCK_ID.equals(type)) {
      return migrateBedrockNode(node, mapper);
    }

    // Rule 3: googleVertexAi → googleGenAi/vertex
    if (GOOGLE_VERTEX_AI_LEGACY_ID.equals(type)) {
      return migrateGoogleVertexAiNode(node, mapper);
    }

    // Rule 7: azureOpenAi → openai/foundry
    if (AZURE_OPENAI_LEGACY_ID.equals(type)) {
      return migrateAzureOpenAiNode(node, mapper);
    }

    // Rule 8: openaiCompatible → openai/custom
    if (OPENAI_COMPATIBLE_LEGACY_ID.equals(type)) {
      return migrateOpenAiCompatibleNode(node, mapper);
    }

    // Rules 4 & 5: anthropic without backend or without auth type discriminator
    if (ANTHROPIC_ID.equals(type)) {
      return migrateAnthropicNode(node, mapper);
    }

    // Rule 6: openai without backend/apiFamily
    if (OPENAI_ID.equals(type)) {
      return migrateOpenAiNode(node, mapper);
    }

    return node;
  }

  /**
   * Rule 1: bedrock with an {@code anthropic.*} model ID → rewrite to anthropic/bedrock. Rule 2:
   * bedrock with non-Anthropic model → passthrough.
   */
  private ObjectNode migrateBedrockNode(ObjectNode node, ObjectMapper mapper) {
    ObjectNode bedrockObj = (ObjectNode) node.path(BEDROCK_ID);
    if (bedrockObj == null || bedrockObj.isMissingNode()) {
      return node;
    }

    ObjectNode modelObj = (ObjectNode) bedrockObj.path(FIELD_MODEL);
    String modelId =
        (modelObj != null && !modelObj.isMissingNode())
            ? modelObj.path(FIELD_MODEL).asText(null)
            : null;

    if (modelId == null || !modelId.startsWith("anthropic.")) {
      // Rule 2: non-Anthropic model — passthrough
      return node;
    }

    // Rule 1: rewrite to anthropic/bedrock
    ObjectNode anthropicObj = mapper.createObjectNode();
    anthropicObj.put(FIELD_BACKEND, "bedrock");

    // Authentication from Bedrock (AWS credentials types) is incompatible with
    // AnthropicAuthentication (apiKey / clientCredentials). Do NOT copy it; the
    // migrated configuration will need to supply Anthropic auth separately.

    // Copy timeouts if present
    if (bedrockObj.has(FIELD_TIMEOUTS)) {
      anthropicObj.set(FIELD_TIMEOUTS, bedrockObj.get(FIELD_TIMEOUTS));
    }

    // Copy endpoint if present
    if (bedrockObj.has(FIELD_ENDPOINT)) {
      anthropicObj.set(FIELD_ENDPOINT, bedrockObj.get(FIELD_ENDPOINT));
    }

    // Copy model (BedrockModel has same field name as AnthropicModel)
    if (bedrockObj.has(FIELD_MODEL)) {
      anthropicObj.set(FIELD_MODEL, bedrockObj.get(FIELD_MODEL));
    }

    ObjectNode result = mapper.createObjectNode();
    result.put(FIELD_TYPE, ANTHROPIC_ID);
    result.set(ANTHROPIC_ID, anthropicObj);
    return result;
  }

  /**
   * Rule 3: googleVertexAi → googleGenAi with backend=vertex.
   *
   * <p>The old shape uses {@code googleVertexAi} as both the discriminator and the nested object
   * key. The new shape uses {@code googleGenAi} as both.
   */
  private ObjectNode migrateGoogleVertexAiNode(ObjectNode node, ObjectMapper mapper) {
    ObjectNode googleObj = mapper.createObjectNode();
    googleObj.put(FIELD_BACKEND, "vertex");

    ObjectNode legacyObj = (ObjectNode) node.path(GOOGLE_VERTEX_AI_LEGACY_ID);
    if (legacyObj != null && !legacyObj.isMissingNode()) {
      legacyObj.fields().forEachRemaining(entry -> googleObj.set(entry.getKey(), entry.getValue()));
    }

    ObjectNode result = mapper.createObjectNode();
    result.put(FIELD_TYPE, GOOGLE_GENAI_ID);
    result.set(GOOGLE_GENAI_ID, googleObj);
    return result;
  }

  /**
   * Rules 4 & 5: inject missing backend and/or auth type discriminator for anthropic.
   *
   * <p>Rule 4: {@code anthropic} without backend → inject {@code backend: direct}. Rule 5: {@code
   * anthropic.authentication} without {@code type} → inject {@code type: apiKey}.
   */
  private ObjectNode migrateAnthropicNode(ObjectNode node, ObjectMapper mapper) {
    ObjectNode anthropicObj = (ObjectNode) node.path(ANTHROPIC_ID);
    if (anthropicObj == null || anthropicObj.isMissingNode()) {
      return node;
    }

    // Rule 4: inject backend=direct if missing
    if (!anthropicObj.has(FIELD_BACKEND)) {
      anthropicObj.put(FIELD_BACKEND, "direct");
    }

    // Rule 5: inject authentication.type=apiKey if auth present but missing type discriminator
    if (anthropicObj.has(FIELD_AUTHENTICATION)) {
      var authNode = anthropicObj.get(FIELD_AUTHENTICATION);
      if (authNode != null && authNode.isObject()) {
        ObjectNode authObj = (ObjectNode) authNode;
        if (!authObj.has(FIELD_TYPE)) {
          authObj.put(FIELD_TYPE, "apiKey");
        }
      }
    }

    return node;
  }

  /** Rule 6: openai without backend → inject backend=openai, apiFamily=completions. */
  private ObjectNode migrateOpenAiNode(ObjectNode node, ObjectMapper mapper) {
    ObjectNode openaiObj = (ObjectNode) node.path(OPENAI_ID);
    if (openaiObj == null || openaiObj.isMissingNode()) {
      return node;
    }

    if (!openaiObj.has(FIELD_BACKEND)) {
      openaiObj.put(FIELD_BACKEND, "openai");
    }

    if (!openaiObj.has(FIELD_API_FAMILY)) {
      openaiObj.put(FIELD_API_FAMILY, "completions");
    }

    return node;
  }

  /**
   * Rule 7: azureOpenAi → openai/foundry with field mapping.
   *
   * <p>Old structure:
   *
   * <pre>
   * {
   *   "type": "azureOpenAi",
   *   "azureOpenAi": {
   *     "endpoint": "...",
   *     "authentication": { "type": "apiKey", "apiKey": "..." },
   *     "model": { "deploymentName": "...", "parameters": {...} },
   *     "timeouts": {...}
   *   }
   * }
   * </pre>
   *
   * <p>New structure:
   *
   * <pre>
   * {
   *   "type": "openai",
   *   "openai": {
   *     "backend": "foundry",
   *     "apiFamily": "completions",
   *     "endpoint": "...",
   *     "authentication": { "type": "apiKey", "apiKey": "..." },
   *     "model": { "model": "...", "parameters": {...} },
   *     "timeouts": {...}
   *   }
   * }
   * </pre>
   *
   * <p>Note: {@code deploymentName} in the old model maps to {@code model} in the new model.
   */
  private ObjectNode migrateAzureOpenAiNode(ObjectNode node, ObjectMapper mapper) {
    ObjectNode azureObj = (ObjectNode) node.path(AZURE_OPENAI_LEGACY_ID);

    ObjectNode openaiObj = mapper.createObjectNode();
    openaiObj.put(FIELD_BACKEND, "foundry");
    openaiObj.put(FIELD_API_FAMILY, "completions");

    if (azureObj != null && !azureObj.isMissingNode()) {
      // Copy endpoint
      if (azureObj.has(FIELD_ENDPOINT)) {
        openaiObj.set(FIELD_ENDPOINT, azureObj.get(FIELD_ENDPOINT));
      }

      // Copy authentication sub-tree directly (same structure)
      if (azureObj.has(FIELD_AUTHENTICATION)) {
        openaiObj.set(FIELD_AUTHENTICATION, azureObj.get(FIELD_AUTHENTICATION));
      }

      // Map model: deploymentName → model
      if (azureObj.has(FIELD_MODEL)) {
        ObjectNode oldModel = (ObjectNode) azureObj.get(FIELD_MODEL);
        ObjectNode newModel = mapper.createObjectNode();
        if (oldModel.has(FIELD_DEPLOYMENT_NAME)) {
          newModel.set(FIELD_MODEL, oldModel.get(FIELD_DEPLOYMENT_NAME));
        }
        if (oldModel.has("parameters")) {
          newModel.set("parameters", oldModel.get("parameters"));
        }
        openaiObj.set(FIELD_MODEL, newModel);
      }

      // Copy timeouts
      if (azureObj.has(FIELD_TIMEOUTS)) {
        openaiObj.set(FIELD_TIMEOUTS, azureObj.get(FIELD_TIMEOUTS));
      }
    }

    ObjectNode result = mapper.createObjectNode();
    result.put(FIELD_TYPE, OPENAI_ID);
    result.set(OPENAI_ID, openaiObj);
    return result;
  }

  /**
   * Rule 8: openaiCompatible → openai/custom with field mapping and auth discriminator injection.
   *
   * <p>Old structure:
   *
   * <pre>
   * {
   *   "type": "openaiCompatible",
   *   "openaiCompatible": {
   *     "endpoint": "...",
   *     "authentication": { "apiKey": "..." },
   *     "headers": {...},
   *     "queryParameters": {...},
   *     "timeouts": {...},
   *     "model": { "model": "...", "parameters": {...} }
   *   }
   * }
   * </pre>
   *
   * <p>New structure:
   *
   * <pre>
   * {
   *   "type": "openai",
   *   "openai": {
   *     "backend": "custom",
   *     "apiFamily": "completions",
   *     "endpoint": "...",
   *     "authentication": { "type": "apiKey", "apiKey": "..." },
   *     "headers": {...},
   *     "queryParameters": {...},
   *     "timeouts": {...},
   *     "model": { "model": "...", "parameters": {...} }
   *   }
   * }
   * </pre>
   *
   * <p>Note: old authentication was a flat record with just {@code apiKey}; the new shape requires
   * a {@code type: apiKey} discriminator.
   */
  private ObjectNode migrateOpenAiCompatibleNode(ObjectNode node, ObjectMapper mapper) {
    ObjectNode compatObj = (ObjectNode) node.path(OPENAI_COMPATIBLE_LEGACY_ID);

    ObjectNode openaiObj = mapper.createObjectNode();
    openaiObj.put(FIELD_BACKEND, "custom");
    openaiObj.put(FIELD_API_FAMILY, "completions");

    if (compatObj != null && !compatObj.isMissingNode()) {
      // Copy endpoint
      if (compatObj.has(FIELD_ENDPOINT)) {
        openaiObj.set(FIELD_ENDPOINT, compatObj.get(FIELD_ENDPOINT));
      }

      // Map authentication: inject type=apiKey discriminator if missing
      if (compatObj.has(FIELD_AUTHENTICATION)) {
        ObjectNode oldAuth = (ObjectNode) compatObj.get(FIELD_AUTHENTICATION);
        if (!oldAuth.has(FIELD_TYPE)) {
          oldAuth.put(FIELD_TYPE, "apiKey");
        }
        openaiObj.set(FIELD_AUTHENTICATION, oldAuth);
      }

      // Copy headers
      if (compatObj.has(FIELD_HEADERS)) {
        openaiObj.set(FIELD_HEADERS, compatObj.get(FIELD_HEADERS));
      }

      // Copy queryParameters
      if (compatObj.has(FIELD_QUERY_PARAMETERS)) {
        openaiObj.set(FIELD_QUERY_PARAMETERS, compatObj.get(FIELD_QUERY_PARAMETERS));
      }

      // Copy timeouts
      if (compatObj.has(FIELD_TIMEOUTS)) {
        openaiObj.set(FIELD_TIMEOUTS, compatObj.get(FIELD_TIMEOUTS));
      }

      // Copy model (same structure)
      if (compatObj.has(FIELD_MODEL)) {
        openaiObj.set(FIELD_MODEL, compatObj.get(FIELD_MODEL));
      }
    }

    ObjectNode result = mapper.createObjectNode();
    result.put(FIELD_TYPE, OPENAI_ID);
    result.set(OPENAI_ID, openaiObj);
    return result;
  }
}
