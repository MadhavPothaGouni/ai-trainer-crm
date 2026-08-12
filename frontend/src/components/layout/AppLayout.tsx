import { Link, NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../../auth/useAuth";
import { Button } from "../ui/Button";
import { NotificationBell } from "./NotificationBell";

const CRM_NAV_LINKS = [
  { to: "/", label: "Dashboard", end: true },
  { to: "/tasks", label: "My Tasks", end: false },
  { to: "/accounts", label: "Accounts", end: false },
  { to: "/contacts", label: "Contacts", end: false },
  { to: "/opportunities", label: "Opportunities", end: false },
  { to: "/leads", label: "Leads", end: false },
  // No permission of its own - reuses whichever of LEAD/CONTACT/ACCOUNT's own READ/UPDATE the
  // pair's entityType maps to, so unlike Forecast/Reports below this belongs in the main nav:
  // a default MEMBER already holds everything this page needs. See backend/crm-platform/
  // README.md's module layout for `dedupe`.
  { to: "/duplicates", label: "Duplicates", end: false },
  // GET /sales-goals/mine needs no permission at all - the same self-scoped shape the
  // notification inbox uses - so this belongs in the main nav even though defining goals
  // (below, "Sales Goals") is admin-gated. See backend/crm-platform/README.md's module
  // layout for `salesgoals`.
  { to: "/my-goals", label: "My Goals", end: false },
  { to: "/tickets", label: "Tickets", end: false },
  { to: "/emails", label: "Emails", end: false },
  { to: "/calendar", label: "Calendar", end: false },
  { to: "/quotes", label: "Quotes", end: false },
  { to: "/products", label: "Products", end: false },
  // No OWN scope on EMAIL_TEMPLATE, same three-scope-ladder shape as Products above (TEAM/
  // DEPARTMENT/ORGANIZATION only) - a template is shared organization content, not admin-only
  // config, so this belongs in the main nav rather than the admin group below. See
  // backend/crm-platform/README.md's module layout for `emailtemplate`.
  { to: "/email-templates", label: "Email Templates", end: false },
  { to: "/orders", label: "Orders", end: false },
  { to: "/invoices", label: "Invoices", end: false },
  { to: "/campaigns", label: "Campaigns", end: false },
  { to: "/attachments", label: "Attachments", end: false },
  { to: "/approvals", label: "Approvals", end: false },
  { to: "/knowledge-articles", label: "Knowledge Base", end: false },
];

// Team/Role management hits USER:READ:ORGANIZATION / ROLE:READ:ORGANIZATION on the
// backend, which only the built-in OWNER and ADMIN roles hold by default (see
// RoleService#createDefaultRolesForOrganization) - a custom role *could* also grant
// these, but there's no per-permission info on the client to check that precisely, so
// this hides the links for the common case rather than showing a dead end. Anyone who
// does have access via a custom role can still reach these pages directly by URL.
// REPORT/API_KEY/INTEGRATION/CUSTOM_FIELD/CUSTOM_OBJECT/WORKFLOW/DASHBOARD aren't core CRM
// resources either (see RoleService#isCoreCrmResource on the backend) - the default MEMBER
// role holds none of them, only OWNER/ADMIN, so Reports, the platform/integration pages,
// Custom Objects/Fields, Workflows, and Dashboards all live in this admin-only group too.
// (Workflow and Dashboard ARE owner-scoped like Contact/Lead, unlike Custom Field/Object -
// they're grouped here purely because MEMBER doesn't hold them by default, same reasoning
// as Reports.) Import/Export rides on ACCOUNT/CONTACT/LEAD's own IMPORT/EXPORT actions,
// which - unlike those same resources' CREATE/READ/UPDATE - also aren't in MEMBER's default
// grant (RoleService#createDefaultRolesForOrganization only hands MEMBER CREATE/READ/UPDATE),
// so it lives here too even though Accounts/Contacts/Leads themselves are in the main nav.
const ADMIN_NAV_LINKS = [
  { to: "/reports", label: "Reports", end: false },
  // Reuses REPORT:READ rather than a permission of its own - see backend/crm-platform/
  // README.md's module layout for `forecast`. Placed next to Reports for the same reason: same
  // gate, same audience, just persisted history instead of a live view.
  { to: "/forecast", label: "Forecast", end: false },
  { to: "/dashboards", label: "Dashboards", end: false },
  { to: "/import-export", label: "Import / Export", end: false },
  { to: "/users", label: "Team", end: false },
  // Deliberately not labeled "Teams" - "/users" above is already labeled "Team" (the whole
  // teammate roster) and the two would be confusable. This is the literal Team entity
  // (Sales/Marketing/Support/... sub-groupings) TEAM/DEPARTMENT-scoped permissions resolve
  // against - see backend/crm-platform/README.md's module layout for `organization`.
  { to: "/teams", label: "Team Groups", end: false },
  { to: "/roles", label: "Roles", end: false },
  { to: "/api-keys", label: "API Keys", end: false },
  { to: "/webhooks", label: "Webhooks", end: false },
  { to: "/custom-objects", label: "Custom Objects", end: false },
  { to: "/custom-fields", label: "Custom Fields", end: false },
  { to: "/workflows", label: "Workflows", end: false },
  // SLA_POLICY:*:ORGANIZATION only, same third-kind admin-config shape as CustomField/ApiKey/
  // Webhook above - see backend/crm-platform/README.md's module layout for `sla`.
  { to: "/sla-policies", label: "SLA Policies", end: false },
  // TERRITORY_RULE:*:ORGANIZATION only, same third-kind shape - this gate only covers defining
  // rules, not the auto-assignment they trigger (that runs unconditionally off TerritoryRule
  // data via an @EventListener with no @PreAuthorize of its own). See backend/crm-platform/
  // README.md's module layout for `territory`.
  { to: "/territory-rules", label: "Territory Rules", end: false },
  // LEAD_SCORING_RULE:*:ORGANIZATION only, same third-kind admin-config shape as
  // TerritoryRule/SlaPolicy above - see backend/crm-platform/README.md's module layout for
  // `leadscoring`. The computed score itself is visible to anyone who can see a Lead at all
  // (LeadDto#score), only *defining the rules* is admin-gated.
  { to: "/lead-scoring-rules", label: "Lead Scoring Rules", end: false },
  // SALES_GOAL:*:ORGANIZATION only, same third-kind admin-config shape as the three above -
  // this gate only covers defining/editing goals. Viewing your OWN goals lives in the main
  // nav's "My Goals" instead (GET /sales-goals/mine, no permission required).
  { to: "/sales-goals", label: "Sales Goals", end: false },
];

/** Shell for every authenticated page: a slim top bar (current user + sign out), CRM nav, and the routed page content. */
export function AppLayout() {
  const { user, logout } = useAuth();
  const isOrgAdmin = user?.roles.includes("OWNER") || user?.roles.includes("ADMIN");
  const navLinks = isOrgAdmin ? [...CRM_NAV_LINKS, ...ADMIN_NAV_LINKS] : CRM_NAV_LINKS;

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
            <NotificationBell />
            {user && (
              <Link to="/profile" className="text-sm text-slate-600 hover:text-slate-900 hover:underline">
                {user.fullName} <span className="text-slate-400">&middot; {user.roles.join(", ")}</span>
              </Link>
            )}
            <Button variant="secondary" onClick={() => void logout()}>
              Sign out
            </Button>
          </div>
        </div>
        <nav className="mx-auto flex max-w-5xl gap-1 px-4">
          {navLinks.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              end={link.end}
              className={({ isActive }) =>
                `border-b-2 px-3 py-2 text-sm font-medium transition-colors ${
                  isActive ? "border-slate-900 text-slate-900" : "border-transparent text-slate-500 hover:text-slate-900"
                }`
              }
            >
              {link.label}
            </NavLink>
          ))}
        </nav>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  );
}
