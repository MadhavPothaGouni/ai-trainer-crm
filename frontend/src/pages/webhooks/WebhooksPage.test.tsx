import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import * as webhooksApi from "../../api/webhooks";
import type { PageResponse, WebhookSubscriptionDto } from "../../types/api";
import WebhooksPage from "./WebhooksPage";

vi.mock("../../api/webhooks", () => ({
  listWebhooks: vi.fn(),
  createWebhook: vi.fn(),
  updateWebhook: vi.fn(),
  deleteWebhook: vi.fn(),
}));

function page(content: WebhookSubscriptionDto[]): PageResponse<WebhookSubscriptionDto> {
  return { content, pageNumber: 0, pageSize: 100, totalElements: content.length, totalPages: 1, first: true, last: true };
}

const WEBHOOK: WebhookSubscriptionDto = {
  id: "w1",
  url: "https://example.com/hooks/crm",
  eventType: null,
  secret: "whsec_abc123",
  active: true,
  createdByUserId: "u1",
  lastTriggeredAt: null,
  lastResponseStatus: null,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

describe("WebhooksPage", () => {
  it("lists existing subscriptions with their signing secret", async () => {
    vi.mocked(webhooksApi.listWebhooks).mockResolvedValue(page([WEBHOOK]));
    render(<WebhooksPage />);

    expect(await screen.findByText("https://example.com/hooks/crm")).toBeInTheDocument();
    expect(screen.getByText("whsec_abc123")).toBeInTheDocument();
    expect(screen.getByText("All events")).toBeInTheDocument();
  });

  it("creates a new subscription from the inline form", async () => {
    vi.mocked(webhooksApi.listWebhooks).mockResolvedValue(page([]));
    vi.mocked(webhooksApi.createWebhook).mockResolvedValue(WEBHOOK);
    const user = userEvent.setup();
    render(<WebhooksPage />);

    await screen.findByText("No webhooks yet.");
    await user.type(screen.getByLabelText("Endpoint URL"), "https://example.com/hooks/crm");
    await user.click(screen.getByRole("button", { name: "Add webhook" }));

    await waitFor(() => {
      expect(webhooksApi.createWebhook).toHaveBeenCalledWith({ url: "https://example.com/hooks/crm", eventType: null });
    });
  });

  it("pauses an active subscription", async () => {
    vi.mocked(webhooksApi.listWebhooks).mockResolvedValue(page([WEBHOOK]));
    vi.mocked(webhooksApi.updateWebhook).mockResolvedValue({ ...WEBHOOK, active: false });
    const user = userEvent.setup();
    render(<WebhooksPage />);

    await user.click(await screen.findByRole("button", { name: "Pause" }));

    await waitFor(() => {
      expect(webhooksApi.updateWebhook).toHaveBeenCalledWith("w1", { url: WEBHOOK.url, eventType: null, active: false });
    });
  });

  it("deletes a subscription after confirmation", async () => {
    vi.mocked(webhooksApi.listWebhooks).mockResolvedValue(page([WEBHOOK]));
    vi.mocked(webhooksApi.deleteWebhook).mockResolvedValue(null);
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const user = userEvent.setup();
    render(<WebhooksPage />);

    await user.click(await screen.findByRole("button", { name: "Delete" }));

    await waitFor(() => {
      expect(webhooksApi.deleteWebhook).toHaveBeenCalledWith("w1");
    });
  });
});
