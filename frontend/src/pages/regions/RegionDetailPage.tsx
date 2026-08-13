import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteRegion, getRegion, getRegionRollup, listRegions, updateRegion } from "../../api/regions";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, updateRegionSchema, type UpdateRegionFormValues } from "../../lib/validation";
import type { RegionDto, RegionRollupDto } from "../../types/api";

export default function RegionDetailPage() {
  const { regionId } = useParams<{ regionId: string }>();
  const navigate = useNavigate();
  const [region, setRegion] = useState<RegionDto | null>(null);
  const [regions, setRegions] = useState<RegionDto[]>([]);
  const [rollup, setRollup] = useState<RegionRollupDto | null>(null);
  const [rollupError, setRollupError] = useState<string | null>(null);
  const [isRollupLoading, setIsRollupLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  useEffect(() => {
    if (!regionId) return;
    let cancelled = false;
    getRegion(regionId)
      .then((data) => {
        if (!cancelled) setRegion(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this region.");
      });
    listRegions()
      .then((res) => {
        if (!cancelled) setRegions(res);
      })
      .catch(() => undefined);
    setIsRollupLoading(true);
    getRegionRollup(regionId)
      .then((res) => {
        if (!cancelled) setRollup(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setRollupError(err instanceof ApiError ? err.message : "Could not load this region's rollup.");
      })
      .finally(() => {
        if (!cancelled) setIsRollupLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [regionId]);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
    setError: setEditFieldError,
  } = useForm<UpdateRegionFormValues>({ resolver: zodResolver(updateRegionSchema) });

  useEffect(() => {
    if (!region) return;
    reset({ name: region.name, parentRegionId: region.parentRegionId ?? "", description: region.description ?? "" });
  }, [region, reset]);

  const onSaveEdits = handleSubmit(async (values) => {
    if (!regionId) return;
    setEditError(null);
    try {
      const updated = await updateRegion(regionId, {
        name: values.name,
        parentRegionId: blankToUndefined(values.parentRegionId),
        description: blankToUndefined(values.description),
      });
      setRegion(updated);
    } catch (error) {
      setEditError(applyServerErrors(error, setEditFieldError));
    }
  });

  async function handleDelete() {
    if (!regionId || !window.confirm("Delete this region?")) return;
    setIsDeleting(true);
    try {
      await deleteRegion(regionId);
      navigate("/regions");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this region - reparent its child regions and reassign its teams first.");
      setIsDeleting(false);
    }
  }

  if (error && !region) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!region) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  const parent = regions.find((r) => r.id === region.parentRegionId);
  const parentOptions = regions.filter((r) => r.id !== region.id);

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/regions" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Territory Hierarchy
          </Link>
          <h1 className="mt-1 text-2xl font-semibold text-slate-900">{region.name}</h1>
          {parent && (
            <p className="mt-1 text-xs text-slate-400">
              Under{" "}
              <Link to={`/regions/${parent.id}`} className="hover:underline">
                {parent.name}
              </Link>
            </p>
          )}
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <RollupPanel rollup={rollup} error={rollupError} isLoading={isRollupLoading} />

      <form onSubmit={onSaveEdits} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit</h2>

        {editError && <Alert variant="error">{editError}</Alert>}

        <TextField label="Name" error={errors.name?.message} {...register("name")} />
        <Select
          label="Parent region"
          placeholder="None - top level"
          options={parentOptions.map((r) => ({ value: r.id, label: r.name }))}
          error={errors.parentRegionId?.message}
          {...register("parentRegionId")}
        />
        <TextArea label="Description" rows={3} error={errors.description?.message} {...register("description")} />

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Save changes
          </Button>
        </div>
      </form>
    </div>
  );
}

/** Computed live on every page load - see RegionService#rollup's javadoc for why there's nothing to cache here. */
function RollupPanel({ rollup, error, isLoading }: { rollup: RegionRollupDto | null; error: string | null; isLoading: boolean }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5">
      <h2 className="text-sm font-medium text-slate-500">Rollup (this region + every descendant)</h2>
      {isLoading && <p className="mt-2 text-sm text-slate-400">Loading...</p>}
      {error && (
        <p className="mt-2 text-sm text-slate-400">
          Rollup unavailable{error ? ": " + error : ""} - you may not hold REGION:READ:ORGANIZATION.
        </p>
      )}
      {rollup && (
        <div className="mt-3 grid grid-cols-2 gap-4 sm:grid-cols-4">
          <Stat label="Sub-regions" value={rollup.descendantRegionCount} />
          <Stat label="Teams" value={rollup.teamCount} />
          <Stat label="Reps" value={rollup.userCount} />
          <Stat label="Open deals" value={rollup.openOpportunityCount} />
          <Stat label="Open pipeline" value={formatCurrency(rollup.openPipelineValue)} />
          <Stat label="Won deals" value={rollup.wonOpportunityCount} />
          <Stat label="Won value" value={formatCurrency(rollup.wonValue)} accent="text-emerald-700" />
          <Stat label="Lost value" value={formatCurrency(rollup.lostValue)} accent="text-red-600" />
        </div>
      )}
    </div>
  );
}

function Stat({ label, value, accent }: { label: string; value: string | number; accent?: string }) {
  return (
    <div>
      <p className="text-xs font-medium uppercase tracking-wide text-slate-400">{label}</p>
      <p className={`mt-0.5 text-lg font-semibold ${accent ?? "text-slate-900"}`}>{value}</p>
    </div>
  );
}

function formatCurrency(value: number): string {
  return value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
