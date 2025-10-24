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
