/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.integration;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class ImapServerProxy implements AutoCloseable {
  private final String backendHost;
  private final int backendPort;
  private final ServerSocket listen;
  private final ExecutorService pool = Executors.newCachedThreadPool();
  private final AtomicReference<Boolean> successMode = new AtomicReference<>(true);
  private final Set<SocketPair> activePairs = ConcurrentHashMap.newKeySet();

  public ImapServerProxy(int listenPort, String backendHost, int backendPort) throws IOException {
    this.backendHost = backendHost;
    this.backendPort = backendPort;
    this.listen = new ServerSocket(listenPort);
    pool.submit(
        () -> {
          while (!listen.isClosed()) {
            final Socket client;
            try {
              client = listen.accept();
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
            pool.submit(() -> handle(client));
          }
        });
  }

  private void handle(Socket client) {
    try (client) {
      proxy(client);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void proxy(Socket client) throws IOException {
    try (Socket backend = new Socket()) {
      backend.connect(new InetSocketAddress(backendHost, backendPort), 2000);

      SocketPair pair = new SocketPair(client, backend);
      activePairs.add(pair);
      try {
        Future<?> f1 =
            // All bytes coming from client are given to the server
            pool.submit(
                () -> {
                  try {
                    pipeClientToServer(client.getInputStream(), backend.getOutputStream());
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                });
        Future<?> f2 =
            // All bytes coming from server are given to the client
            pool.submit(
                () -> {
                  try {
                    pipeServerToClient(backend.getInputStream(), client.getOutputStream());
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                });
        try {
          f1.get();
        } catch (Exception ignored) {
        }
        try {
          f2.get();
        } catch (Exception ignored) {
        }
      } finally {
        // Clean up when proxy is done
        activePairs.remove(pair);
      }
    }
  }

  private void pipeClientToServer(
      InputStream byteReceivedFromClient, OutputStream byteToSendToServer) {
    try (byteToSendToServer) {
      byte[] buf = new byte[8192];
      int n;
      // If successMode is false, our stop piping data to the server, simulating a server not
      // responding
      while ((n = byteReceivedFromClient.read(buf)) >= 0) {
        byteToSendToServer.write(buf, 0, n);
        byteToSendToServer.flush();
        if (!successMode.get()) {
          return;
        }
      }
    } catch (Exception ignored) {
    }
  }

  private void pipeServerToClient(
      InputStream byteReceivedFromServer, OutputStream byteToSendToClient) {
    try (byteToSendToClient) {
      byte[] buf = new byte[8192];
      int n;
      while ((n = byteReceivedFromServer.read(buf)) >= 0) {
        byteToSendToClient.write(buf, 0, n);
        byteToSendToClient.flush();
        if (!successMode.get()) {
          return;
        }
      }
    } catch (Exception ignored) {
    }
  }

  public void cutConnection() {
    successMode.set(false);
    activePairs.forEach(
        pair -> {
          try {
            pair.backend.shutdownOutput();
          } catch (IOException ignored) {
          }
        });
  }

  public void setOkProxy() {
    successMode.set(true);
  }

  @Override
  public void close() throws Exception {
    listen.close();
    pool.shutdownNow();
  }

  private static class SocketPair {
    final Socket client;
    final Socket backend;

    SocketPair(Socket client, Socket backend) {
      this.client = client;
      this.backend = backend;
    }
  }
}
