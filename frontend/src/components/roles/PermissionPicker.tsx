import type { PermissionDto } from "../../types/api";

interface PermissionPickerProps {
  catalog: PermissionDto[];
  selectedIds: Set<string>;
  onToggle: (permissionId: string) => void;
  disabled?: boolean;
}

/** Groups the full permission catalog by resource and renders one checkbox per (action, scope) permission. */
export function PermissionPicker({ catalog, selectedIds, onToggle, disabled = false }: PermissionPickerProps) {
  const byResource = new Map<string, PermissionDto[]>();
  for (const permission of catalog) {
    const group = byResource.get(permission.resource) ?? [];
    group.push(permission);
    byResource.set(permission.resource, group);
  }

  const resources = [...byResource.keys()].sort();

  if (resources.length === 0) {
    return <p className="text-sm text-slate-400">No permissions available.</p>;
  }

  return (
    <div className="flex max-h-96 flex-col gap-4 overflow-y-auto rounded-md border border-slate-200 p-4">
      {resources.map((resource) => (
        <div key={resource}>
          <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">{resource}</h3>
          <div className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1.5 sm:grid-cols-3">
            {byResource.get(resource)!.map((permission) => (
              <label key={permission.id} className="flex items-center gap-2 text-sm text-slate-700">
                <input
                  type="checkbox"
                  className="h-4 w-4 rounded border-slate-300"
                  checked={selectedIds.has(permission.id)}
                  disabled={disabled}
                  onChange={() => onToggle(permission.id)}
                />
                {permission.action}:{permission.scope}
              </label>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
