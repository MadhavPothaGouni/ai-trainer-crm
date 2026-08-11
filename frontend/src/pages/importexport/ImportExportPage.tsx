import { useEffect, useRef, useState } from "react";
import { exportEntities, getImportJob, importEntities, listImportJobs } from "../../api/importExport";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { Select } from "../../components/ui/Select";
import { ApiError } from "../../lib/apiClient";
import { IMPORT_ENTITY_TYPES, type ImportEntityType, type ImportJobDto, type PageResponse } from "../../types/api";

const ENTITY_LABELS: Record<ImportEntityType, string> = {
  ACCOUNT: "Accounts",
  CONTACT: "Contacts",
  LEAD: "Leads",
};

const PAGE_SIZE = 10;

/**
 * A single page covering all three IMPORT/EXPORT-implemented entities rather than three near-
 * identical pages, since the only thing that changes between Account/Contact/Lead is which
 * endpoint gets called (see api/importExport.ts's per-entity path maps) - everything else about
 * the export-a-file / upload-a-file / see-what-happened flow is identical.
 */
export default function ImportExportPage() {
  const [entityType, setEntityType] = useState<ImportEntityType>("ACCOUNT");
  const [isExporting, setIsExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [isImporting, setIsImporting] = useState(false);
  const [importError, setImportError] = useState<string | null>(null);
  const [lastJob, setLastJob] = useState<ImportJobDto | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [historyPage, setHistoryPage] = useState(0);
  const [history, setHistory] = useState<PageResponse<ImportJobDto> | null>(null);
  const [historyError, setHistoryError] = useState<string | null>(null);

  function loadHistory() {
    listImportJobs({ page: historyPage, size: PAGE_SIZE, sort: "createdAt,desc" })
      .then(setHistory)
      .catch((err: unknown) => setHistoryError(err instanceof ApiError ? err.message : "Could not load import history."));
  }

  useEffect(() => {
    loadHistory();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [historyPage]);

  async function handleExport() {
    setExportError(null);
    setIsExporting(true);
    try {
      await exportEntities(entityType);
    } catch (err) {
      setExportError(err instanceof ApiError ? err.message : "Could not export this file.");
    } finally {
      setIsExporting(false);
    }
  }

  async function handleImport() {
    if (!selectedFile) return;
    setImportError(null);
    setIsImporting(true);
    try {
      const job = await importEntities(entityType, selectedFile);
      setLastJob(job);
      setSelectedFile(null);
      if (fileInputRef.current) fileInputRef.current.value = "";
      loadHistory();
    } catch (err) {
      setImportError(err instanceof ApiError ? err.message : "Could not import this file.");
    } finally {
      setIsImporting(false);
    }
  }

  async function viewJob(jobId: string) {
    try {
      setLastJob(await getImportJob(jobId));
    } catch (err) {
      setHistoryError(err instanceof ApiError ? err.message : "Could not load that import job.");
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Import / Export</h1>
        <p className="mt-1 text-sm text-slate-500">Bulk CSV import and export for Accounts, Contacts, and Leads.</p>
      </div>

      <Select
        label="Entity"
        options={IMPORT_ENTITY_TYPES.map((type) => ({ value: type, label: ENTITY_LABELS[type] }))}
        value={entityType}
        onChange={(e) => setEntityType(e.target.value as ImportEntityType)}
        className="max-w-xs"
      />

      <div className="grid gap-6 md:grid-cols-2">
        <div className="flex flex-col gap-3 rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-semibold text-slate-900">Export {ENTITY_LABELS[entityType].toLowerCase()}</h2>
          <p className="text-sm text-slate-500">Downloads every {ENTITY_LABELS[entityType].toLowerCase()} record you can see as a CSV file.</p>
          {exportError && <Alert variant="error">{exportError}</Alert>}
          <Button onClick={() => void handleExport()} isLoading={isExporting} className="self-start">
            Export CSV
          </Button>
        </div>

        <div className="flex flex-col gap-3 rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-semibold text-slate-900">Import {ENTITY_LABELS[entityType].toLowerCase()}</h2>
          <p className="text-sm text-slate-500">
            Upload a CSV exported from here (or matching its columns). Rows are processed independently - a bad row
            doesn't stop the rest.
          </p>
          {importError && <Alert variant="error">{importError}</Alert>}
          <input
            ref={fileInputRef}
            type="file"
            accept=".csv,text/csv"
            onChange={(e) => setSelectedFile(e.target.files?.[0] ?? null)}
            className="text-sm text-slate-600 file:mr-3 file:rounded-md file:border-0 file:bg-slate-100 file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-slate-700 hover:file:bg-slate-200"
          />
          <Button onClick={() => void handleImport()} isLoading={isImporting} disabled={!selectedFile} className="self-start">
            Import CSV
          </Button>
        </div>
      </div>

      {lastJob && (
        <div className="flex flex-col gap-3 rounded-lg border border-slate-200 bg-white p-5">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold text-slate-900">
              {ENTITY_LABELS[lastJob.entityType]} import - {lastJob.status === "COMPLETED" ? "Completed" : "Failed"}
            </h2>
            <span className="text-xs text-slate-400">{new Date(lastJob.createdAt).toLocaleString()}</span>
          </div>
          <div className="flex gap-6 text-sm text-slate-600">
            <span>Total rows: {lastJob.totalRows}</span>
            <span className="text-emerald-700">Succeeded: {lastJob.successCount}</span>
            <span className="text-red-700">Failed: {lastJob.errorCount}</span>
          </div>
          {lastJob.errors.length > 0 && (
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="py-2 pr-4 font-medium">Row</th>
                  <th className="py-2 font-medium">Error</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {lastJob.errors.map((rowError) => (
                  <tr key={rowError.rowNumber}>
                    <td className="py-2 pr-4 text-slate-500">{rowError.rowNumber === 0 ? "—" : rowError.rowNumber}</td>
                    <td className="py-2 text-slate-700">{rowError.message}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      <div className="flex flex-col gap-3">
        <h2 className="text-sm font-semibold text-slate-900">Import history</h2>
        {historyError && <Alert variant="error">{historyError}</Alert>}
        <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-3 font-medium">Entity</th>
                <th className="px-4 py-3 font-medium">Status</th>
                <th className="px-4 py-3 font-medium">Rows</th>
                <th className="px-4 py-3 font-medium">Succeeded</th>
                <th className="px-4 py-3 font-medium">Failed</th>
                <th className="px-4 py-3 font-medium">When</th>
                <th className="px-4 py-3 font-medium"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {history?.content.length === 0 && (
                <tr>
                  <td className="px-4 py-6 text-center text-slate-400" colSpan={7}>
                    No imports yet.
                  </td>
                </tr>
              )}
              {history?.content.map((job) => (
                <tr key={job.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3 text-slate-900">{ENTITY_LABELS[job.entityType]}</td>
                  <td className="px-4 py-3">
                    <span
                      className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${
                        job.status === "COMPLETED" ? "bg-emerald-100 text-emerald-700" : "bg-red-100 text-red-700"
                      }`}
                    >
                      {job.status}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-slate-600">{job.totalRows}</td>
                  <td className="px-4 py-3 text-slate-600">{job.successCount}</td>
                  <td className="px-4 py-3 text-slate-600">{job.errorCount}</td>
                  <td className="px-4 py-3 text-slate-600">{new Date(job.createdAt).toLocaleString()}</td>
                  <td className="px-4 py-3 text-right">
                    <button type="button" onClick={() => void viewJob(job.id)} className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
                      View
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {history && (
          <Pagination
            pageNumber={history.pageNumber}
            totalPages={history.totalPages}
            first={history.first}
            last={history.last}
            totalElements={history.totalElements}
            onPageChange={setHistoryPage}
          />
        )}
      </div>
    </div>
  );
}
