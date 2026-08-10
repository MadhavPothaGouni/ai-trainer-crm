import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { listRoles } from "../../api/roles";
import { getUser, removeUser, updateUserRoles, updateUserStatus } from "../../api/users";
import { useAuth } from "../../auth/useAuth";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { ApiError } from "../../lib/apiClient";
import type { RoleDto, UserDto, UserStatus } from "../../types/api";
import { UserStatusBadge } from "./UserListPage";

const STATUS_OPTIONS: UserStatus[] = ["PENDING_VERIFICATION", "ACTIVE", "SUSPENDED", "DEACTIVATED"];

export default function UserDetailPage() {
  const { userId } = useParams<{ userId: string }>();
  const navigate = useNavigate();
  const { user: currentUser } = useAuth();
  const [user, setUser] = useState<UserDto | null>(null);
  const [roles, setRoles] = useState<RoleDto[]>([]);
  const [selectedRoleIds, setSelectedRoleIds] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);
  const [isSavingRoles, setIsSavingRoles] = useState(false);
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);
  const [isRemoving, setIsRemoving] = useState(false);

  useEffect(() => {
    if (!userId) return;
    let cancelled = false;
    Promise.all([getUser(userId), listRoles()])
      .then(([userData, roleData]) => {
        if (cancelled) return;
        setUser(userData);
        setRoles(roleData);
        const matchingIds = roleData.filter((role) => userData.roles.includes(role.name)).map((role) => role.id);
        setSelectedRoleIds(new Set(matchingIds));
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this teammate.");
      });
    return () => {
      cancelled = true;
    };
  }, [userId]);

  function toggleRole(roleId: string) {
    setSelectedRoleIds((prev) => {
      const next = new Set(prev);
      if (next.has(roleId)) next.delete(roleId);
      else next.add(roleId);
      return next;
    });
  }

  async function handleSaveRoles() {
    if (!userId || selectedRoleIds.size === 0) {
      setError("Select at least one role.");
      return;
    }
    setIsSavingRoles(true);
    setError(null);
    try {
      const updated = await updateUserRoles(userId, { roleIds: [...selectedRoleIds] });
      setUser(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update roles.");
    } finally {
      setIsSavingRoles(false);
    }
  }

  async function handleStatusChange(status: string) {
    if (!userId) return;
    setIsUpdatingStatus(true);
    setError(null);
    try {
      const updated = await updateUserStatus(userId, { status: status as UserStatus });
      setUser(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update status.");
    } finally {
      setIsUpdatingStatus(false);
    }
  }

  async function handleRemove() {
    if (!userId || !window.confirm("Remove this teammate's access? This cannot be undone.")) return;
    setIsRemoving(true);
    try {
      await removeUser(userId);
      navigate("/users");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not remove this teammate.");
      setIsRemoving(false);
    }
  }

  if (error && !user) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!user) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  const isSelf = currentUser?.id === user.id;

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/users" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Team
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{user.fullName}</h1>
            <UserStatusBadge status={user.status} />
          </div>
        </div>
        {!isSelf && (
          <Button variant="danger" onClick={() => void handleRemove()} isLoading={isRemoving}>
            Remove
          </Button>
        )}
      </div>

      {error && <Alert variant="error">{error}</Alert>}
      {isSelf && <Alert variant="info">This is your own account - status and removal can&apos;t be changed here.</Alert>}

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Overview</h2>
          <dl className="mt-3 flex flex-col gap-2 text-sm">
            <div className="flex justify-between gap-4">
              <dt className="text-slate-500">Email</dt>
              <dd className="text-slate-900">{user.email}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-slate-500">Phone</dt>
              <dd className="text-slate-900">{user.phone ?? "—"}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-slate-500">Email verified</dt>
              <dd className="text-slate-900">{user.emailVerified ? "Yes" : "No"}</dd>
            </div>
          </dl>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Status</h2>
          <div className="mt-3">
            <Select
              label="Status"
              options={STATUS_OPTIONS.map((status) => ({ value: status, label: status.replace("_", " ") }))}
              value={user.status}
              disabled={isSelf || isUpdatingStatus}
              onChange={(event) => void handleStatusChange(event.target.value)}
            />
          </div>
        </div>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-500">Roles</h2>
        <div className="mt-3 flex flex-col gap-1.5">
          {roles.map((role) => (
            <label key={role.id} className="flex items-center gap-2 text-sm text-slate-700">
              <input
                type="checkbox"
                className="h-4 w-4 rounded border-slate-300"
                checked={selectedRoleIds.has(role.id)}
                onChange={() => toggleRole(role.id)}
              />
              {role.name}
            </label>
          ))}
        </div>
        <div className="mt-4 flex justify-end">
          <Button onClick={() => void handleSaveRoles()} isLoading={isSavingRoles}>
            Save roles
          </Button>
        </div>
      </div>
    </div>
  );
}
