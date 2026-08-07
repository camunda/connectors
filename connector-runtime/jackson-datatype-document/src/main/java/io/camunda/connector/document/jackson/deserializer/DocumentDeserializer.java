/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.document.jackson.deserializer;

import static io.camunda.connector.document.jackson.deserializer.DeserializationUtil.isDocumentReference;
import static io.camunda.connector.document.jackson.deserializer.DeserializationUtil.isIntrinsicFunction;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.document.jackson.DocumentReferenceModel;
import io.camunda.connector.document.jackson.IntrinsicFunctionExecutor;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer.DocumentModuleSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;

/**
 * Deserializer for {@link Document} targets. It supports both the case where the source is a
 * document reference and the case where the source is an operation that returns a document.
 */
public class DocumentDeserializer extends AbstractDeserializer<Document> {

  private final IntrinsicFunctionObjectResultDeserializer intrinsicFunctionDeserializer;
  private final DocumentFactory documentFactory;
  private final Map<String, DocumentFactory> documentFactoriesByPhysicalTenantId;

  public DocumentDeserializer(
      DocumentFactory documentFactory,
      IntrinsicFunctionExecutor intrinsicFunctionExecutor,
      DocumentModuleSettings settings) {
    super(settings);
    this.documentFactory = documentFactory;
    this.documentFactoriesByPhysicalTenantId = null;
    this.intrinsicFunctionDeserializer =
        new IntrinsicFunctionObjectResultDeserializer(intrinsicFunctionExecutor, settings);
  }

  /**
   * Physical-tenant-aware variant: resolves the {@link DocumentFactory} to use for each
   * deserialization call from the {@link DocumentFactory#PHYSICAL_TENANT_ID_ATTRIBUTE} reader
   * attribute, set by the writer (e.g. {@code JobHandlerContext}/{@code
   * InboundConnectorContextImpl}) before deserializing — necessary because this deserializer is
   * registered into a long-lived, shared {@code ObjectMapper}/module, not rebuilt per physical
   * tenant.
   */
  public DocumentDeserializer(
      Map<String, DocumentFactory> documentFactoriesByPhysicalTenantId,
      IntrinsicFunctionExecutor intrinsicFunctionExecutor,
      DocumentModuleSettings settings) {
    super(settings);
    this.documentFactory = null;
    this.documentFactoriesByPhysicalTenantId = documentFactoriesByPhysicalTenantId;
    this.intrinsicFunctionDeserializer =
        new IntrinsicFunctionObjectResultDeserializer(intrinsicFunctionExecutor, settings);
  }

  private DocumentFactory resolveDocumentFactory(DeserializationContext context) {
    if (documentFactory != null) {
      return documentFactory;
    }
    var physicalTenantId =
        (String) context.getAttribute(DocumentFactory.PHYSICAL_TENANT_ID_ATTRIBUTE);
    if (physicalTenantId != null) {
      var resolved = documentFactoriesByPhysicalTenantId.get(physicalTenantId);
      if (resolved == null) {
        throw new IllegalStateException(
            "No DocumentFactory configured for physical tenant '" + physicalTenantId + "'");
      }
      return resolved;
    }
    if (documentFactoriesByPhysicalTenantId.size() == 1) {
      return documentFactoriesByPhysicalTenantId.values().iterator().next();
    }
    throw new IllegalStateException(
        "Cannot resolve a DocumentFactory to deserialize a Document: no physical tenant ID "
            + "attribute was set on the reader and "
            + documentFactoriesByPhysicalTenantId.size()
            + " physical tenants are configured");
  }

  @Override
  protected Document handleJsonNode(JsonNode node, DeserializationContext context) {
    if (isDocumentReference(node)) {
      final var reference = context.readTreeAsValue(node, DocumentReferenceModel.class);
      return resolveDocumentFactory(context).resolve(reference);
    }
    if (node.isArray()) {
      List<JsonNode> elements = new ArrayList<>(node.values());
      if (elements.size() == 1 && isDocumentReference(elements.get(0))) {
        final var reference =
            context.readTreeAsValue(elements.get(0), DocumentReferenceModel.class);
        return resolveDocumentFactory(context).resolve(reference);
      } else {
        throw new IllegalArgumentException(
            "Cant bind a multi element document array to a single document.");
      }
    }
    if (isIntrinsicFunction(node)) {
      // counter is decremented in the function deserializer
      final Object functionResult = intrinsicFunctionDeserializer.handleJsonNode(node, context);
      if (functionResult instanceof Document) {
        return (Document) functionResult;
      }
      throw new IllegalArgumentException(
          "Unsupported operation result, expected a document, got: " + functionResult);
    }
    throw new IllegalArgumentException(
        "Unsupported node format, expected either a document reference or an operation, got: "
            + node);
  }
}
