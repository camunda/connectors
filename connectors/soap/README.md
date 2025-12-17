# SOAP Connector

## Connection

Enter your SOAP service URL in the field **Service URL**, for example `https://myservice.com/service/MyService.wso`.

## Authentication

Select the authentication type from the **Authentication** dropdown.

### None

Use **None** if the SOAP service does not require authentication.

### WSS username token

Use **WSS username token** in the **Authentication** dropdown when the requested SOAP endpoint requires
[username token extension](https://docs.oasis-open.org/wss/v1.1/wss-v1.1-spec-pr-UsernameTokenProfile-01.htm#_Toc104276211).

Enter **Username**, **Password**, and indicate if the password is encoded.

:::note
The **SOAP connector** currently supports only `SHA-1` password encoding.
:::

### WSS signature

Use the **WSS signature** in the **Authentication** dropdown when the requested SOAP endpoint requires a message to be
cryptographically signed with a [signature](http://docs.oasis-open.org/wss-m/wss/v1.1.1/cs01/wss-SOAPMessageSecurity-v1.1.1-cs01.html#_Toc307407954).

Enter all necessary fields according to your service specification:

- **Certificate type**: From where the certificate is obtained.
  - **Single certificate**: A single certificate can be specified.
    - **Certificate**: The X.509 certificate to use to sign the request.
    - **Private key**: The private key for the certificate.
  - **Keystore certificate**: The certificate is stored in a keystore file.
    - **Keystore location**: Path to the keystore file. For that a keystore file needs to be mounted to the connector container on the specified path.
    - **Keystore password**: Password of the keystore.
    - **Certificate alias**: Which certificate from the keystore should be used to sign the SOAP message
    - **Certificate password**: Password of the certificate.
- **Signature algorithm**: The signature algorithm to use. The default is set by the data in the certificate.
- **Digest algorithm**: The signature digest algorithm to use. The default is SHA-1.
- **Timestamp timeout in seconds**: Adds a timestamp to the SOAP header, which is valid for the given time in seconds.
- **Signature parts**: Defines which parts of the SOAP message should be signed. If no signature parts are specified, the SOAP body is signed by default.

## SOAP message

### SOAP version

Select the desired version of the SOAP service.

### SOAPAction HTTP header

Enter the SOAPAction HTTP header that will be used in the request. Leave this value blank if the SOAPAction HTTP header
won't be used in your request. This field is only required by SOAP version 1.1.

### SOAP header

From the dropdown, select whether the **SOAP header** is required, and if so, in which format you wish to provide it.

### SOAP body

From the **SOAP body** dropdown, select whether you will provide the SOAP request body in a form of **Template**, or
**XML compatible JSON**.

#### Template

When **Template** is chosen, enter the **XML template** value, for example `<camunda:Param><camunda:ParamType>{{paramValue}}</camunda:ParamType></camunda:Param>`.

Enter the **XML template context** value, for example `={paramValue: 1234567890}`, and enter the **Namespaces** value, for example `={"camunda":"http://my.service.com/webservicesserver/"}`.

#### XML compatible JSON

When **XML compatible JSON** is chosen, enter the **JSON definition**, for example

```json
= {
  "camunda:Object01": {
    "camunda:Object02": myObjectValue
  }
}
```

Enter the **Namespaces** value, for example `={"camunda":"http://my.service.com/webservicesserver/"}`.

### Timeout
The **Connection timeout in seconds** determines the time frame in which the client will try to establish a connection with the server. If you do not specify a value, the system uses the default of 20 seconds. For cases where you need to wait indefinitely, set this value to 0.

## Output mapping

### Result variable

You can export a complete response from a SOAP call into a dedicated variable accessible anywhere in a process.
To do so, input a variable name in the **Result variable** field. Use a unique name to avoid
overwriting variables.

A typical response may look like as follows:

```json
{
  "Envelope": {
    "Header": {
      "MyHeader": "Header value"
    },
    "Body": {
      "MyResponseObject": {
        "MyResponseObjectField": "My result value"
      }
    }
  }
}
```

### Result expression

Additionally, you can choose to unpack the content of your `response` into multiple process variables using the **Result expression**, which is a [FEEL Context Expression](/components/modeler/feel/language-guide/feel-context-expressions.md).

Given SOAP service response that looks like as follows:

```json
{
  "Envelope": {
    "Header": {
      "MyHeader": "Header value"
    },
    "Body": {
      "MyResponseObject": {
        "MyResponseObjectField": "My result value"
      }
    }
  }
}
```

To extract the `MyResponseObjectField` value into its own variable, you can do:

```
= {
    MyResponseObjectResult: response.Envelope.Body.MyResponseObject.MyResponseObjectField
}
```

## Usage examples

### Example 1

For example, imagine you want to send the following SOAP request:

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

In your BPMN diagram, set the field **Service URL** as `https://myservice:8888/webservice.wso`, and **SOAP body** as:

```json
{
  "Object01": {
    "Object02": 12345
  }
}
```

### Example 2: Pre-defined namespaces

Consider a namespace is defined within your objects, and you want to send the following request:

URL: `https://myservice:8888/webservice.wso`

Body:

```xml
<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Body>
    <Object01 xmlns="http://www.my.namespace.com/namespace/">
      <Object02>12345</Object02>
    </Object01>
  </soap:Body>
</soap:Envelope>
```

In your BPMN diagram, set the field **Service URL** as `https://myservice:8888/webservice.wso`, and **SOAP body** as:

```json
{
  "ns:Object01": {
    "ns:Object02": 12345
  }
}
```

:::note
Here, we introduced a new `ns:` prefix. The prefix can be any arbitrary string that is not defined as a namespace.
:::

Now, you'll need to associate a namespace. Set the following value at the **Namespaces** field.
For the given example, it should be set as:

```json
{
  "ns": "http://www.my.namespace.com/namespace/"
}
```

### Example 3: Using templates

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

Set the **SOAP body** dropdown to **Template**.

In the **XML template** field, define the template. For example:

```xml
<Object01>
  <Object02>{{myObjectValue}}</Object02>
</Object01>
```

In the **XML template context** field, define context JSON. For example:

```json
{
  "myObjectValue": 12345
}
```

### Example 4: Using WSS signature with keystore
The below tables shows an example on how to fill in the properties for signing the SOAP message with a WSS signature using a keystore certificate.

<table>
<tr>
<td>

**Property**
</td>
<td>

**Example**
</td>
</tr>
<tr>
<td>Authentication</td>
<td>WSS signature</td>
</tr>
<tr>
<td>Certificate type</td>
<td>Keystore certificate</td>
</tr>
<tr>
<td>Keystore location</td>
<td>/opt/certificates/keystore.jks</td>
</tr>
<tr>
<td>Keystore password</td>
<td>SuperSecretKeystorePassword</td>
</tr>
<tr>
<td>Certificate alias</td>
<td>mycert</td>
</tr>
<tr>
<td>Certificate password</td>
<td>SuperSecretCertificatePassword</td>
</tr>
<tr>
<td>Signature algorithm</td>
<td></td>
</tr>
<tr>
<td>Digest algorithm</td>
<td></td>
</tr>
<tr>
<td>Timestamp timeout in seconds</td>
<td>SuperSecretKeystorePassword</td>
</tr>
<tr>
<td>Signature parts</td>
<td>

```json 
[
    {
      "namespace": "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd",
      "localName": "UsernameToken"
    },
    {
      "namespace": "http://contracts.company.com/services/AccountFulfilment",
      "localName": "getAccountRequest"
    }
]
```
</td>
</tr>
</table>


## Running

1. Build or download 'fat' jar.
2. Run as a custom Connector.

Please refer the [running custom Connector guide](https://docs.camunda.io/docs/guides/host-custom-connectors/) page.
