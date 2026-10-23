/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.rabbitmq.common.model.UriAuthentication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConnectionFactorySupplierTest {

  private final ConnectionFactorySupplier supplier = new ConnectionFactorySupplier();

  @Test
  void usesHostPortCredentialsAndVirtualHostFromUri() throws Exception {
    var factory =
        supplier.createFactory(
            new UriAuthentication("amqps://user:pw@my-host:5671/my-vhost"), null);

    assertThat(factory.getHost()).isEqualTo("my-host");
    assertThat(factory.getPort()).isEqualTo(5671);
    assertThat(factory.getUsername()).isEqualTo("user");
    assertThat(factory.getPassword()).isEqualTo("pw");
    assertThat(factory.getVirtualHost()).isEqualTo("my-vhost");
  }

  @ParameterizedTest
  @ValueSource(strings = {"amqps://user:pw@my-host:notaport/", "amqps://user:pw@my_rabbit:5671/"})
  void rejectsUriWhoseHostCannotBeParsedInsteadOfFallingBackToLocalhost(String uri) {
    assertThatThrownBy(() -> supplier.createFactory(new UriAuthentication(uri), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageNotContaining("pw");
  }
}
