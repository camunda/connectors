package io.camunda.connector.awss3.in.model;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.ToString;

@Data
public class AuthenticationRequestData {

  @NotEmpty
  @ToString.Exclude
  private String accessKey;

  @NotEmpty
  @ToString.Exclude
  private String secretKey;

}
