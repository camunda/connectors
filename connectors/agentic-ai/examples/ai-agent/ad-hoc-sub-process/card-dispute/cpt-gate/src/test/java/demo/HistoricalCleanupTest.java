package demo;

import io.camunda.client.CamundaClient;
import java.net.URI;
import org.junit.jupiter.api.Test;

class HistoricalCleanupTest {
  @Test void deleteOnlyTheThreeExplicitlyAuthorizedGateOwners() throws Exception {
    try (var client = CamundaClient.newClientBuilder().restAddress(URI.create("http://localhost:8280"))
        .grpcAddress(URI.create("http://localhost:26710")).preferRestOverGrpc(true)
        .credentialsProvider(io.camunda.client.CredentialsProvider.newBasicAuthCredentialsProviderBuilder()
            .username("demo").password("demo").build())
        .build()) {
      var cleanup = new SandboxCleanup(client, new Api("http://localhost:8280", true));
      cleanup.deploy();
      for (long owner : new long[] {2251799813691161L, 2251799813691492L, 2251799813691862L}) {
        cleanup.deleteOwned(owner);
      }
    }
  }
}
