import assert from "assert";
import allConnectors from "../src/connectors.js";

describe("OOTBConnectorsStore JSON validity", () => {
  it("should have at least 30 JSON connectors", () => {
    const connectors = allConnectors;
    assert.ok(connectors.length > 30);
  });

  it("should have valid JSON connectors", () => {
    const connectors = allConnectors;

    connectors.forEach((connector) => {
      assert.strictEqual(typeof connector, "object");
      assert.notStrictEqual(connector, null);
      assert.doesNotThrow(() => JSON.stringify(connector));
    });
  });
});
