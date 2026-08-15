import { useMemo, useState } from "react";
import { Link, NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../../auth/useAuth";
import { Button } from "../ui/Button";
import { NotificationBell } from "./NotificationBell";

interface NavItem {
  to: string;
  label: string;
  end?: boolean;
}

interface NavGroup {
  heading: string;
  items: NavItem[];
}

/**
 * Every non-admin page, grouped for a sidebar instead of the single flat list this used
 * to be. The grouping is purely presentational - it doesn't encode any permission logic
 * of its own, that's still enforced by the backend (see each page's own data-fetch) and
 * by ADMIN_NAV_GROUPS below being hidden entirely for non-admins. Two self-scoped,
 * no-permission-required pages ("My Goals" hits GET /sales-goals/mine, "My Commissions"
 * hits GET /commission-records/mine) sit in Overview even though defining goals/plans is
 * admin-gated - see ADMIN_NAV_GROUPS' "Rules & Policies" for those.
 */
const CRM_NAV_GROUPS: NavGroup[] = [
  {
    heading: "Overview",
    items: [
      { to: "/", label: "Dashboard", end: true },
      { to: "/tasks", label: "My Tasks" },
      { to: "/my-goals", label: "My Goals" },
      { to: "/my-commissions", label: "My Commissions" },
    ],
  },
  {
    heading: "CRM",
    items: [
      { to: "/accounts", label: "Accounts" },
      { to: "/contacts", label: "Contacts" },
      { to: "/opportunities", label: "Opportunities" },
      { to: "/leads", label: "Leads" },
      { to: "/duplicates", label: "Duplicates" },
    ],
  },
  {
    heading: "Engagement",
    items: [
      { to: "/tickets", label: "Tickets" },
      { to: "/emails", label: "Emails" },
      { to: "/calendar", label: "Calendar" },
      { to: "/booking-links", label: "Booking Links" },
      { to: "/campaigns", label: "Campaigns" },
      { to: "/knowledge-articles", label: "Knowledge Base" },
    ],
  },
  {
    heading: "Sales",
    items: [
      { to: "/quotes", label: "Quotes" },
      { to: "/orders", label: "Orders" },
      { to: "/invoices", label: "Invoices" },
      { to: "/contracts", label: "Contracts" },
      { to: "/products", label: "Products" },
      { to: "/membership-plans", label: "Membership Plans" },
      { to: "/email-templates", label: "Email Templates" },
      { to: "/referrals", label: "Referrals" },
      { to: "/vendors", label: "Vendors" },
      { to: "/purchase-orders", label: "Purchase Orders" },
      { to: "/promo-codes", label: "Promo Codes" },
      { to: "/promo-redemptions", label: "Promo Redemptions" },
      { to: "/gift-cards", label: "Gift Cards" },
    ],
  },
  {
    heading: "Training & Clients",
    items: [
      { to: "/client-goals", label: "Client Goals" },
      { to: "/progress-photos", label: "Progress Photos" },
      { to: "/client-documents", label: "Client Documents" },
      { to: "/client-check-ins", label: "Client Check-Ins" },
      { to: "/no-show-records", label: "No-Show Records" },
      { to: "/loyalty-transactions", label: "Loyalty Transactions" },
      { to: "/intake-forms", label: "Intake Forms" },
      { to: "/intake-form-submissions", label: "Intake Form Submissions" },
      { to: "/memberships", label: "Memberships" },
      { to: "/group-classes", label: "Group Classes" },
      { to: "/class-sessions", label: "Class Sessions" },
      { to: "/equipment", label: "Equipment" },
      { to: "/maintenance-logs", label: "Maintenance Logs" },
      { to: "/equipment-reservations", label: "Equipment Reservations" },
      { to: "/rooms", label: "Rooms" },
      { to: "/room-bookings", label: "Room Bookings" },
      { to: "/lockers", label: "Lockers" },
      { to: "/locker-assignments", label: "Locker Assignments" },
      { to: "/shift-templates", label: "Shift Templates" },
      { to: "/shifts", label: "Shifts" },
      { to: "/time-off-requests", label: "Time-Off Requests" },
      { to: "/training-sessions", label: "Training Sessions" },
      { to: "/nutrition-plans", label: "Nutrition Plans" },
      { to: "/body-measurements", label: "Body Measurements" },
      { to: "/courses", label: "Training" },
      { to: "/certifications", label: "Certifications" },
      { to: "/exercises", label: "Exercises" },
    ],
  },
  {
    heading: "Automation",
    items: [
      { to: "/sequences", label: "Sequences" },
      { to: "/macros", label: "Macros" },
      { to: "/attachments", label: "Attachments" },
      { to: "/approvals", label: "Approvals" },
    ],
  },
];

/**
 * Only rendered for OWNER/ADMIN (see isOrgAdmin below) - every one of these resources
 * either isn't a core CRM resource at all (RoleService#isCoreCrmResource on the backend
 * says no) or, like Import/Export, rides on an action a default MEMBER isn't granted.
 * Anyone with access via a custom role can still reach these pages directly by URL; this
 * only hides the links for the common case rather than showing a dead end. See
 * backend/crm-platform/README.md's module layout for exactly which resource backs each.
 */
const ADMIN_NAV_GROUPS: NavGroup[] = [
  {
    heading: "Analytics",
    items: [
      { to: "/reports", label: "Reports" },
      { to: "/forecast", label: "Forecast" },
      { to: "/dashboards", label: "Dashboards" },
    ],
  },
  {
    heading: "Organization",
    items: [
      { to: "/users", label: "Team" },
      { to: "/teams", label: "Team Groups" },
      { to: "/roles", label: "Roles" },
      { to: "/api-keys", label: "API Keys" },
      { to: "/webhooks", label: "Webhooks" },
    ],
  },
  {
    heading: "Platform",
    items: [
      { to: "/import-export", label: "Import / Export" },
      { to: "/custom-objects", label: "Custom Objects" },
      { to: "/custom-fields", label: "Custom Fields" },
      { to: "/workflows", label: "Workflows" },
    ],
  },
  {
    heading: "Rules & Policies",
    items: [
      { to: "/sla-policies", label: "SLA Policies" },
      { to: "/territory-rules", label: "Territory Rules" },
      { to: "/lead-scoring-rules", label: "Lead Scoring Rules" },
      { to: "/sales-goals", label: "Sales Goals" },
      { to: "/regions", label: "Territory Hierarchy" },
      { to: "/commission-plans", label: "Commission Plans" },
      { to: "/commission-records", label: "Commission Records" },
      { to: "/compensation-records", label: "Compensation Records" },
    ],
  },
  {
    heading: "Compliance",
    items: [{ to: "/data-subject-requests", label: "Data Subject Requests" }],
  },
];

/** Shell for every authenticated page: a slim top bar, a grouped/searchable sidebar, and the routed page content. */
export function AppLayout() {
  const { user, logout } = useAuth();
  const [query, setQuery] = useState("");
  const [isMobileNavOpen, setIsMobileNavOpen] = useState(false);
  const isOrgAdmin = user?.roles.includes("OWNER") || user?.roles.includes("ADMIN");
  const groups = isOrgAdmin ? [...CRM_NAV_GROUPS, ...ADMIN_NAV_GROUPS] : CRM_NAV_GROUPS;

  const visibleGroups = useMemo(() => {
    const trimmed = query.trim().toLowerCase();
    if (!trimmed) return groups;
    return groups
      .map((group) => ({ ...group, items: group.items.filter((item) => item.label.toLowerCase().includes(trimmed)) }))
      .filter((group) => group.items.length > 0);
  }, [groups, query]);

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="sticky top-0 z-20 border-b border-slate-200 bg-white">
        <div className="flex h-14 items-center justify-between px-4">
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setIsMobileNavOpen((prev) => !prev)}
              className="rounded-md p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-900 lg:hidden"
              aria-label="Toggle navigation"
            >
              <MenuIcon />
            </button>
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
      </header>

      <div className="flex">
        {isMobileNavOpen && (
          <div
            className="fixed inset-0 z-10 bg-slate-900/30 lg:hidden"
            onClick={() => setIsMobileNavOpen(false)}
            aria-hidden="true"
          />
        )}

        <aside
          className={`fixed inset-y-0 top-14 z-10 w-64 shrink-0 -translate-x-full overflow-y-auto border-r border-slate-200 bg-white pb-8 transition-transform lg:sticky lg:top-14 lg:h-[calc(100vh-3.5rem)] lg:translate-x-0 ${
            isMobileNavOpen ? "translate-x-0" : ""
          }`}
        >
          <div className="sticky top-0 z-10 border-b border-slate-100 bg-white p-3">
            <div className="relative">
              <span className="pointer-events-none absolute inset-y-0 left-2.5 flex items-center text-slate-400">
                <SearchIcon />
              </span>
              <input
                type="search"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Find a page..."
                className="w-full rounded-md border border-slate-300 py-1.5 pl-8 pr-2 text-sm text-slate-900 placeholder:text-slate-400 focus:border-slate-400 focus:outline-none"
              />
            </div>
          </div>

          <nav className="flex flex-col gap-4 px-3 py-3">
            {visibleGroups.length === 0 && <p className="px-2 py-4 text-sm text-slate-400">No pages match &ldquo;{query}&rdquo;.</p>}
            {visibleGroups.map((group) => (
              <div key={group.heading}>
                <p className="px-2 pb-1 text-xs font-semibold uppercase tracking-wide text-slate-400">{group.heading}</p>
                <div className="flex flex-col gap-0.5">
                  {group.items.map((item) => (
                    <NavLink
                      key={item.to}
                      to={item.to}
                      end={item.end}
                      onClick={() => setIsMobileNavOpen(false)}
                      className={({ isActive }) =>
                        `rounded-md px-2 py-1.5 text-sm font-medium transition-colors ${
                          isActive ? "bg-slate-900 text-white" : "text-slate-600 hover:bg-slate-100 hover:text-slate-900"
                        }`
                      }
                    >
                      {item.label}
                    </NavLink>
                  ))}
                </div>
              </div>
            ))}
          </nav>
        </aside>

        <main className="min-w-0 flex-1 px-4 py-8 sm:px-6 lg:px-8">
          <div className="mx-auto max-w-5xl">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}

function MenuIcon() {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.75} className="h-5 w-5">
      <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 6.75h16.5M3.75 12h16.5M3.75 17.25h16.5" />
    </svg>
  );
}

function SearchIcon() {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.75} className="h-4 w-4">
      <path strokeLinecap="round" strokeLinejoin="round" d="m21 21-5.2-5.2m0 0a7.2 7.2 0 1 0-10.184 0 7.2 7.2 0 0 0 10.184 0Z" />
    </svg>
  );
}
