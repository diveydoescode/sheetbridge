import { describe, expect, it } from "vitest";
import { threeWayMerge } from "./merge";

const COLS = ["sku", "quantity", "price", "notes"];

function row(sku: string, qty: string, price: string, notes: string) {
  return { sku, quantity: qty, price, notes };
}

describe("threeWayMerge", () => {
  it("classifies identical rows as clean", () => {
    const r = row("SKU-1", "10", "5.00", "ok");
    expect(threeWayMerge("SKU-1", "sku", COLS, r, r, r).outcome).toBe("CLEAN");
  });

  it("applies a sheet-only change", () => {
    const snap = row("SKU-1", "10", "5.00", "ok");
    const sheet = row("SKU-1", "7", "5.00", "ok");
    const result = threeWayMerge("SKU-1", "sku", COLS, snap, sheet, snap);
    expect(result.outcome).toBe("SHEET_ONLY");
    expect(result.mergedPayload?.quantity).toBe("7");
  });

  it("auto-merges disjoint field edits", () => {
    const snap = row("SKU-1", "10", "5.00", "ok");
    const sheet = row("SKU-1", "7", "5.00", "ok");
    const db = row("SKU-1", "10", "6.50", "ok");
    const result = threeWayMerge("SKU-1", "sku", COLS, snap, sheet, db);
    expect(result.outcome).toBe("AUTO_MERGED");
    expect(result.mergedPayload).toEqual({ sku: "SKU-1", quantity: "7", price: "6.50", notes: "ok" });
  });

  it("conflicts when the same field diverges", () => {
    const snap = row("SKU-1", "10", "5.00", "ok");
    const result = threeWayMerge(
      "SKU-1",
      "sku",
      COLS,
      snap,
      row("SKU-1", "7", "5.00", "ok"),
      row("SKU-1", "12", "5.00", "ok"),
    );
    expect(result.outcome).toBe("CONFLICT");
    expect(result.conflictingFields).toEqual(["quantity"]);
  });
});
