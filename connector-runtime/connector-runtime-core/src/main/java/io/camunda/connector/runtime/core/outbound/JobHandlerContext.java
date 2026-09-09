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
package io.camunda.connector.runtime.core.outbound;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.*;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.outbound.JobContext;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.AbstractConnectorContext;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import io.camunda.document.Document;
import io.camunda.document.factory.DocumentFactory;
import io.camunda.document.reference.DocumentReference;
import io.camunda.document.store.DocumentCreationRequest;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link io.camunda.connector.api.outbound.OutboundConnectorContext} passed on to
 * a {@link io.camunda.connector.api.outbound.OutboundConnectorFunction} when called from the
 * JobHandler, e.g. SpringConnectorJobHandler.
 */
public class JobHandlerContext extends AbstractConnectorContext
    implements OutboundConnectorContext {

  private static final Logger log = LoggerFactory.getLogger(JobHandlerContext.class);
  private final ActivatedJob job;

  private final ObjectMapper objectMapper;
  private final JobContext jobContext;
  private final DocumentFactory documentFactory;
  private JsonNode jsonWithSecrets = null;

  public JobHandlerContext(
      final ActivatedJob job,
      final SecretProvider secretProvider,
      final ValidationProvider validationProvider,
      final DocumentFactory documentFactory,
      final ObjectMapper objectMapper,
      final SecretFilter secretFilter) {
    super(secretProvider, secretFilter, validationProvider);
    this.documentFactory = documentFactory;
    this.job = job;
    this.objectMapper = objectMapper;
    this.jobContext = new ActivatedJobContext(job, () -> writeJson(getJsonReplacedWithSecrets()));
  }

  @Override
  public <T> T bindVariables(Class<T> cls) {
    var mappedObject = mapJson(cls);
    getValidationProvider().validate(mappedObject);
    return mappedObject;
  }

  private JsonNode getJsonReplacedWithSecrets() {
    if (jsonWithSecrets == null) {
      jsonWithSecrets =
          getSecretHandler().replaceSecrets(parseVariables(), new SecretContext(job.getTenantId()));
    }
    return jsonWithSecrets;
  }

  /**
   * Parses via this context's own {@code objectMapper}, with {@code USE_BIG_DECIMAL_FOR_FLOATS}
   * enabled, rather than {@code job.getVariablesAsType(JsonNode.class)} — the client's own {@code
   * JsonMapper} parses untyped JSON numbers to {@code double} by default, which loses precision
   * (e.g. {@code 0.1234567890123456789012345} -> {@code 0.12345678901234568}) before the value ever
   * reaches a {@code BigDecimal}-typed connector field. Reading through an {@code ObjectReader}
   * rather than reconfiguring the shared mapper keeps that feature scoped to this one parse. The
   * node factory is additionally configured with {@code withExactBigDecimals(true)}: its default
   * strips trailing zeros off the parsed {@code BigDecimal} itself at node construction (e.g.
   * {@code 1.10} becomes scale-1 {@code 1.1}), a value change a {@code BigDecimal}-typed connector
   * field would observe via {@code equals}, not merely a difference in how it prints.
   */
  private JsonNode parseVariables() {
    JsonNode variables;
    try {
      variables =
          objectMapper
              .reader()
              .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
              .with(JsonNodeFactory.withExactBigDecimals(true))
              .forType(JsonNode.class)
              .readValue(job.getVariables());
    } catch (JsonProcessingException e) {
      throw translateJsonException(e);
    }
    if (!variables.isObject()) {
      throw new ConnectorInputException("This is not a JSON object");
    }
    return variables;
  }

  /**
   * {@code JsonNode.toString()} serializes through Jackson's shared internal mapper, which writes a
   * {@code DecimalNode} via plain {@code BigDecimal.toString()} — reintroducing scientific notation
   * for small-magnitude values (e.g. {@code 0.00000001} -> {@code 1E-8}) even though the digits
   * themselves survived parsing. {@code WRITE_BIGDECIMAL_AS_PLAIN} keeps the plain-decimal form the
   * raw job JSON used.
   *
   * <p>Jackson refuses plain output for a {@code BigDecimal} whose scale falls outside ±9999 (e.g.
   * {@code 1e-10000}, which parses fine into a {@code DecimalNode}). Such a document is written
   * without the feature instead, i.e. in scientific notation — the same text the previous
   * raw-string path returned — rather than failing the job.
   */
  private String writeJson(JsonNode node) {
    try {
      return objectMapper
          .writer()
          .with(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
          .writeValueAsString(node);
    } catch (JsonGenerationException e) {
      return writeJsonWithoutPlainDecimals(node);
    } catch (JsonProcessingException e) {
      throw translateJsonException(e);
    }
  }

  private String writeJsonWithoutPlainDecimals(JsonNode node) {
    try {
      return objectMapper.writeValueAsString(node);
    } catch (JsonProcessingException e) {
      throw translateJsonException(e);
    }
  }

  private <T> T mapJson(Class<T> cls) {
    var jsonWithSecrets = getJsonReplacedWithSecrets();
    try {
      return objectMapper.treeToValue(jsonWithSecrets, cls);
    } catch (JsonProcessingException e) {
      throw translateJsonException(e);
    }
  }

  private static ConnectorInputException translateJsonException(JsonProcessingException e) {
    if (e instanceof JsonParseException) {
      return new ConnectorInputException("This is not a JSON object", e);
    }
    if (e instanceof InvalidFormatException
        || e instanceof InvalidNullException
        || e instanceof InvalidTypeIdException
        || e instanceof PropertyBindingException) {
      MismatchedInputException mappingException = (MismatchedInputException) e;
      String errorMessage =
          mappingException.getPath().stream()
              .map(JsonMappingException.Reference::getFieldName)
              .reduce((s, s2) -> s.concat(", ").concat(s2))
              .map("Json object contains an invalid field: "::concat)
              .map(
                  s ->
                      mappingException.getTargetType() == null
                          ? s
                          : s.concat(". It Must be `")
                              .concat(mappingException.getTargetType().getSimpleName())
                              .concat("`"))
              .orElse("Unexpected Error, Further investigation is needed");
      return new ConnectorInputException(errorMessage, e);
    }
    return new ConnectorInputException(e.getOriginalMessage(), e);
  }

  @Override
  public JobContext getJobContext() {
    return jobContext;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    JobHandlerContext that = (JobHandlerContext) o;
    return Objects.equals(job, that.job);
  }

  @Override
  public int hashCode() {
    return Objects.hash(job);
  }

  @Override
  public Document resolve(DocumentReference reference) {
    return documentFactory.resolve(reference);
  }

  @Override
  public Document create(DocumentCreationRequest request) {
    return documentFactory.create(request);
  }
}
