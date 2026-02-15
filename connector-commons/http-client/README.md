# Connectors HTTP client

An HTTP client for internal use. Provides a flexible API for making HTTP requests and handling responses.

## Example Usage

### Handling response with one of the built-in handlers

```java
HttpClient httpClient = new CustomApacheHttpClient();

HttpClientRequest request = new HttpClientRequest();
request.setMethod(HttpMethod.GET);
request.setUrl("https://api.example.com/data");

HttpResponse<String> result = httpClient.execute(request, ResponseMappers.asString());
```

See [ResponseMappers](src/main/java/io/camunda/connector/http/client/mapper/ResponseMappers.java) for more built-in response mappers.

### Handling response with a custom handler

```java
HttpClient httpClient = new CustomApacheHttpClient();
HttpClientRequest request = new HttpClientRequest();
request.setMethod(HttpMethod.GET);
request.setUrl("https://api.example.com/data");

HttpResponse<CustomData> result = httpClient.execute(request, response -> {
  // Custom logic to handle the response
  String body = new String(response.body().readAllBytes());
  // Parse body into CustomData object
  return new CustomData(body);
});
```

See [StreamingHttpResponse](src/main/java/io/camunda/connector/http/client/mapper/StreamingHttpResponse.java) -
raw response model that provides access to response body as InputStream for custom processing.

## Configuration

The HTTP client can be configured using the following environment variables:

`CONNECTOR_HTTP_CLIENT_MAX_BODY_SIZE`: Defines the maximum size of the response body that can be processed by built-in handlers. Default is 50 MB.
This limit does not apply when using custom response handlers.

### Proxy Configuration

Both the Apache HTTP client and the JDK `HttpClient` can be configured to use a proxy via environment variables.
Different proxy settings can be configured for HTTP and HTTPS target URLs.

| Environment Variable | Description |
|---|---|
| `CONNECTOR_HTTP_PROXY_HOST` | Proxy host for HTTP targets |
| `CONNECTOR_HTTP_PROXY_PORT` | Proxy port for HTTP targets |
| `CONNECTOR_HTTP_PROXY_SCHEME` | Proxy scheme for HTTP targets (default: `http`) |
| `CONNECTOR_HTTP_PROXY_USER` | Proxy username for HTTP targets (optional) |
| `CONNECTOR_HTTP_PROXY_PASSWORD` | Proxy password for HTTP targets (optional) |
| `CONNECTOR_HTTPS_PROXY_HOST` | Proxy host for HTTPS targets |
| `CONNECTOR_HTTPS_PROXY_PORT` | Proxy port for HTTPS targets |
| `CONNECTOR_HTTPS_PROXY_SCHEME` | Proxy scheme for HTTPS targets (default: `http`) |
| `CONNECTOR_HTTPS_PROXY_USER` | Proxy username for HTTPS targets (optional) |
| `CONNECTOR_HTTPS_PROXY_PASSWORD` | Proxy password for HTTPS targets (optional) |
| `CONNECTOR_HTTP_NON_PROXY_HOSTS` | Pipe-separated patterns to bypass proxy (e.g. `localhost\|*.internal.com`) |

The `http.nonProxyHosts` Java system property is also respected.

#### JDK HttpClient proxy setup

```java
import io.camunda.connector.http.client.client.jdk.proxy.JdkHttpClientProxyConfigurator;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;

HttpClient client = JdkHttpClientProxyConfigurator.newHttpClient(new ProxyConfiguration());
```
