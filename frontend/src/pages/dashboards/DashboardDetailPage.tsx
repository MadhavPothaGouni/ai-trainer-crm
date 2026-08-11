import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { addDashboardWidget, deleteDashboard, getDashboard, getDashboardData, removeDashboardWidget, setDashboardDefault } from "../../api/dashboards";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { addDashboardWidgetSchema, type AddDashboardWidgetFormValues } from "../../lib/validation";
import { LeadFunnelCard, LeaderboardCard, PipelineByStageCard } from "../reports/ReportsPage";
import {
  DASHBOARD_WIDGET_REPORT_TYPES,
  type DashboardDataDto,
  type DashboardDto,
  type DashboardWidgetReportType,
  type LeadFunnelStageDto,
  type PipelineStageSummaryDto,
  type RepLeaderboardEntryDto,
} from "../../types/api";

const REPORT_TYPE_LABELS: Record<DashboardWidgetReportType, string> = {
  PIPELINE_BY_STAGE: "Pipeline by stage",
  LEAD_FUNNEL: "Lead conversion funnel",
  LEADERBOARD: "Rep leaderboard",
};

export default function DashboardDetailPage() {
  const { dashboardId } = useParams<{ dashboardId: string }>();
  const navigate = useNavigate();
  const [dashboard, setDashboard] = useState<DashboardDto | null>(null);
  const [data, setData] = useState<DashboardDataDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isTogglingDefault, setIsTogglingDefault] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isAddingWidget, setIsAddingWidget] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<AddDashboardWidgetFormValues>({
    resolver: zodResolver(addDashboardWidgetSchema),
    defaultValues: { reportType: "PIPELINE_BY_STAGE" },
  });

  function reload() {
    if (!dashboardId) return;
    getDashboard(dashboardId)
      .then(setDashboard)
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load this dashboard."));
    getDashboardData(dashboardId)
      .then(setData)
      .catch(() => undefined);
  }

  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dashboardId]);

  async function handleToggleDefault() {
    if (!dashboardId) return;
    setIsTogglingDefault(true);
    setError(null);
    try {
      setDashboard(await setDashboardDefault(dashboardId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not set this as the default dashboard.");
    } finally {
      setIsTogglingDefault(false);
    }
  }

  const onAddWidget = handleSubmit(async (values) => {
    if (!dashboardId) return;
    setError(null);
    setIsAddingWidget(true);
    try {
      await addDashboardWidget(dashboardId, {
        reportType: values.reportType as DashboardWidgetReportType,
        title: values.title || undefined,
        width: values.width ? Number(values.width) : undefined,
        height: values.height ? Number(values.height) : undefined,
      });
      reset({ reportType: values.reportType, title: "", width: "", height: "" });
      reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not add this widget.");
    } finally {
      setIsAddingWidget(false);
    }
  });

  async function handleRemoveWidget(widgetId: string) {
    if (!dashboardId || !window.confirm("Remove this widget?")) return;
    try {
      await removeDashboardWidget(dashboardId, widgetId);
      reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not remove this widget.");
    }
  }

  async function handleDelete() {
    if (!dashboardId || !window.confirm("Delete this dashboard?")) return;
    setIsDeleting(true);
    try {
      await deleteDashboard(dashboardId);
      navigate("/dashboards");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this dashboard.");
      setIsDeleting(false);
    }
  }

  if (error && !dashboard) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!dashboard || !dashboardId) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/dashboards" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Dashboards
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{dashboard.name}</h1>
            {dashboard.isDefault && (
              <span className="rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-medium text-emerald-700">Default</span>
            )}
          </div>
          {dashboard.description && <p className="text-sm text-slate-500">{dashboard.description}</p>}
        </div>
        <div className="flex gap-3">
          {!dashboard.isDefault && (
            <Button variant="secondary" onClick={() => void handleToggleDefault()} isLoading={isTogglingDefault}>
              Set as default
            </Button>
          )}
          <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
            Delete
          </Button>
        </div>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="grid gap-4 lg:grid-cols-2">
        {data?.widgets.map((widget) => (
          <div key={widget.id} className="relative">
            <button
              type="button"
              onClick={() => void handleRemoveWidget(widget.id)}
              className="absolute right-3 top-3 text-xs text-slate-400 hover:text-red-600"
            >
              Remove
            </button>
            {widget.reportType === "PIPELINE_BY_STAGE" && <PipelineByStageCard rows={widget.data as PipelineStageSummaryDto[]} />}
            {widget.reportType === "LEAD_FUNNEL" && <LeadFunnelCard rows={widget.data as LeadFunnelStageDto[]} />}
            {widget.reportType === "LEADERBOARD" && <LeaderboardCard rows={widget.data as RepLeaderboardEntryDto[]} />}
          </div>
        ))}
        {data && data.widgets.length === 0 && (
          <p className="text-sm text-slate-400 lg:col-span-2">No widgets yet - add one below.</p>
        )}
      </div>

      <form onSubmit={onAddWidget} noValidate className="flex flex-wrap items-end gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <Select
          label="Add a widget"
          options={DASHBOARD_WIDGET_REPORT_TYPES.map((type) => ({ value: type, label: REPORT_TYPE_LABELS[type] }))}
          error={errors.reportType?.message}
          {...register("reportType")}
        />
        <TextField label="Title (optional)" placeholder="Uses a default title if left blank" error={errors.title?.message} {...register("title")} />
        <Button type="submit" isLoading={isAddingWidget}>
          Add widget
        </Button>
      </form>
    </div>
  );
}
