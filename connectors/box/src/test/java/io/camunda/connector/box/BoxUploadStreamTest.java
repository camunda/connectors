/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.box;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.box.sdkgen.box.developertokenauth.BoxDeveloperTokenAuth;
import com.box.sdkgen.client.BoxClient;
import com.box.sdkgen.networking.network.NetworkSession;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.box.model.BoxRequest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

public class BoxUploadStreamTest {

  @Test
  void closesDocumentStreamWhenTheUploadFails() {
    var closed = new AtomicBoolean();
    InputStream stream =
        new ByteArrayInputStream(new byte[] {1, 2, 3}) {
          @Override
          public void close() {
            closed.set(true);
          }
        };
    Document document =
        (Document)
            Proxy.newProxyInstance(
                Document.class.getClassLoader(),
                new Class<?>[] {Document.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("asInputStream")) {
                    return stream;
                  }
                  throw new UnsupportedOperationException(method.getName());
                });
    BoxClient client =
        new BoxClient.Builder(new BoxDeveloperTokenAuth("token"))
            .networkSession(
                new NetworkSession()
                    .withNetworkClient(
                        options -> {
                          throw new IllegalStateException("authentication failed");
                        }))
            .build();

    assertThatThrownBy(
            () ->
                BoxOperations.uploadFile(
                    new BoxRequest.Operation.UploadFile("/", document, "file.txt"), client))
        .isInstanceOf(IllegalStateException.class);
    assertThat(closed).isTrue();
  }

  @Test
  void rejectsUploadWhenNeitherNameNorDocumentFileNameIsAvailable() {
    DocumentMetadata metadata =
        (DocumentMetadata)
            Proxy.newProxyInstance(
                DocumentMetadata.class.getClassLoader(),
                new Class<?>[] {DocumentMetadata.class},
                (proxy, method, args) -> method.getName().equals("getFileName") ? "" : null);
    Document document =
        (Document)
            Proxy.newProxyInstance(
                Document.class.getClassLoader(),
                new Class<?>[] {Document.class},
                (proxy, method, args) -> metadata);

    assertThatThrownBy(() -> new BoxRequest.Operation.UploadFile("/", document, "").getFileName())
        .isInstanceOf(ConnectorInputException.class);
  }
}
