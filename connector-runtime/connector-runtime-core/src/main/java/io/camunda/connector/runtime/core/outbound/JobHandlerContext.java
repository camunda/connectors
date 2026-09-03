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
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.InvalidNullException;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.PropertyBindingException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.JobContext;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.AbstractConnectorContext;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import java.util.Objects;

/**
 * Implementation of {@link io.camunda.connector.api.outbound.OutboundConnectorContext} passed on to
 * a {@link io.camunda.connector.api.outbound.OutboundConnectorFunction} when called from the {@link
 * ConnectorJobHandler}.
 */
public class JobHandlerContext extends AbstractConnectorContext
    implements OutboundConnectorContext {

  private final ActivatedJob job;
  private final ObjectMapper objectMapper;
  private final JobContext jobContext;
  private JsonNode jsonWithSecrets;

  public JobHandlerContext(
      final ActivatedJob job,
      final SecretProvider secretProvider,
      final ValidationProvider validationProvider,
      final ObjectMapper objectMapper,
      final SecretFilter secretFilter) {
    super(secretProvider, secretFilter, validationProvider);
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
      jsonWithSecrets = getSecretHandler().replaceSecrets(parseVariables());
    }
    return jsonWithSecrets;
  }

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
      throw new ConnectorException("JSON_PARSE_ERROR", "This is not a JSON object");
    }
    return variables;
  }

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
    try {
      return objectMapper.treeToValue(getJsonReplacedWithSecrets(), cls);
    } catch (JsonProcessingException e) {
      throw translateJsonException(e);
    }
  }

  private static ConnectorException translateJsonException(JsonProcessingException e) {
    if (e instanceof JsonParseException) {
      return new ConnectorException("JSON_PARSE_ERROR", "This is not a JSON object");
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
      return new ConnectorException("JSON_FORMAT_ERROR", errorMessage);
    }
    if (e instanceof MismatchedInputException) {
      return new ConnectorException("JSON_MISMATCH_ERROR", e.getOriginalMessage());
    }
    return new ConnectorException(
        "JSON_PROCESSING_ERROR", "Exception: " + e.getClass().getSimpleName() + "was raised");
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
}
