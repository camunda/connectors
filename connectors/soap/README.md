# SOAP Connector

## Define SOAP Message

### HTTP

The HTTP connection can be configured just like in the http-json-connector.

Input fields:

* `serviceUrl`: The URL where the service runs
* `connectionTimeoutInSeconds`: Sets the timeout in seconds to establish a connection or 0 for an infinite timeout

### SOAP Envelope

The SOAP Envelope is generated from a template, additional namespaces are added to the envelope. Also, the SOAP version can be configured.

Input fields:

* `namespaces`: The namespaces that should be declared on the SOAP Envelope
* `soapVersion`: The SOAP version the service uses
* `soapVersion.soapAction`: The SOAPAction HTTP header to be used in the request, applies to SOAP 1.1 only

### SOAP Header

The SOAP Header can be extended by headers defined in FEEL.

The input can be a template string plus context or a xml-compatible json structure (defined in feel)

### SOAP Body

The SOAP Body is templated using mustache with a body template and a context that is mapped from the process variables.

The input can be a template string plus context or a xml-compatible json structure (defined in feel)

### Authentication

All current authentication mechanisms are:

* WSS username token
* WSS signature
* None

### Examples

#### Example 1

For example, you would like to send a following SOAP request:

URL: `https://myservice:8888/webservice.wso`

Body:

```xml
<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Body>
    <Object01>
      <Object02>12345</Object02>
    </Object01>
  </soap:Body>
</soap:Envelope>
```
In order to do so, in your BPMN diagram, set the field **Service URL** as `https://myservice:8888/webservice.wso`, and **SOAP body** as

```json
{
  "Object01": {
    "Object02": 12345
  }
}
```

#### Example 2: pre-defined namespaces

Consider a namespace is defined within your objects, and you wish to send the following request:

URL: `https://myservice:8888/webservice.wso`

Body:

```xml
<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Body>
    <Object01 mlns="http://www.my.namespace.com/namespace/">
      <Object02>12345</Object02>
    </Object01>
  </soap:Body>
</soap:Envelope>
```

In order to do so, in your BPMN diagram, set the field **Service URL** as `https://myservice:8888/webservice.wso`, and **SOAP body** as

```json
{
  "ns:Object01": {
    "ns:Object02": 12345
  }
}
```

Please, pay attention, that here we introduced a new `ns:` prefix. Prefix, can be any arbitrary string, that is not defined as namespace.

Now, you'll need to associate a namespace. You can do it by setting the following value at the **Namespaces** field.
For the given example, it should be set as:

```json
{
  "ns": "http://www.my.namespace.com/namespace/"
}
```

#### Example 3: using templates

As an alternative, you can use templates to send SOAP messages.

URL: `https://myservice:8888/webservice.wso`

Body:

```xml
<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Body>
    <Object01>
      <Object02>12345</Object02>
    </Object01>
  </soap:Body>
</soap:Envelope>
```

For that, set the **SOAP body** dropdown to **Template**.

In the **XML template** field define the template, for example as:

```xml
<Object01>
  <Object02>{{myObjectValue}}</Object02>
</Object01>
```

In the **XML template context** field define context JSON, for example:

```json
{
  myObjectValue: 12345
}
```

### Running

1. Build or download 'fat' jar.
2. Run as a custom Connector.

Please refer the [running custom Connector guide](https://docs.camunda.io/docs/guides/host-custom-connectors/) page.
