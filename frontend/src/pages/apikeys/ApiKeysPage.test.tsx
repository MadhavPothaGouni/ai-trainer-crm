import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import * as apiKeysApi from "../../api/apiKeys";
import type { ApiKeyDto, PageResponse } from "../../types/api";
import ApiKeysPage from "./ApiKeysPage";

vi.mock("../../api/apiKeys", () => ({
  listApiKeys: vi.fn(),
  createApiKey: vi.fn(),
  revokeApiKey: vi.fn(),
}));

function page(content: ApiKeyDto[]): PageResponse<ApiKeyDto> {
  return { content, pageNumber: 0, pageSize: 100, totalElements: content.length, totalPages: 1, first: true, last: true };
}

const KEY: ApiKeyDto = {
  id: "k1",
  name: "CI bot",
  keyPrefix: "ak_abc123",
  createdByUserId: "u1",
  lastUsedAt: null,
  expiresAt: null,
  revokedAt: null,
  createdAt: "2026-01-01T00:00:00Z",
};

describe("ApiKeysPage", () => {
  it("lists existing keys without ever showing a raw key", async () => {
    vi.mocked(apiKeysApi.listApiKeys).mockResolvedValue(page([KEY]));
    render(<ApiKeysPage />);

    expect(await screen.findByText("CI bot")).toBeInTheDocument();
    expect(screen.getByText("ak_abc123")).toBeInTheDocument();
    expect(screen.queryByText(/won't be shown again/)).not.toBeInTheDocument();
  });

  it("shows the raw key exactly once after creating one", async () => {
    vi.mocked(apiKeysApi.listApiKeys).mockResolvedValue(page([]));
    vi.mocked(apiKeysApi.createApiKey).mockResolvedValue({ ...KEY, rawKey: "ak_abc123.super-secret-value" });
    const user = userEvent.setup();
    render(<ApiKeysPage />);

    await screen.findByText("No API keys yet.");
    await user.type(screen.getByLabelText("Key name"), "CI bot");
    await user.click(screen.getByRole("button", { name: "Create key" }));

    expect(await screen.findByText("ak_abc123.super-secret-value")).toBeInTheDocument();
    expect(apiKeysApi.createApiKey).toHaveBeenCalledWith({ name: "CI bot" });
  });

  it("revokes a key after confirmation", async () => {
    vi.mocked(apiKeysApi.listApiKeys).mockResolvedValue(page([KEY]));
    vi.mocked(apiKeysApi.revokeApiKey).mockResolvedValue(null);
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const user = userEvent.setup();
    render(<ApiKeysPage />);

    await user.click(await screen.findByRole("button", { name: "Revoke" }));

    await waitFor(() => {
      expect(apiKeysApi.revokeApiKey).toHaveBeenCalledWith("k1");
    });
  });
});
