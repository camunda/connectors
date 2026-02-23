/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connectors.soap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connectors.soap.SoapConnectorInput.Authentication.None;
import io.camunda.connectors.soap.SoapConnectorInput.Authentication.Signature;
import io.camunda.connectors.soap.SoapConnectorInput.Authentication.Signature.Certificate.KeystoreCertificate;
import io.camunda.connectors.soap.SoapConnectorInput.Authentication.Signature.Certificate.SingleCertificate;
import io.camunda.connectors.soap.SoapConnectorInput.Authentication.UsernameToken;
import io.camunda.connectors.soap.SoapConnectorInput.SoapBodyPart.BodyJson;
import io.camunda.connectors.soap.SoapConnectorInput.SoapBodyPart.BodyTemplate;
import io.camunda.connectors.soap.SoapConnectorInput.SoapHeaderPart.HeaderJson;
import io.camunda.connectors.soap.SoapConnectorInput.SoapHeaderPart.HeaderNone;
import io.camunda.connectors.soap.SoapConnectorInput.SoapHeaderPart.HeaderTemplate;
import io.camunda.connectors.soap.SoapConnectorInput.Version._1_1;
import io.camunda.connectors.soap.SoapConnectorInput.Version._1_2;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SoapConnectorInput(
    @TemplateProperty(
            group = "connection",
            label = "Service URL",
            description = "The URL where the service runs")
        @NotNull
        String serviceUrl,
    @Valid Authentication authentication,
    @Valid Version soapVersion,
    @Valid SoapHeaderPart header,
    @Valid SoapBodyPart body,
    @TemplateProperty(
            label = "Namespaces",
            description = "The namespaces that should be declared on the SOAP Envelope",
            optional = true,
            group = "soap-message",
            feel = FeelMode.required)
        Map<String, String> namespaces,
    @TemplateProperty(
            group = "timeout",
            defaultValue = "20",
            defaultValueType = TemplateProperty.DefaultValueType.Number,
            optional = true,
            description =
                "Sets timeout in seconds to establish a connection or 0 for an infinite timeout")
        Integer connectionTimeoutInSeconds) {
  public enum YesNo {
    Yes,
    No
  }

  @JsonTypeInfo(use = Id.NAME, property = "type")
  @JsonSubTypes({
    @Type(value = HeaderTemplate.class, name = "template"),
    @Type(value = HeaderJson.class, name = "json"),
    @Type(value = HeaderNone.class, name = "none")
  })
  @TemplateDiscriminatorProperty(
      name = "type",
      label = "SOAP header",
      description = "The definition of the SOAP header",
      group = "soap-message",
      defaultValue = "none")
  @NotNull
  public sealed interface SoapHeaderPart {
    @TemplateSubType(id = "template", label = "Template")
    record HeaderTemplate(
        @TemplateProperty(
                label = "XML template",
                description = "The template for the header in XML format",
                type = PropertyType.Text,
                group = "soap-message")
            String template,
        @TemplateProperty(
                label = "XML template context",
                description = "The context that is used to fill the template",
                group = "soap-message",
                feel = FeelMode.required)
            Map<String, Object> context)
        implements SoapHeaderPart {}

    @TemplateSubType(id = "json", label = "XML compatible JSON")
    record HeaderJson(
        @TemplateProperty(
                label = "JSON definition",
                description = "Definition of the SOAP header as JSON object",
                group = "soap-message",
                feel = FeelMode.required)
            Map<String, Object> json)
        implements SoapHeaderPart {}

    @TemplateSubType(id = "none", label = "No SOAP header required")
    record HeaderNone() implements SoapHeaderPart {}
  }

  @JsonTypeInfo(use = Id.NAME, property = "type")
  @JsonSubTypes({
    @Type(value = BodyTemplate.class, name = "template"),
    @Type(value = BodyJson.class, name = "json")
  })
  @TemplateDiscriminatorProperty(
      name = "type",
      label = "SOAP body",
      description = "The XML definition of the SOAP body",
      group = "soap-message",
      defaultValue = "json")
  @NotNull
  public sealed interface SoapBodyPart {
    @TemplateSubType(id = "template", label = "Template")
    record BodyTemplate(
        @TemplateProperty(
                label = "XML template",
                description = "The template for the body in XML format",
                type = PropertyType.Text,
                group = "soap-message")
            String template,
        @TemplateProperty(
                label = "XML template context",
                description = "The context that is used to fill the template",
                group = "soap-message",
                feel = FeelMode.required)
            Map<String, Object> context)
        implements SoapBodyPart {}

    @TemplateSubType(id = "json", label = "XML compatible JSON")
    record BodyJson(
        @TemplateProperty(
                label = "JSON definition",
                description = "Definition of the SOAP body as JSON object",
                group = "soap-message",
                feel = FeelMode.required)
            Map<String, Object> json)
        implements SoapBodyPart {}
  }

  @JsonTypeInfo(use = Id.NAME, property = "version")
  @JsonSubTypes({@Type(value = _1_1.class, name = "1.1"), @Type(value = _1_2.class, name = "1.2")})
  @TemplateDiscriminatorProperty(
      name = "version",
      label = "SOAP version",
      description = "The SOAP version the service uses",
      group = "soap-message",
      defaultValue = "1.1")
  @NotNull
  public sealed interface Version {
    @TemplateSubType(id = "1.1", label = "1.1")
    record _1_1(
        @TemplateProperty(
                label = "SOAPAction HTTP header",
                description = "The SOAPAction HTTP header to be used in the request",
                optional = true,
                group = "soap-message")
            String soapAction)
        implements Version {}

    @TemplateSubType(id = "1.2", label = "1.2")
    record _1_2() implements Version {}
  }

  @JsonTypeInfo(use = Id.NAME, property = "authentication")
  @JsonSubTypes({
    @Type(value = None.class, name = "none"),
    @Type(value = UsernameToken.class, name = "usernameToken"),
    @Type(value = Signature.class, name = "signature")
  })
  @TemplateDiscriminatorProperty(
      name = "authentication",
      label = "Authentication",
      description = "Authentication mechanism to use",
      group = "authentication",
      defaultValue = "none")
  @NotNull
  public sealed interface Authentication {
    @TemplateSubType(id = "none", label = "None")
    record None() implements Authentication {}

    @TemplateSubType(id = "usernameToken", label = "WSS username token")
    record UsernameToken(
        @TemplateProperty(label = "Username", group = "authentication") @NotNull String username,
        @TemplateProperty(label = "Password", group = "authentication") String password,
        @TemplateProperty(label = "Encoded", group = "authentication", type = PropertyType.Dropdown)
            @NotNull
            SoapConnectorInput.YesNo encoded)
        implements Authentication {
      @Override
      public String toString() {
        return "UsernameToken{"
            + "username='"
            + username
            + "'"
            + ", password=[REDACTED]"
            + ", encoded="
            + encoded
            + "}";
      }
    }

    @TemplateSubType(id = "signature", label = "WSS signature")
    record Signature(
        @Valid Certificate certificate,
        @TemplateProperty(
                label = "Signature algorithm",
                group = "authentication",
                optional = true,
                description = "Fully qualified name of an alternative signature algorithm")
            String signatureAlgorithm,
        @TemplateProperty(
                label = "Digest algorithm",
                group = "authentication",
                optional = true,
                description = "Fully qualified name of an alternative digest algorithm")
            String digestAlgorithm,
        @TemplateProperty(
                label = "Timestamp timeout in seconds",
                description = "If set, adds a timestamp header with the given timeout",
                group = "authentication",
                optional = true)
            Integer timestamp,
        @TemplateProperty(
                label = "Signature parts",
                group = "authentication",
                feel = FeelMode.required,
                optional = true,
                description = "Array of signature parts with namespace and localName")
            List<EncryptionPart> encryptionParts)
        implements Authentication {
      @JsonTypeInfo(use = Id.NAME, property = "certificateType")
      @JsonSubTypes({
        @Type(value = SingleCertificate.class, name = "single"),
        @Type(value = KeystoreCertificate.class, name = "keystore")
      })
      @TemplateDiscriminatorProperty(
          name = "certificateType",
          label = "Certificate type",
          description = "From where the certificate is obtained",
          group = "authentication")
      @NotNull
      public sealed interface Certificate {
        @TemplateSubType(id = "single", label = "Single certificate")
        record SingleCertificate(
            @TemplateProperty(
                    label = "Certificate",
                    group = "authentication",
                    description = "The X.509 certificate to use to sign the request")
                @NotNull
                String certificate,
            @TemplateProperty(
                    label = "Private key",
                    group = "authentication",
                    description = "The private key for the certificate")
                @NotNull
                String privateKey)
            implements Certificate {
          @Override
          public String toString() {
            return "SingleCertificate{"
                + "certificate='"
                + certificate
                + "'"
                + ", privateKey=[REDACTED]"
                + "}";
          }
        }

        @TemplateSubType(id = "keystore", label = "Keystore certificate")
        record KeystoreCertificate(
            @TemplateProperty(
                    label = "Keystore location",
                    description = "The keystore to use",
                    group = "authentication")
                @NotNull
                String keystoreLocation,
            @TemplateProperty(
                    label = "Keystore password",
                    description = "The password to access the keystore",
                    group = "authentication")
                @NotNull
                String keystorePassword,
            @TemplateProperty(
                    label = "Certificate alias",
                    description = "The alias for the certificate in the keystore",
                    group = "authentication")
                @NotNull
                String alias,
            @TemplateProperty(
                    label = "Certificate password",
                    description = "The password to access the certificate",
                    group = "authentication")
                @NotNull
                String password)
            implements Certificate {
          @Override
          public String toString() {
            return "KeystoreCertificate{"
                + "keystoreLocation='"
                + keystoreLocation
                + "'"
                + ", keystorePassword=[REDACTED]"
                + ", alias='"
                + alias
                + "'"
                + ", password=[REDACTED]"
                + "}";
          }
        }
      }

      public record EncryptionPart(
          String namespace, String localName, String encryptionModifier, String id) {}
    }
  }
}
