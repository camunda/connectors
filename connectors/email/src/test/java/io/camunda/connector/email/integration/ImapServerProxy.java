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
import java.nio.charset.StandardCharsets;
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
    }
  }

  private void pipeClientToServer(InputStream in, OutputStream out) {
    try (out) {
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) >= 0) {
        out.write(buf, 0, n);
        out.flush();
        if (!successMode.get()) {
          out.write("\r\n".getBytes());
          out.write("* BYE [ALERT] Proxy in failure mode\r\n".getBytes(StandardCharsets.US_ASCII));
          out.flush();
          break;
        }
      }
    } catch (Exception ignored) {
    }
  }

  private void pipeServerToClient(InputStream in, OutputStream out) {
    try (out) {
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) >= 0) {
        out.write(buf, 0, n);
        out.flush();
      }
    } catch (Exception ignored) {
    }
  }

  public void cutConnection() {
    successMode.set(false);
  }

  public void setOkProxy() {
    successMode.set(true);
  }

  @Override
  public void close() throws Exception {
    listen.close();
    pool.shutdownNow();
  }
}
