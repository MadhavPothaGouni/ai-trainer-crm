import { Route, Routes } from "react-router-dom";
import { AppLayout } from "./components/layout/AppLayout";
import { ProtectedRoute, PublicOnlyRoute } from "./auth/ProtectedRoute";
import DashboardPage from "./pages/DashboardPage";
import NotFoundPage from "./pages/NotFoundPage";
import ForgotPasswordPage from "./pages/auth/ForgotPasswordPage";
import LoginPage from "./pages/auth/LoginPage";
import RegisterPage from "./pages/auth/RegisterPage";
import ResetPasswordPage from "./pages/auth/ResetPasswordPage";
import VerifyEmailPage from "./pages/auth/VerifyEmailPage";
import AccountCreatePage from "./pages/accounts/AccountCreatePage";
import AccountDetailPage from "./pages/accounts/AccountDetailPage";
import AccountListPage from "./pages/accounts/AccountListPage";
import ApiKeysPage from "./pages/apikeys/ApiKeysPage";
import CampaignCreatePage from "./pages/campaigns/CampaignCreatePage";
import CampaignDetailPage from "./pages/campaigns/CampaignDetailPage";
import CampaignListPage from "./pages/campaigns/CampaignListPage";
import CalendarEventCreatePage from "./pages/calendar/CalendarEventCreatePage";
import CalendarEventDetailPage from "./pages/calendar/CalendarEventDetailPage";
import CalendarEventListPage from "./pages/calendar/CalendarEventListPage";
import ContactCreatePage from "./pages/contacts/ContactCreatePage";
import ContactDetailPage from "./pages/contacts/ContactDetailPage";
import ContactListPage from "./pages/contacts/ContactListPage";
import EmailCreatePage from "./pages/emails/EmailCreatePage";
import EmailDetailPage from "./pages/emails/EmailDetailPage";
import EmailListPage from "./pages/emails/EmailListPage";
import DashboardCreatePage from "./pages/dashboards/DashboardCreatePage";
import DashboardDetailPage from "./pages/dashboards/DashboardDetailPage";
import DashboardListPage from "./pages/dashboards/DashboardListPage";
import CustomFieldCreatePage from "./pages/customfields/CustomFieldCreatePage";
import CustomFieldListPage from "./pages/customfields/CustomFieldListPage";
import CustomObjectCreatePage from "./pages/customobjects/CustomObjectCreatePage";
import CustomObjectDetailPage from "./pages/customobjects/CustomObjectDetailPage";
import CustomObjectListPage from "./pages/customobjects/CustomObjectListPage";
import CustomObjectRecordDetailPage from "./pages/customobjects/CustomObjectRecordDetailPage";
import ImportExportPage from "./pages/importexport/ImportExportPage";
import InvoiceDetailPage from "./pages/invoices/InvoiceDetailPage";
import InvoiceListPage from "./pages/invoices/InvoiceListPage";
import KnowledgeArticleCreatePage from "./pages/knowledge/KnowledgeArticleCreatePage";
import KnowledgeArticleDetailPage from "./pages/knowledge/KnowledgeArticleDetailPage";
import KnowledgeArticleListPage from "./pages/knowledge/KnowledgeArticleListPage";
import OpportunityCreatePage from "./pages/opportunities/OpportunityCreatePage";
import OpportunityDetailPage from "./pages/opportunities/OpportunityDetailPage";
import OpportunityListPage from "./pages/opportunities/OpportunityListPage";
import OrderCreatePage from "./pages/orders/OrderCreatePage";
import OrderDetailPage from "./pages/orders/OrderDetailPage";
import OrderListPage from "./pages/orders/OrderListPage";
import ProductCreatePage from "./pages/products/ProductCreatePage";
import ProductDetailPage from "./pages/products/ProductDetailPage";
import ProductListPage from "./pages/products/ProductListPage";
import ProfilePage from "./pages/profile/ProfilePage";
import QuoteCreatePage from "./pages/quotes/QuoteCreatePage";
import QuoteDetailPage from "./pages/quotes/QuoteDetailPage";
import QuoteListPage from "./pages/quotes/QuoteListPage";
import ReportsPage from "./pages/reports/ReportsPage";
import MyTasksPage from "./pages/tasks/MyTasksPage";
import TicketCreatePage from "./pages/tickets/TicketCreatePage";
import TicketDetailPage from "./pages/tickets/TicketDetailPage";
import TicketListPage from "./pages/tickets/TicketListPage";
import LeadCreatePage from "./pages/leads/LeadCreatePage";
import LeadDetailPage from "./pages/leads/LeadDetailPage";
import LeadListPage from "./pages/leads/LeadListPage";
import RoleCreatePage from "./pages/roles/RoleCreatePage";
import RoleDetailPage from "./pages/roles/RoleDetailPage";
import RoleListPage from "./pages/roles/RoleListPage";
import TeamCreatePage from "./pages/teams/TeamCreatePage";
import TeamDetailPage from "./pages/teams/TeamDetailPage";
import TeamListPage from "./pages/teams/TeamListPage";
import UserDetailPage from "./pages/users/UserDetailPage";
import UserInvitePage from "./pages/users/UserInvitePage";
import UserListPage from "./pages/users/UserListPage";
import WebhooksPage from "./pages/webhooks/WebhooksPage";
import WorkflowCreatePage from "./pages/workflows/WorkflowCreatePage";
import WorkflowDetailPage from "./pages/workflows/WorkflowDetailPage";
import WorkflowListPage from "./pages/workflows/WorkflowListPage";

export default function App() {
  return (
    <Routes>
      <Route element={<PublicOnlyRoute />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
      </Route>

      {/* Not gated by PublicOnlyRoute: a signed-in user following a fresh verification
          link from their inbox should still be able to see the result, not get bounced
          straight to the dashboard. */}
      <Route path="/verify-email" element={<VerifyEmailPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route path="/" element={<DashboardPage />} />

          <Route path="/tasks" element={<MyTasksPage />} />

          <Route path="/accounts" element={<AccountListPage />} />
          <Route path="/accounts/new" element={<AccountCreatePage />} />
          <Route path="/accounts/:accountId" element={<AccountDetailPage />} />

          <Route path="/contacts" element={<ContactListPage />} />
          <Route path="/contacts/new" element={<ContactCreatePage />} />
          <Route path="/contacts/:contactId" element={<ContactDetailPage />} />

          <Route path="/opportunities" element={<OpportunityListPage />} />
          <Route path="/opportunities/new" element={<OpportunityCreatePage />} />
          <Route path="/opportunities/:opportunityId" element={<OpportunityDetailPage />} />

          <Route path="/leads" element={<LeadListPage />} />
          <Route path="/leads/new" element={<LeadCreatePage />} />
          <Route path="/leads/:leadId" element={<LeadDetailPage />} />

          <Route path="/tickets" element={<TicketListPage />} />
          <Route path="/tickets/new" element={<TicketCreatePage />} />
          <Route path="/tickets/:ticketId" element={<TicketDetailPage />} />

          <Route path="/emails" element={<EmailListPage />} />
          <Route path="/emails/new" element={<EmailCreatePage />} />
          <Route path="/emails/:emailId" element={<EmailDetailPage />} />

          <Route path="/calendar" element={<CalendarEventListPage />} />
          <Route path="/calendar/new" element={<CalendarEventCreatePage />} />
          <Route path="/calendar/:eventId" element={<CalendarEventDetailPage />} />

          <Route path="/products" element={<ProductListPage />} />
          <Route path="/products/new" element={<ProductCreatePage />} />
          <Route path="/products/:productId" element={<ProductDetailPage />} />

          <Route path="/quotes" element={<QuoteListPage />} />
          <Route path="/quotes/new" element={<QuoteCreatePage />} />
          <Route path="/quotes/:quoteId" element={<QuoteDetailPage />} />

          <Route path="/orders" element={<OrderListPage />} />
          <Route path="/orders/new" element={<OrderCreatePage />} />
          <Route path="/orders/:orderId" element={<OrderDetailPage />} />

          <Route path="/invoices" element={<InvoiceListPage />} />
          <Route path="/invoices/:invoiceId" element={<InvoiceDetailPage />} />

          <Route path="/campaigns" element={<CampaignListPage />} />
          <Route path="/campaigns/new" element={<CampaignCreatePage />} />
          <Route path="/campaigns/:campaignId" element={<CampaignDetailPage />} />

          <Route path="/knowledge-articles" element={<KnowledgeArticleListPage />} />
          <Route path="/knowledge-articles/new" element={<KnowledgeArticleCreatePage />} />
          <Route path="/knowledge-articles/:articleId" element={<KnowledgeArticleDetailPage />} />

          <Route path="/custom-objects" element={<CustomObjectListPage />} />
          <Route path="/custom-objects/new" element={<CustomObjectCreatePage />} />
          <Route path="/custom-objects/:customObjectId" element={<CustomObjectDetailPage />} />
          <Route path="/custom-objects/:customObjectId/records/:recordId" element={<CustomObjectRecordDetailPage />} />

          <Route path="/custom-fields" element={<CustomFieldListPage />} />
          <Route path="/custom-fields/new" element={<CustomFieldCreatePage />} />

          <Route path="/dashboards" element={<DashboardListPage />} />
          <Route path="/dashboards/new" element={<DashboardCreatePage />} />
          <Route path="/dashboards/:dashboardId" element={<DashboardDetailPage />} />

          <Route path="/reports" element={<ReportsPage />} />

          <Route path="/import-export" element={<ImportExportPage />} />

          <Route path="/users" element={<UserListPage />} />
          <Route path="/users/invite" element={<UserInvitePage />} />
          <Route path="/users/:userId" element={<UserDetailPage />} />

          <Route path="/roles" element={<RoleListPage />} />
          <Route path="/roles/new" element={<RoleCreatePage />} />
          <Route path="/roles/:roleId" element={<RoleDetailPage />} />

          <Route path="/teams" element={<TeamListPage />} />
          <Route path="/teams/new" element={<TeamCreatePage />} />
          <Route path="/teams/:teamId" element={<TeamDetailPage />} />

          <Route path="/api-keys" element={<ApiKeysPage />} />
          <Route path="/webhooks" element={<WebhooksPage />} />

          <Route path="/workflows" element={<WorkflowListPage />} />
          <Route path="/workflows/new" element={<WorkflowCreatePage />} />
          <Route path="/workflows/:workflowId" element={<WorkflowDetailPage />} />

          <Route path="/profile" element={<ProfilePage />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
