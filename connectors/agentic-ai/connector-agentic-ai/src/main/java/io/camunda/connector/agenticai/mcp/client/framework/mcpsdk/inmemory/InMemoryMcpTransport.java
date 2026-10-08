/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.inmemory;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCMessage;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpServerTransport;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

/**
 * Connects an MCP SDK client to an MCP SDK server running in the same JVM. Messages are handed over
 * directly - no sockets or processes are involved.
 *
 * <p>One instance serves exactly one client/server pair: use {@link #clientTransport()} for the
 * client and {@link #serverTransportProvider()} for the server.
 */
public class InMemoryMcpTransport {

  private final ServerSide serverSide = new ServerSide();
  private final ClientSide clientSide = new ClientSide();

  public McpClientTransport clientTransport() {
    return clientSide;
  }

  public McpServerTransportProvider serverTransportProvider() {
    return serverSide;
  }

  private final class ClientSide implements McpClientTransport {

    private volatile @Nullable Function<Mono<JSONRPCMessage>, Mono<JSONRPCMessage>> handler;

    @Override
    public Mono<Void> connect(Function<Mono<JSONRPCMessage>, Mono<JSONRPCMessage>> handler) {
      return Mono.fromRunnable(
          () -> {
            this.handler = handler;
            serverSide.openSession();
          });
    }

    @Override
    public Mono<Void> sendMessage(JSONRPCMessage message) {
      return serverSide.receive(message);
    }

    Mono<Void> deliver(JSONRPCMessage message) {
      final var currentHandler = handler;
      if (currentHandler == null) {
        return Mono.error(new IllegalStateException("In-memory MCP client is not connected"));
      }
      return Mono.just(message).transform(currentHandler).then();
    }

    @Override
    public Mono<Void> closeGracefully() {
      return serverSide.closeGracefully();
    }

    @Override
    public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
      return McpJsonDefaults.getMapper().convertValue(data, typeRef);
    }
  }

  private final class ServerSide implements McpServerTransportProvider {

    private final McpServerTransport sessionTransport = new SessionTransport();
    private volatile McpServerSession.@Nullable Factory sessionFactory;
    private volatile @Nullable McpServerSession session;

    @Override
    public void setSessionFactory(McpServerSession.Factory sessionFactory) {
      this.sessionFactory = sessionFactory;
    }

    void openSession() {
      if (sessionFactory == null) {
        throw new IllegalStateException("In-memory MCP server is not started");
      }
      session = sessionFactory.create(sessionTransport);
    }

    Mono<Void> receive(JSONRPCMessage message) {
      final var currentSession = session;
      if (currentSession == null) {
        return Mono.error(new IllegalStateException("In-memory MCP session is not open"));
      }
      return currentSession.handle(message);
    }

    @Override
    public Mono<Void> notifyClients(String method, Object params) {
      final var currentSession = session;
      return currentSession == null
          ? Mono.empty()
          : currentSession.sendNotification(method, params);
    }

    @Override
    public Mono<Void> closeGracefully() {
      final var currentSession = session;
      session = null;
      return currentSession == null ? Mono.empty() : currentSession.closeGracefully();
    }
  }

  private final class SessionTransport implements McpServerTransport {

    @Override
    public Mono<Void> sendMessage(JSONRPCMessage message) {
      return clientSide.deliver(message);
    }

    @Override
    public Mono<Void> closeGracefully() {
      return Mono.empty();
    }

    @Override
    public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
      return McpJsonDefaults.getMapper().convertValue(data, typeRef);
    }
  }
}
