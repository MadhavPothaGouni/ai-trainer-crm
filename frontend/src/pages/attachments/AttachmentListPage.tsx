import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listAttachments } from "../../api/attachments";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import { formatFileSize, triggerAttachmentDownload } from "../../lib/attachments";
import type { AttachmentDto, PageResponse } from "../../types/api";

const PAGE_SIZE = 20;

export default function AttachmentListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<AttachmentDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [downloadingId, setDownloadingId] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listAttachments({ page, size: PAGE_SIZE, sort: "createdAt,desc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load attachments.");
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [page]);

  async function handleDownload(attachment: AttachmentDto) {
    setDownloadingId(attachment.id);
    setError(null);
    try {
      await triggerAttachmentDownload(attachment);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not download this file.");
    } finally {
      setDownloadingId(null);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Attachments</h1>
          <p className="mt-1 text-sm text-slate-500">Files uploaded against an Account, Contact, Opportunity, Lead, or Ticket.</p>
        </div>
        <Link to="/attachments/new">
          <Button>Upload file</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">File</th>
              <th className="px-4 py-3 font-medium">Related to</th>
              <th className="px-4 py-3 font-medium">Size</th>
              <th className="px-4 py-3 font-medium" />
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
                  No attachments yet.
                </td>
              </tr>
            )}
            {result?.content.map((attachment) => (
              <tr key={attachment.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/attachments/${attachment.id}`} className="font-medium text-slate-900 hover:underline">
                    {attachment.fileName}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-500">
                  {attachment.relatedToType[0]}
                  {attachment.relatedToType.slice(1).toLowerCase()}
                </td>
                <td className="px-4 py-3 text-slate-500">{formatFileSize(attachment.fileSizeBytes)}</td>
                <td className="px-4 py-3 text-right">
                  <button
                    type="button"
                    onClick={() => void handleDownload(attachment)}
                    disabled={downloadingId === attachment.id}
                    className="text-xs font-medium text-slate-500 hover:text-slate-900 hover:underline disabled:opacity-50"
                  >
                    {downloadingId === attachment.id ? "Downloading..." : "Download"}
                  </button>
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
