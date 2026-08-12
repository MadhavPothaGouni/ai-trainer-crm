import { useEffect, useRef, useState } from "react";
import { createSavedView, deleteSavedView, listSavedViews, setDefaultSavedView, updateSavedView } from "../../api/savedViews";
import { ApiError } from "../../lib/apiClient";
import type { SavedViewDto, SavedViewEntityType, SavedViewFilters, SavedViewSortDirection } from "../../types/api";
import { Alert } from "../ui/Alert";
import { Button } from "../ui/Button";
import { Select } from "../ui/Select";
import { TextField } from "../ui/TextField";

export interface SortFieldOption {
  value: string;
  label: string;
}

interface SavedViewsBarProps {
  entityType: SavedViewEntityType;
  search: string;
  onSearchChange: (search: string) => void;
  sortField: string;
  sortDirection: SavedViewSortDirection;
  sortFieldOptions: SortFieldOption[];
  onSortChange: (sortField: string, sortDirection: SavedViewSortDirection) => void;
}

/** Reads `view.filters` as {@link SavedViewFilters} JSON, defaulting to `{}` for anything malformed
 * or pre-dating a filter this bar didn't know about yet - filters is the backend's opaque blob, so
 * nothing on the wire guarantees it parses cleanly. */
function parseFilters(filters: string): SavedViewFilters {
  try {
    const parsed: unknown = JSON.parse(filters);
    return parsed && typeof parsed === "object" ? (parsed as SavedViewFilters) : {};
  } catch {
    return {};
  }
}

/**
 * Sits above a CRM list page letting a user save the current search text + sort as a named,
 * personal preset, switch between presets, and mark one as the default that's auto-applied the
 * next time this entity type's list page loads. Fully self-scoped on the backend (no permission
 * needed at all), so this bar renders identically for every role.
 *
 * The parent page owns `search`/`sortField`/`sortDirection` as controlled state and is responsible
 * for actually filtering/sorting its results with them - this component only manages the saved
 * presets themselves and calls back into the parent when one is applied.
 */
export function SavedViewsBar({
  entityType,
  search,
  onSearchChange,
  sortField,
  sortDirection,
  sortFieldOptions,
  onSortChange,
}: SavedViewsBarProps) {
  const [views, setViews] = useState<SavedViewDto[]>([]);
  const [selectedViewId, setSelectedViewId] = useState<string>("");
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [showSaveForm, setShowSaveForm] = useState(false);
  const [newViewName, setNewViewName] = useState("");
  const hasAutoAppliedDefault = useRef(false);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listSavedViews(entityType)
      .then((result) => {
        if (cancelled) return;
        setViews(result);
        if (!hasAutoAppliedDefault.current) {
          hasAutoAppliedDefault.current = true;
          const defaultView = result.find((view) => view.isDefault);
          if (defaultView) applyView(defaultView);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load saved views.");
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // Only re-runs when the page itself switches entity type, not on every search/sort keystroke.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [entityType]);

  function applyView(view: SavedViewDto) {
    const filters = parseFilters(view.filters);
    onSearchChange(filters.search ?? "");
    onSortChange(view.sortField ?? sortFieldOptions[0]?.value ?? "", view.sortDirection ?? "DESC");
    setSelectedViewId(view.id);
  }

  function handleSelectView(viewId: string) {
    if (!viewId) {
      setSelectedViewId("");
      return;
    }
    const view = views.find((candidate) => candidate.id === viewId);
    if (view) applyView(view);
  }

  async function handleSaveNewView(event: React.FormEvent) {
    event.preventDefault();
    if (!newViewName.trim()) return;
    setIsSaving(true);
    setError(null);
    try {
      const filters: SavedViewFilters = { search: search || undefined };
      const created = await createSavedView({
        entityType,
        name: newViewName.trim(),
        filters: JSON.stringify(filters),
        sortField,
        sortDirection,
      });
      setViews((current) => [...current, created].sort((a, b) => a.name.localeCompare(b.name)));
      setSelectedViewId(created.id);
      setNewViewName("");
      setShowSaveForm(false);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not save this view.");
    } finally {
      setIsSaving(false);
    }
  }

  async function handleUpdateSelectedView() {
    const view = views.find((candidate) => candidate.id === selectedViewId);
    if (!view) return;
    setIsSaving(true);
    setError(null);
    try {
      const filters: SavedViewFilters = { search: search || undefined };
      const updated = await updateSavedView(view.id, {
        name: view.name,
        filters: JSON.stringify(filters),
        sortField,
        sortDirection,
      });
      setViews((current) => current.map((candidate) => (candidate.id === updated.id ? updated : candidate)));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update this view.");
    } finally {
      setIsSaving(false);
    }
  }

  async function handleSetDefault() {
    if (!selectedViewId) return;
    setIsSaving(true);
    setError(null);
    try {
      const updated = await setDefaultSavedView(selectedViewId);
      setViews((current) => current.map((view) => ({ ...view, isDefault: view.id === updated.id })));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not set this view as default.");
    } finally {
      setIsSaving(false);
    }
  }

  async function handleDelete() {
    if (!selectedViewId) return;
    setIsSaving(true);
    setError(null);
    try {
      await deleteSavedView(selectedViewId);
      setViews((current) => current.filter((view) => view.id !== selectedViewId));
      setSelectedViewId("");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this view.");
    } finally {
      setIsSaving(false);
    }
  }

  const selectedView = views.find((view) => view.id === selectedViewId) ?? null;

  return (
    <div className="flex flex-col gap-3 rounded-lg border border-slate-200 bg-slate-50 p-3">
      {error && <Alert variant="error">{error}</Alert>}
      <div className="flex flex-wrap items-end gap-3">
        <div className="min-w-[220px] flex-1">
          <TextField
            label="Search"
            placeholder="Filter this page..."
            value={search}
            onChange={(event) => onSearchChange(event.target.value)}
          />
        </div>
        <div className="w-44">
          <Select
            label="Sort by"
            value={sortField}
            options={sortFieldOptions}
            onChange={(event) => onSortChange(event.target.value, sortDirection)}
          />
        </div>
        <div className="w-36">
          <Select
            label="Direction"
            value={sortDirection}
            options={[
              { value: "DESC", label: "Descending" },
              { value: "ASC", label: "Ascending" },
            ]}
            onChange={(event) => onSortChange(sortField, event.target.value as SavedViewSortDirection)}
          />
        </div>
        <div className="w-52">
          <Select
            label="Saved view"
            value={selectedViewId}
            placeholder={isLoading ? "Loading..." : "None"}
            options={views.map((view) => ({ value: view.id, label: view.isDefault ? `${view.name} (default)` : view.name }))}
            onChange={(event) => handleSelectView(event.target.value)}
          />
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <Button type="button" variant="secondary" onClick={() => setShowSaveForm((current) => !current)} disabled={isSaving}>
          Save current as...
        </Button>
        {selectedView && (
          <>
            <Button type="button" variant="secondary" onClick={handleUpdateSelectedView} isLoading={isSaving}>
              Update "{selectedView.name}"
            </Button>
            {!selectedView.isDefault && (
              <Button type="button" variant="secondary" onClick={handleSetDefault} isLoading={isSaving}>
                Set as default
              </Button>
            )}
            <Button type="button" variant="danger" onClick={handleDelete} isLoading={isSaving}>
              Delete
            </Button>
          </>
        )}
      </div>

      {showSaveForm && (
        <form className="flex items-end gap-2" onSubmit={handleSaveNewView}>
          <div className="w-64">
            <TextField
              label="New view name"
              value={newViewName}
              onChange={(event) => setNewViewName(event.target.value)}
              placeholder="e.g. Hot leads this week"
              required
            />
          </div>
          <Button type="submit" isLoading={isSaving}>
            Save view
          </Button>
        </form>
      )}
    </div>
  );
}
