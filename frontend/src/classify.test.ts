import { describe, expect, it } from "vitest";
import { isConflictOutcome, outcomeLabel, outcomeTone } from "./classify";

describe("classify", () => {
  it("labels three-way outcomes for the queue", () => {
    expect(outcomeLabel("CLEAN")).toBe("In sync");
    expect(outcomeLabel("SHEET_ONLY")).toBe("Sheet only");
    expect(outcomeLabel("CONFLICT")).toBe("Conflict");
    expect(outcomeTone("AUTO_MERGED")).toBe("merge");
    expect(isConflictOutcome("DELETE_CONFLICT")).toBe(true);
    expect(isConflictOutcome("CLEAN")).toBe(false);
  });
});
