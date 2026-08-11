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
import ContactCreatePage from "./pages/contacts/ContactCreatePage";
import ContactDetailPage from "./pages/contacts/ContactDetailPage";
import ContactListPage from "./pages/contacts/ContactListPage";
import OpportunityCreatePage from "./pages/opportunities/OpportunityCreatePage";
import OpportunityDetailPage from "./pages/opportunities/OpportunityDetailPage";
import OpportunityListPage from "./pages/opportunities/OpportunityListPage";
import ProfilePage from "./pages/profile/ProfilePage";
import MyTasksPage from "./pages/tasks/MyTasksPage";
import LeadCreatePage from "./pages/leads/LeadCreatePage";
import LeadDetailPage from "./pages/leads/LeadDetailPage";
import LeadListPage from "./pages/leads/LeadListPage";
import RoleCreatePage from "./pages/roles/RoleCreatePage";
import RoleDetailPage from "./pages/roles/RoleDetailPage";
import RoleListPage from "./pages/roles/RoleListPage";
import UserDetailPage from "./pages/users/UserDetailPage";
import UserInvitePage from "./pages/users/UserInvitePage";
import UserListPage from "./pages/users/UserListPage";

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

          <Route path="/users" element={<UserListPage />} />
          <Route path="/users/invite" element={<UserInvitePage />} />
          <Route path="/users/:userId" element={<UserDetailPage />} />

          <Route path="/roles" element={<RoleListPage />} />
          <Route path="/roles/new" element={<RoleCreatePage />} />
          <Route path="/roles/:roleId" element={<RoleDetailPage />} />

          <Route path="/profile" element={<ProfilePage />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
