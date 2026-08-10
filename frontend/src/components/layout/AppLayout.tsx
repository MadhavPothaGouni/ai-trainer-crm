import { Outlet } from "react-router-dom";
import { useAuth } from "../../auth/useAuth";
import { Button } from "../ui/Button";

/** Shell for every authenticated page: a slim top bar (current user + sign out) plus the routed page content. */
export function AppLayout() {
  const { user, logout } = useAuth();

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4">
          <div className="flex items-center gap-2">
            <div className="flex h-7 w-7 items-center justify-center rounded-md bg-slate-900 text-xs font-semibold text-white">
              AT
            </div>
            <span className="text-sm font-medium text-slate-900">AI-Trainer CRM</span>
          </div>
          <div className="flex items-center gap-3">
            {user && (
              <span className="text-sm text-slate-600">
                {user.fullName} <span className="text-slate-400">&middot; {user.roles.join(", ")}</span>
              </span>
            )}
            <Button variant="secondary" onClick={() => void logout()}>
              Sign out
            </Button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  );
}
