/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connectors.soap;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NullableBoolean;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyBinding;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
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
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * @param soapCredential reusable SOAP credential; when bound, its authentication takes precedence
 *     over the inline one (see {@link #authentication()}). Declared before the fields it gates, as
 *     required by ConditionPropertyOrderRule.
 * @param authentication hidden once a credential is bound, and then dropped in the compact
 *     constructor, so a leftover inline discriminator default is neither used nor validated.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SoapConnectorInput(
    @TemplateProperty(
            group = "connection",
            label = "Service URL",
            tooltip = "The URL where the service runs")
        @NotNull
        String serviceUrl,
    @TemplateProperty(
            id = "soapCredential",
            label = "SOAP credential",
            group = "authentication",
            type = PropertyType.Configuration,
            optional = true,
            binding = @PropertyBinding(name = "soapCredential"),
            description =
                "Choose a reusable SOAP credential, or configure one-time authentication parameters"
                    + " below.")
        @Valid
        SoapCredentialConfiguration soapCredential,
    @NestedProperties(
            condition =
                @PropertyCondition(property = "soapCredential", isEmpty = NullableBoolean.TRUE))
        @Valid
        Authentication authentication,
    @Valid Version soapVersion,
    @Valid SoapHeaderPart header,
    @Valid SoapBodyPart body,
    @TemplateProperty(
            label = "Namespaces",
            tooltip = "The namespaces that should be declared on the SOAP Envelope",
            optional = true,
            group = "soap-message",
            feel = FeelMode.required)
        Map<String, String> namespaces,
    @TemplateProperty(
            group = "timeout",
            defaultValue = "20",
            defaultValueType = TemplateProperty.DefaultValueType.Number,
            optional = true,
            tooltip =
                "Time in seconds to wait when establishing a connection, or 0 to wait indefinitely")
        Integer connectionTimeoutInSeconds) {

  public SoapConnectorInput {
    if (soapCredential != null) {
      authentication = null;
    }
  }

  /** The bound credential's authentication wins over the inline one. */
  @Override
  public Authentication authentication() {
    return soapCredential != null && soapCredential.authentication() != null
        ? soapCredential.authentication().toAuthentication()
        : authentication;
  }

  @AssertTrue(
      message = "No authentication provided by the reusable credential or the element template")
  @JsonIgnore
  public boolean isAuthenticationPresent() {
    return authentication() != null;
  }

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
                tooltip = "The template for the header in XML format",
                type = PropertyType.Text,
                group = "soap-message")
            String template,
        @TemplateProperty(
                label = "XML template context",
                tooltip = "The context that is used to fill the template",
                group = "soap-message",
                feel = FeelMode.required)
            Map<String, Object> context)
        implements SoapHeaderPart {}

    @TemplateSubType(id = "json", label = "XML compatible JSON")
    record HeaderJson(
        @TemplateProperty(
                label = "JSON definition",
                tooltip = "Definition of the SOAP header as JSON object",
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
                tooltip = "The template for the body in XML format",
                type = PropertyType.Text,
                group = "soap-message")
            String template,
        @TemplateProperty(
                label = "XML template context",
                tooltip = "The context that is used to fill the template",
                group = "soap-message",
                feel = FeelMode.required)
            Map<String, Object> context)
        implements SoapBodyPart {}

    @TemplateSubType(id = "json", label = "XML compatible JSON")
    record BodyJson(
        @TemplateProperty(
                label = "JSON definition",
                tooltip = "Definition of the SOAP body as JSON object",
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
        @TemplateProperty(label = "SOAPAction HTTP header", optional = true, group = "soap-message")
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
        @TemplateProperty(label = "Password", group = "authentication", secret = true)
            String password,
        @TemplateProperty(label = "Encoded", group = "authentication", type = PropertyType.Dropdown)
            @NotNull
            SoapConnectorInput.YesNo encoded)
        implements Authentication, CredentialAuthentication {
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

      @Override
      public Authentication toAuthentication() {
        return this;
      }
    }

    @TemplateSubType(id = "signature", label = "WSS signature")
    record Signature(
        @Valid Certificate certificate,
        @TemplateProperty(
                label = "Signature algorithm",
                group = "authentication",
                optional = true,
                tooltip = "Fully qualified name of an alternative signature algorithm")
            String signatureAlgorithm,
        @TemplateProperty(
                label = "Digest algorithm",
                group = "authentication",
                optional = true,
                tooltip = "Fully qualified name of an alternative digest algorithm")
            String digestAlgorithm,
        @TemplateProperty(
                label = "Timestamp timeout in seconds",
                tooltip = "If set, adds a timestamp header with the given timeout",
                group = "authentication",
                optional = true)
            Integer timestamp,
        @TemplateProperty(
                label = "Signature parts",
                group = "authentication",
                feel = FeelMode.required,
                optional = true,
                tooltip = "Array of signature parts with namespace and localName")
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
                    tooltip = "The X.509 certificate to use to sign the request")
                @NotNull
                String certificate,
            @TemplateProperty(
                    label = "Private key",
                    group = "authentication",
                    secret = true,
                    tooltip = "The private key for the certificate")
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
            @TemplateProperty(label = "Keystore location", group = "authentication") @NotNull
                String keystoreLocation,
            @TemplateProperty(label = "Keystore password", group = "authentication", secret = true)
                @NotNull
                String keystorePassword,
            @TemplateProperty(
                    label = "Certificate alias",
                    tooltip = "The alias for the certificate in the keystore",
                    group = "authentication")
                @NotNull
                String alias,
            @TemplateProperty(
                    label = "Certificate password",
                    group = "authentication",
                    secret = true)
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

  /**
   * The authentication variants a reusable credential can hold. {@link
   * Authentication.Signature.Certificate.KeystoreCertificate} is left out: it points at a file
   * mounted into the connector runtime, which a credential shared across runtimes cannot guarantee,
   * so it is only offered inline.
   */
  @JsonTypeInfo(use = Id.NAME, property = "type")
  @JsonSubTypes({
    @Type(value = UsernameToken.class, name = "usernameToken"),
    @Type(value = CredentialAuthentication.SingleCertificateSignature.class, name = "signature")
  })
  @TemplateDiscriminatorProperty(
      name = "type",
      label = "Authentication",
      description = "Authentication mechanism to use",
      group = "authentication",
      defaultValue = "usernameToken")
  public sealed interface CredentialAuthentication
      permits UsernameToken, CredentialAuthentication.SingleCertificateSignature {

    /** The inline authentication the SOAP client understands. */
    Authentication toAuthentication();

    @TemplateSubType(id = "signature", label = "WSS signature (single certificate)")
    record SingleCertificateSignature(
        @TemplateProperty(
                label = "Certificate",
                group = "authentication",
                tooltip = "The X.509 certificate to use to sign the request")
            @NotNull
            String certificate,
        @TemplateProperty(
                label = "Private key",
                group = "authentication",
                secret = true,
                tooltip = "The private key for the certificate")
            @NotNull
            String privateKey,
        @TemplateProperty(
                label = "Signature algorithm",
                group = "authentication",
                optional = true,
                tooltip = "Fully qualified name of an alternative signature algorithm")
            String signatureAlgorithm,
        @TemplateProperty(
                label = "Digest algorithm",
                group = "authentication",
                optional = true,
                tooltip = "Fully qualified name of an alternative digest algorithm")
            String digestAlgorithm,
        @TemplateProperty(
                label = "Timestamp timeout in seconds",
                tooltip = "If set, adds a timestamp header with the given timeout",
                group = "authentication",
                optional = true)
            Integer timestamp)
        implements CredentialAuthentication {

      @Override
      public Authentication toAuthentication() {
        return new Signature(
            new SingleCertificate(certificate, privateKey),
            signatureAlgorithm,
            digestAlgorithm,
            timestamp,
            null);
      }

      @Override
      public String toString() {
        return "SingleCertificateSignature{certificate='"
            + certificate
            + "', privateKey=[REDACTED], signatureAlgorithm="
            + signatureAlgorithm
            + ", digestAlgorithm="
            + digestAlgorithm
            + ", timestamp="
            + timestamp
            + "}";
      }
    }
  }
}
