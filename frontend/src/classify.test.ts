import { describe, expect, it } from "vitest";
import { isConflictOutcome, outcomeHint, outcomeLabel, outcomeTone } from "./classify";

describe("classify", () => {
  it("labels a row by the action reconcile will take", () => {
    expect(outcomeLabel("CLEAN")).toBe("In sync");
    expect(outcomeLabel("SHEET_ONLY")).toBe("Sheet → database");
    expect(outcomeLabel("CONFLICT")).toBe("Needs a choice");
    expect(outcomeHint("AUTO_MERGED")).toContain("different field");
    expect(outcomeTone("AUTO_MERGED")).toBe("merge");
    expect(isConflictOutcome("DELETE_CONFLICT")).toBe(true);
    expect(isConflictOutcome("CLEAN")).toBe(false);
  });
});
