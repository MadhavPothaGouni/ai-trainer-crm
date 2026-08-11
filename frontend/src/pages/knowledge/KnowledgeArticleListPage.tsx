import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { exportKnowledgeArticlesCsv, listKnowledgeArticles } from "../../api/knowledgeArticles";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { KnowledgeArticleDto, KnowledgeArticleStatus, PageResponse } from "../../types/api";

const PAGE_SIZE = 20;

const STATUS_CLASSES: Record<KnowledgeArticleStatus, string> = {
  DRAFT: "bg-slate-100 text-slate-700",
  PUBLISHED: "bg-emerald-100 text-emerald-700",
  ARCHIVED: "bg-red-100 text-red-700",
};

export function KnowledgeArticleStatusBadge({ status }: { status: KnowledgeArticleStatus }) {
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASSES[status]}`}>{status}</span>;
}

export default function KnowledgeArticleListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<KnowledgeArticleDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isExporting, setIsExporting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listKnowledgeArticles({ page, size: PAGE_SIZE, sort: "createdAt,desc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load articles.");
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
      await exportKnowledgeArticlesCsv();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not export articles.");
    } finally {
      setIsExporting(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Knowledge base</h1>
          <p className="mt-1 text-sm text-slate-500">Support/help-center articles.</p>
        </div>
        <div className="flex gap-3">
          <Button variant="secondary" onClick={() => void handleExport()} isLoading={isExporting}>
            Export CSV
          </Button>
          <Link to="/knowledge-articles/new">
            <Button>New article</Button>
          </Link>
        </div>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Title</th>
              <th className="px-4 py-3 font-medium">Category</th>
              <th className="px-4 py-3 font-medium">Views</th>
              <th className="px-4 py-3 font-medium">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={4}>
                  Loading...
                </td>
              </tr>
            )}
            {!isLoading && result?.content.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={4}>
                  No articles yet.
                </td>
              </tr>
            )}
            {result?.content.map((article) => (
              <tr key={article.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/knowledge-articles/${article.id}`} className="font-medium text-slate-900 hover:underline">
                    {article.title}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">{article.category ?? "—"}</td>
                <td className="px-4 py-3 text-slate-600">{article.viewCount.toLocaleString()}</td>
                <td className="px-4 py-3">
                  <KnowledgeArticleStatusBadge status={article.status} />
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
