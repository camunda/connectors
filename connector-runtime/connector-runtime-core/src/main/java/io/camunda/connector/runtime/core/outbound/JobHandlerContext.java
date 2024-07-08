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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.*;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.JobContext;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.AbstractConnectorContext;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link io.camunda.connector.api.outbound.OutboundConnectorContext} passed on to
 * a {@link io.camunda.connector.api.outbound.OutboundConnectorFunction} when called from the {@link
 * ConnectorJobHandler}.
 */
public class JobHandlerContext extends AbstractConnectorContext
    implements OutboundConnectorContext {

  private static final Logger log = LoggerFactory.getLogger(JobHandlerContext.class);
  private final ActivatedJob job;

  private final ObjectMapper objectMapper;
  private final JobContext jobContext;
  private String jsonWithSecrets = null;

  public JobHandlerContext(
      final ActivatedJob job,
      final SecretProvider secretProvider,
      final ValidationProvider validationProvider,
      final ObjectMapper objectMapper) {
    super(secretProvider, validationProvider);
    this.job = job;
    this.objectMapper = objectMapper;
    this.jobContext = new ActivatedJobContext(job, this::getJsonReplacedWithSecrets);
  }

  @Override
  public <T> T bindVariables(Class<T> cls) {
    var mappedObject = mapJson(cls);
    getValidationProvider().validate(mappedObject);
    return mappedObject;
  }

  private String getJsonReplacedWithSecrets() {
    if (jsonWithSecrets == null) {
      jsonWithSecrets = getSecretHandler().replaceSecrets(job.getVariables());
    }
    return jsonWithSecrets;
  }

  private <T> T mapJson(Class<T> cls) {
    var jsonWithSecrets = getJsonReplacedWithSecrets();
    try {
      return objectMapper.readValue(jsonWithSecrets, cls);
    } catch (JsonParseException e) {
      throw new ConnectorException("JSON_PARSE_ERROR", "This is not a JSON object");
    } catch (InvalidFormatException
        | InvalidNullException
        | InvalidTypeIdException
        | PropertyBindingException e) {
      String errorMessage =
          e.getPath().stream()
              .map(JsonMappingException.Reference::getFieldName)
              .reduce((s, s2) -> s.concat(", ").concat(s2))
              .map("Json object contains an invalid field: "::concat)
              .map(
                  s ->
                      e.getTargetType() == null
                          ? s
                          : s.concat(". It Must be `")
                              .concat(e.getTargetType().getSimpleName())
                              .concat("`"))
              .orElse("Unexpected Error, Further investigation is needed");

      throw new ConnectorException("JSON_FORMAT_ERROR", errorMessage);
    } catch (MismatchedInputException e) {
      throw new ConnectorException("JSON_MISMATCH_ERROR", e.getOriginalMessage());
    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          "JSON_PROCESSING_ERROR", "Exception: " + e.getClass().getSimpleName() + "was raised");
    }
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
