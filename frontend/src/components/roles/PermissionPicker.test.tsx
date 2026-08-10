import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { PermissionPicker } from "./PermissionPicker";
import type { PermissionDto } from "../../types/api";

const CATALOG: PermissionDto[] = [
  { id: "p1", resource: "LEAD", action: "CREATE", scope: "OWN", description: "", authorityName: "LEAD:CREATE:OWN" },
  { id: "p2", resource: "LEAD", action: "READ", scope: "TEAM", description: "", authorityName: "LEAD:READ:TEAM" },
  { id: "p3", resource: "ACCOUNT", action: "DELETE", scope: "ORGANIZATION", description: "", authorityName: "ACCOUNT:DELETE:ORGANIZATION" },
];

describe("PermissionPicker", () => {
  it("groups permissions by resource", () => {
    render(<PermissionPicker catalog={CATALOG} selectedIds={new Set()} onToggle={vi.fn()} />);
    expect(screen.getByText("LEAD")).toBeInTheDocument();
    expect(screen.getByText("ACCOUNT")).toBeInTheDocument();
    expect(screen.getByText("CREATE:OWN")).toBeInTheDocument();
    expect(screen.getByText("DELETE:ORGANIZATION")).toBeInTheDocument();
  });

  it("checks exactly the permissions in selectedIds", () => {
    render(<PermissionPicker catalog={CATALOG} selectedIds={new Set(["p1", "p3"])} onToggle={vi.fn()} />);
    expect(screen.getByRole("checkbox", { name: "CREATE:OWN" })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: "READ:TEAM" })).not.toBeChecked();
    expect(screen.getByRole("checkbox", { name: "DELETE:ORGANIZATION" })).toBeChecked();
  });

  it("calls onToggle with the permission id when a checkbox is clicked", async () => {
    const onToggle = vi.fn();
    const user = userEvent.setup();
    render(<PermissionPicker catalog={CATALOG} selectedIds={new Set()} onToggle={onToggle} />);

    await user.click(screen.getByRole("checkbox", { name: "READ:TEAM" }));

    expect(onToggle).toHaveBeenCalledWith("p2");
  });

  it("disables every checkbox when disabled is set", () => {
    render(<PermissionPicker catalog={CATALOG} selectedIds={new Set()} onToggle={vi.fn()} disabled />);
    for (const checkbox of screen.getAllByRole("checkbox")) {
      expect(checkbox).toBeDisabled();
    }
  });

  it("shows a fallback message for an empty catalog", () => {
    render(<PermissionPicker catalog={[]} selectedIds={new Set()} onToggle={vi.fn()} />);
    expect(screen.getByText("No permissions available.")).toBeInTheDocument();
  });
});
