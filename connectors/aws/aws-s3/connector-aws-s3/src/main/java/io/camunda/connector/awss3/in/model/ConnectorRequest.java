package io.camunda.connector.awss3.in.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConnectorRequest {

  @Valid
  @NotNull
  private AuthenticationRequestData authentication;

  @Valid
  @NotNull
  private RequestDetails requestDetails;
}
