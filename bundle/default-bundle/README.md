Run via Maven / IDE:

Run `io.camunda.coonnector.runtime.app.LocalConnectorRuntime` class via your favorite IDE.
You can add an `application.properties` file to `src/test/resources` containing your configurations.

Run via command line

```bash
# java \
  -Dzeebe.client.cloud.region=bru-2 \
  -Dzeebe.client.cloud.clusterId=xxx \
  -Dzeebe.client.cloud.clientId=yyy \
  -Dzeebe.client.cloud.clientSecret=zzz \
  -jar target/connector-runtime-bundle-{VERSION}-with-dependencies.jar
```
