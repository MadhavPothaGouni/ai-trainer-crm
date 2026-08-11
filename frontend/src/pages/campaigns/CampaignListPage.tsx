import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { exportCampaignsCsv, listCampaigns } from "../../api/campaigns";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { CampaignDto, CampaignStatus, PageResponse } from "../../types/api";

const PAGE_SIZE = 20;

const STATUS_CLASSES: Record<CampaignStatus, string> = {
  PLANNED: "bg-slate-100 text-slate-700",
  ACTIVE: "bg-blue-100 text-blue-700",
  COMPLETED: "bg-emerald-100 text-emerald-700",
  CANCELLED: "bg-red-100 text-red-700",
};

export function CampaignStatusBadge({ status }: { status: CampaignStatus }) {
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASSES[status]}`}>{status}</span>;
}

export default function CampaignListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<CampaignDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isExporting, setIsExporting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listCampaigns({ page, size: PAGE_SIZE, sort: "createdAt,desc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load campaigns.");
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [page]);

  async function handleExport() {
    setIsExporting(true);
    setError(null);
    try {
      await exportCampaignsCsv();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not export campaigns.");
    } finally {
      setIsExporting(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Campaigns</h1>
          <p className="mt-1 text-sm text-slate-500">Marketing campaigns and who's engaging with them.</p>
        </div>
        <div className="flex gap-3">
          <Button variant="secondary" onClick={() => void handleExport()} isLoading={isExporting}>
            Export CSV
          </Button>
          <Link to="/campaigns/new">
            <Button>New campaign</Button>
          </Link>
        </div>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Name</th>
              <th className="px-4 py-3 font-medium">Type</th>
              <th className="px-4 py-3 font-medium">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={3}>
                  Loading...
                </td>
              </tr>
            )}
            {!isLoading && result?.content.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={3}>
                  No campaigns yet.
                </td>
              </tr>
            )}
            {result?.content.map((campaign) => (
              <tr key={campaign.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/campaigns/${campaign.id}`} className="font-medium text-slate-900 hover:underline">
                    {campaign.name}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">{campaign.type.replace("_", " ")}</td>
                <td className="px-4 py-3">
                  <CampaignStatusBadge status={campaign.status} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {result && (
        <Pagination
          pageNumber={result.pageNumber}
          totalPages={result.totalPages}
          first={result.first}
          last={result.last}
          totalElements={result.totalElements}
          onPageChange={setPage}
        />
      )}
    </div>
  );
}
