import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { Link } from "react-router-dom";
import { createRegion, listRegions } from "../../api/regions";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createRegionSchema, type CreateRegionFormValues } from "../../lib/validation";
import type { RegionDto } from "../../types/api";

export default function RegionListPage() {
  const [regions, setRegions] = useState<RegionDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  function reload() {
    setIsLoading(true);
    listRegions()
      .then((res) => setRegions(res))
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load regions."))
      .finally(() => setIsLoading(false));
  }

  useEffect(() => {
    reload();
  }, []);

  const childrenByParent = useMemo(() => {
    const map = new Map<string, RegionDto[]>();
    for (const region of regions) {
      const key = region.parentRegionId ?? "";
      const siblings = map.get(key) ?? [];
      siblings.push(region);
      map.set(key, siblings);
    }
    return map;
  }, [regions]);

  const roots = childrenByParent.get("") ?? [];

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Territory Hierarchy</h1>
        <p className="mt-1 text-sm text-slate-500">
          A nested Region tree for org-chart-style rollup reporting - a different concept from Territory Rules, which auto-route
          new Leads/Accounts to an owner. Assign a Team to a Region from the team's own page.
        </p>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="rounded-lg border border-slate-200 bg-white p-5">
        {isLoading && <p className="text-sm text-slate-400">Loading...</p>}
        {!isLoading && regions.length === 0 && <p className="text-sm text-slate-400">No regions yet.</p>}
        {!isLoading && roots.length > 0 && (
          <ul className="flex flex-col gap-1">
            {roots.map((region) => (
              <RegionTreeNode key={region.id} region={region} childrenByParent={childrenByParent} depth={0} />
            ))}
          </ul>
        )}
      </div>

      <CreateRegionForm regions={regions} onCreated={reload} />
    </div>
  );
}

function RegionTreeNode({
  region,
  childrenByParent,
  depth,
}: {
  region: RegionDto;
  childrenByParent: Map<string, RegionDto[]>;
  depth: number;
}) {
  const children = childrenByParent.get(region.id) ?? [];
  return (
    <li>
      <Link
        to={`/regions/${region.id}`}
        className="block rounded px-2 py-1.5 text-sm font-medium text-slate-900 hover:bg-slate-50 hover:underline"
        style={{ marginLeft: depth * 20 }}
      >
        {region.name}
      </Link>
      {children.length > 0 && (
        <ul className="flex flex-col gap-1">
          {children.map((child) => (
            <RegionTreeNode key={child.id} region={child} childrenByParent={childrenByParent} depth={depth + 1} />
          ))}
        </ul>
      )}
    </li>
  );
}

function CreateRegionForm({ regions, onCreated }: { regions: RegionDto[]; onCreated: () => void }) {
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<CreateRegionFormValues>({
    resolver: zodResolver(createRegionSchema),
    defaultValues: { name: "", parentRegionId: "", description: "" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await createRegion({
        name: values.name,
        parentRegionId: blankToUndefined(values.parentRegionId),
        description: blankToUndefined(values.description),
      });
      reset({ name: "", parentRegionId: "", description: "" });
      onCreated();
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex max-w-xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
      <h2 className="text-sm font-medium text-slate-900">New region</h2>

      {formError && <Alert variant="error">{formError}</Alert>}

      <TextField label="Name" error={errors.name?.message} {...register("name")} />
      <Select
        label="Parent region"
        placeholder="None - top level"
        options={regions.map((r) => ({ value: r.id, label: r.name }))}
        error={errors.parentRegionId?.message}
        {...register("parentRegionId")}
      />
      <TextArea label="Description" rows={2} error={errors.description?.message} {...register("description")} />

      <div className="flex justify-end">
        <Button type="submit" isLoading={isSubmitting}>
          Create region
        </Button>
      </div>
    </form>
  );
}
