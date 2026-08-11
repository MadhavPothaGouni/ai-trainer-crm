import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import * as reportsApi from "../../api/reports";
import { ApiError } from "../../lib/apiClient";
import type { LeadFunnelStageDto, PipelineStageSummaryDto, RepLeaderboardEntryDto } from "../../types/api";
import ReportsPage from "./ReportsPage";

vi.mock("../../api/reports", () => ({
  getPipelineByStage: vi.fn(),
  getLeadFunnel: vi.fn(),
  getLeaderboard: vi.fn(),
}));

const PIPELINE: PipelineStageSummaryDto[] = [
  { stage: "PROSPECTING", opportunityCount: 1, totalAmount: 1000 },
  { stage: "QUALIFICATION", opportunityCount: 0, totalAmount: 0 },
  { stage: "PROPOSAL", opportunityCount: 0, totalAmount: 0 },
  { stage: "NEGOTIATION", opportunityCount: 0, totalAmount: 0 },
  { stage: "CLOSED_WON", opportunityCount: 1, totalAmount: 2000 },
  { stage: "CLOSED_LOST", opportunityCount: 0, totalAmount: 0 },
];

const FUNNEL: LeadFunnelStageDto[] = [
  { status: "NEW", leadCount: 2 },
  { status: "CONTACTED", leadCount: 1 },
  { status: "QUALIFIED", leadCount: 0 },
  { status: "UNQUALIFIED", leadCount: 0 },
  { status: "CONVERTED", leadCount: 0 },
];

const LEADERBOARD: RepLeaderboardEntryDto[] = [
  { ownerId: "u1", ownerName: "Ada Lovelace", openCount: 1, openAmount: 1000, wonCount: 1, wonAmount: 2000, lostCount: 0 },
];

describe("ReportsPage", () => {
  it("loads and renders pipeline, funnel, and leaderboard data", async () => {
    vi.mocked(reportsApi.getPipelineByStage).mockResolvedValue(PIPELINE);
    vi.mocked(reportsApi.getLeadFunnel).mockResolvedValue(FUNNEL);
    vi.mocked(reportsApi.getLeaderboard).mockResolvedValue(LEADERBOARD);

    render(<ReportsPage />);

    expect(await screen.findByText("Ada Lovelace")).toBeInTheDocument();
    expect(screen.getByText("Pipeline by stage")).toBeInTheDocument();
    expect(screen.getByText("Lead conversion funnel")).toBeInTheDocument();
    expect(screen.getByText("Rep leaderboard")).toBeInTheDocument();
  });

  it("shows a fallback message when nobody owns an opportunity yet", async () => {
    vi.mocked(reportsApi.getPipelineByStage).mockResolvedValue(PIPELINE);
    vi.mocked(reportsApi.getLeadFunnel).mockResolvedValue(FUNNEL);
    vi.mocked(reportsApi.getLeaderboard).mockResolvedValue([]);

    render(<ReportsPage />);

    expect(await screen.findByText("No opportunities owned by anyone yet.")).toBeInTheDocument();
  });

  it("shows a permission-specific message on a 403", async () => {
    vi.mocked(reportsApi.getPipelineByStage).mockRejectedValue(new ApiError({ message: "Forbidden" }, 403));
    vi.mocked(reportsApi.getLeadFunnel).mockResolvedValue(FUNNEL);
    vi.mocked(reportsApi.getLeaderboard).mockResolvedValue(LEADERBOARD);

    render(<ReportsPage />);

    expect(await screen.findByText(/don't have access to reports/)).toBeInTheDocument();
  });
});
