import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import * as activitiesApi from "../../api/activities";
import type { ActivityDto, PageResponse } from "../../types/api";
import { ActivityTimeline } from "./ActivityTimeline";

vi.mock("../../api/activities", () => ({
  listActivities: vi.fn(),
  createActivity: vi.fn(),
  updateActivityStatus: vi.fn(),
  deleteActivity: vi.fn(),
}));

function page(content: ActivityDto[]): PageResponse<ActivityDto> {
  return { content, pageNumber: 0, pageSize: 50, totalElements: content.length, totalPages: 1, first: true, last: true };
}

const TASK: ActivityDto = {
  id: "a1",
  type: "TASK",
  subject: "Call about renewal",
  description: null,
  status: "OPEN",
  priority: "HIGH",
  dueAt: null,
  completedAt: null,
  relatedToType: "ACCOUNT",
  relatedToId: "acc1",
  ownerId: "u1",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

describe("ActivityTimeline", () => {
  it("loads and renders activities for the given record", async () => {
    vi.mocked(activitiesApi.listActivities).mockResolvedValue(page([TASK]));
    render(<ActivityTimeline relatedToType="ACCOUNT" relatedToId="acc1" />);

    expect(await screen.findByText("Call about renewal")).toBeInTheDocument();
    expect(activitiesApi.listActivities).toHaveBeenCalledWith(
      expect.objectContaining({ relatedToType: "ACCOUNT", relatedToId: "acc1" }),
    );
  });

  it("shows a fallback message when there's nothing logged yet", async () => {
    vi.mocked(activitiesApi.listActivities).mockResolvedValue(page([]));
    render(<ActivityTimeline relatedToType="ACCOUNT" relatedToId="acc1" />);

    expect(await screen.findByText("Nothing logged yet.")).toBeInTheDocument();
  });

  it("completes an open task and reloads", async () => {
    vi.mocked(activitiesApi.listActivities).mockResolvedValue(page([TASK]));
    vi.mocked(activitiesApi.updateActivityStatus).mockResolvedValue({ ...TASK, status: "COMPLETED" });
    const user = userEvent.setup();
    render(<ActivityTimeline relatedToType="ACCOUNT" relatedToId="acc1" />);

    await user.click(await screen.findByRole("button", { name: "Complete" }));

    await waitFor(() => {
      expect(activitiesApi.updateActivityStatus).toHaveBeenCalledWith("a1", { status: "COMPLETED" });
    });
  });

  it("logs a new activity from the inline form", async () => {
    vi.mocked(activitiesApi.listActivities).mockResolvedValue(page([]));
    vi.mocked(activitiesApi.createActivity).mockResolvedValue(TASK);
    const user = userEvent.setup();
    render(<ActivityTimeline relatedToType="ACCOUNT" relatedToId="acc1" />);

    await user.click(await screen.findByRole("button", { name: "Log activity" }));
    await user.type(screen.getByLabelText("Subject"), "Send proposal");
    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      expect(activitiesApi.createActivity).toHaveBeenCalledWith(
        expect.objectContaining({ subject: "Send proposal", relatedToType: "ACCOUNT", relatedToId: "acc1" }),
      );
    });
  });

  it("deletes an activity after confirmation", async () => {
    vi.mocked(activitiesApi.listActivities).mockResolvedValue(page([TASK]));
    vi.mocked(activitiesApi.deleteActivity).mockResolvedValue(null);
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const user = userEvent.setup();
    render(<ActivityTimeline relatedToType="ACCOUNT" relatedToId="acc1" />);

    await user.click(await screen.findByRole("button", { name: "Delete" }));

    await waitFor(() => {
      expect(activitiesApi.deleteActivity).toHaveBeenCalledWith("a1");
    });
  });
});
