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
import ApprovalCreatePage from "./pages/approvals/ApprovalCreatePage";
import ApprovalDetailPage from "./pages/approvals/ApprovalDetailPage";
import ApprovalListPage from "./pages/approvals/ApprovalListPage";
import SlaPolicyDetailPage from "./pages/sla/SlaPolicyDetailPage";
import SlaPolicyListPage from "./pages/sla/SlaPolicyListPage";
import TerritoryRuleDetailPage from "./pages/territory/TerritoryRuleDetailPage";
import TerritoryRuleListPage from "./pages/territory/TerritoryRuleListPage";
import PipelineTrendPage from "./pages/forecast/PipelineTrendPage";
import DuplicateMatchListPage from "./pages/dedupe/DuplicateMatchListPage";
import MyGoalsPage from "./pages/salesgoals/MyGoalsPage";
import SalesGoalDetailPage from "./pages/salesgoals/SalesGoalDetailPage";
import SalesGoalListPage from "./pages/salesgoals/SalesGoalListPage";
import LeadScoringRuleDetailPage from "./pages/leadscoring/LeadScoringRuleDetailPage";
import LeadScoringRuleListPage from "./pages/leadscoring/LeadScoringRuleListPage";
import EmailTemplateDetailPage from "./pages/emailtemplates/EmailTemplateDetailPage";
import EmailTemplateListPage from "./pages/emailtemplates/EmailTemplateListPage";
import RegionDetailPage from "./pages/regions/RegionDetailPage";
import RegionListPage from "./pages/regions/RegionListPage";
import CommissionPlanDetailPage from "./pages/commission/CommissionPlanDetailPage";
import CommissionPlanListPage from "./pages/commission/CommissionPlanListPage";
import CommissionRecordListPage from "./pages/commission/CommissionRecordListPage";
import MyCommissionsPage from "./pages/commission/MyCommissionsPage";
import DataSubjectRequestListPage from "./pages/gdpr/DataSubjectRequestListPage";
import CourseCreatePage from "./pages/courses/CourseCreatePage";
import CourseDetailPage from "./pages/courses/CourseDetailPage";
import CourseListPage from "./pages/courses/CourseListPage";
import CertificationCreatePage from "./pages/certifications/CertificationCreatePage";
import CertificationDetailPage from "./pages/certifications/CertificationDetailPage";
import CertificationListPage from "./pages/certifications/CertificationListPage";
import SequenceCreatePage from "./pages/sequences/SequenceCreatePage";
import SequenceDetailPage from "./pages/sequences/SequenceDetailPage";
import SequenceListPage from "./pages/sequences/SequenceListPage";
import BookingLinkCreatePage from "./pages/bookingLinks/BookingLinkCreatePage";
import BookingLinkDetailPage from "./pages/bookingLinks/BookingLinkDetailPage";
import BookingLinkListPage from "./pages/bookingLinks/BookingLinkListPage";
import MacroCreatePage from "./pages/macros/MacroCreatePage";
import MacroDetailPage from "./pages/macros/MacroDetailPage";
import MacroListPage from "./pages/macros/MacroListPage";
import ContractCreatePage from "./pages/contracts/ContractCreatePage";
import ContractDetailPage from "./pages/contracts/ContractDetailPage";
import ContractListPage from "./pages/contracts/ContractListPage";
import ClientGoalCreatePage from "./pages/clientGoals/ClientGoalCreatePage";
import ClientGoalDetailPage from "./pages/clientGoals/ClientGoalDetailPage";
import ClientGoalListPage from "./pages/clientGoals/ClientGoalListPage";
import TrainingSessionCreatePage from "./pages/trainingSessions/TrainingSessionCreatePage";
import TrainingSessionDetailPage from "./pages/trainingSessions/TrainingSessionDetailPage";
import TrainingSessionListPage from "./pages/trainingSessions/TrainingSessionListPage";
import ExerciseCreatePage from "./pages/exercises/ExerciseCreatePage";
import ExerciseDetailPage from "./pages/exercises/ExerciseDetailPage";
import ExerciseListPage from "./pages/exercises/ExerciseListPage";
import NutritionPlanCreatePage from "./pages/nutritionPlans/NutritionPlanCreatePage";
import NutritionPlanDetailPage from "./pages/nutritionPlans/NutritionPlanDetailPage";
import NutritionPlanListPage from "./pages/nutritionPlans/NutritionPlanListPage";
import BodyMeasurementCreatePage from "./pages/bodyMeasurements/BodyMeasurementCreatePage";
import BodyMeasurementDetailPage from "./pages/bodyMeasurements/BodyMeasurementDetailPage";
import BodyMeasurementListPage from "./pages/bodyMeasurements/BodyMeasurementListPage";
import MembershipPlanCreatePage from "./pages/membershipPlans/MembershipPlanCreatePage";
import MembershipPlanDetailPage from "./pages/membershipPlans/MembershipPlanDetailPage";
import MembershipPlanListPage from "./pages/membershipPlans/MembershipPlanListPage";
import MembershipCreatePage from "./pages/memberships/MembershipCreatePage";
import MembershipDetailPage from "./pages/memberships/MembershipDetailPage";
import MembershipListPage from "./pages/memberships/MembershipListPage";
import GroupClassCreatePage from "./pages/groupClasses/GroupClassCreatePage";
import GroupClassDetailPage from "./pages/groupClasses/GroupClassDetailPage";
import GroupClassListPage from "./pages/groupClasses/GroupClassListPage";
import ClassSessionCreatePage from "./pages/classSessions/ClassSessionCreatePage";
import ClassSessionDetailPage from "./pages/classSessions/ClassSessionDetailPage";
import ClassSessionListPage from "./pages/classSessions/ClassSessionListPage";
import EquipmentCreatePage from "./pages/equipment/EquipmentCreatePage";
import EquipmentDetailPage from "./pages/equipment/EquipmentDetailPage";
import EquipmentListPage from "./pages/equipment/EquipmentListPage";
import MaintenanceLogCreatePage from "./pages/maintenanceLogs/MaintenanceLogCreatePage";
import MaintenanceLogDetailPage from "./pages/maintenanceLogs/MaintenanceLogDetailPage";
import MaintenanceLogListPage from "./pages/maintenanceLogs/MaintenanceLogListPage";
import ShiftTemplateCreatePage from "./pages/shiftTemplates/ShiftTemplateCreatePage";
import ShiftTemplateDetailPage from "./pages/shiftTemplates/ShiftTemplateDetailPage";
import ShiftTemplateListPage from "./pages/shiftTemplates/ShiftTemplateListPage";
import ShiftCreatePage from "./pages/shifts/ShiftCreatePage";
import ShiftDetailPage from "./pages/shifts/ShiftDetailPage";
import ShiftListPage from "./pages/shifts/ShiftListPage";
import ReferralCreatePage from "./pages/referrals/ReferralCreatePage";
import ReferralDetailPage from "./pages/referrals/ReferralDetailPage";
import ReferralListPage from "./pages/referrals/ReferralListPage";
import VendorCreatePage from "./pages/vendors/VendorCreatePage";
import VendorDetailPage from "./pages/vendors/VendorDetailPage";
import VendorListPage from "./pages/vendors/VendorListPage";
import PurchaseOrderCreatePage from "./pages/purchaseOrders/PurchaseOrderCreatePage";
import PurchaseOrderDetailPage from "./pages/purchaseOrders/PurchaseOrderDetailPage";
import PurchaseOrderListPage from "./pages/purchaseOrders/PurchaseOrderListPage";
import ClientDocumentCreatePage from "./pages/clientDocuments/ClientDocumentCreatePage";
import ClientDocumentDetailPage from "./pages/clientDocuments/ClientDocumentDetailPage";
import ClientDocumentListPage from "./pages/clientDocuments/ClientDocumentListPage";
import TimeOffRequestCreatePage from "./pages/timeOffRequests/TimeOffRequestCreatePage";
import TimeOffRequestDetailPage from "./pages/timeOffRequests/TimeOffRequestDetailPage";
import TimeOffRequestListPage from "./pages/timeOffRequests/TimeOffRequestListPage";
import LockerCreatePage from "./pages/lockers/LockerCreatePage";
import LockerDetailPage from "./pages/lockers/LockerDetailPage";
import LockerListPage from "./pages/lockers/LockerListPage";
import LockerAssignmentCreatePage from "./pages/lockerAssignments/LockerAssignmentCreatePage";
import LockerAssignmentDetailPage from "./pages/lockerAssignments/LockerAssignmentDetailPage";
import LockerAssignmentListPage from "./pages/lockerAssignments/LockerAssignmentListPage";
import PromoCodeCreatePage from "./pages/promoCodes/PromoCodeCreatePage";
import PromoCodeDetailPage from "./pages/promoCodes/PromoCodeDetailPage";
import PromoCodeListPage from "./pages/promoCodes/PromoCodeListPage";
import PromoRedemptionCreatePage from "./pages/promoRedemptions/PromoRedemptionCreatePage";
import PromoRedemptionDetailPage from "./pages/promoRedemptions/PromoRedemptionDetailPage";
import PromoRedemptionListPage from "./pages/promoRedemptions/PromoRedemptionListPage";
import ClientCheckInCreatePage from "./pages/clientCheckIns/ClientCheckInCreatePage";
import ClientCheckInDetailPage from "./pages/clientCheckIns/ClientCheckInDetailPage";
import ClientCheckInListPage from "./pages/clientCheckIns/ClientCheckInListPage";
import RoomCreatePage from "./pages/rooms/RoomCreatePage";
import RoomDetailPage from "./pages/rooms/RoomDetailPage";
import RoomListPage from "./pages/rooms/RoomListPage";
import RoomBookingCreatePage from "./pages/roomBookings/RoomBookingCreatePage";
import RoomBookingDetailPage from "./pages/roomBookings/RoomBookingDetailPage";
import RoomBookingListPage from "./pages/roomBookings/RoomBookingListPage";
import GiftCardCreatePage from "./pages/giftCards/GiftCardCreatePage";
import GiftCardDetailPage from "./pages/giftCards/GiftCardDetailPage";
import GiftCardListPage from "./pages/giftCards/GiftCardListPage";
import ProgressPhotoCreatePage from "./pages/progressPhotos/ProgressPhotoCreatePage";
import ProgressPhotoDetailPage from "./pages/progressPhotos/ProgressPhotoDetailPage";
import ProgressPhotoListPage from "./pages/progressPhotos/ProgressPhotoListPage";
import EquipmentReservationCreatePage from "./pages/equipmentReservations/EquipmentReservationCreatePage";
import EquipmentReservationDetailPage from "./pages/equipmentReservations/EquipmentReservationDetailPage";
import EquipmentReservationListPage from "./pages/equipmentReservations/EquipmentReservationListPage";
import CompensationRecordCreatePage from "./pages/compensationRecords/CompensationRecordCreatePage";
import CompensationRecordDetailPage from "./pages/compensationRecords/CompensationRecordDetailPage";
import CompensationRecordListPage from "./pages/compensationRecords/CompensationRecordListPage";
import NoShowRecordCreatePage from "./pages/noShowRecords/NoShowRecordCreatePage";
import NoShowRecordDetailPage from "./pages/noShowRecords/NoShowRecordDetailPage";
import NoShowRecordListPage from "./pages/noShowRecords/NoShowRecordListPage";
import LoyaltyTransactionCreatePage from "./pages/loyaltyTransactions/LoyaltyTransactionCreatePage";
import LoyaltyTransactionDetailPage from "./pages/loyaltyTransactions/LoyaltyTransactionDetailPage";
import LoyaltyTransactionListPage from "./pages/loyaltyTransactions/LoyaltyTransactionListPage";
import IntakeFormCreatePage from "./pages/intakeForms/IntakeFormCreatePage";
import IntakeFormDetailPage from "./pages/intakeForms/IntakeFormDetailPage";
import IntakeFormListPage from "./pages/intakeForms/IntakeFormListPage";
import IntakeFormSubmissionCreatePage from "./pages/intakeFormSubmissions/IntakeFormSubmissionCreatePage";
import IntakeFormSubmissionDetailPage from "./pages/intakeFormSubmissions/IntakeFormSubmissionDetailPage";
import IntakeFormSubmissionListPage from "./pages/intakeFormSubmissions/IntakeFormSubmissionListPage";
import ClassWaitlistCreatePage from "./pages/classWaitlists/ClassWaitlistCreatePage";
import ClassWaitlistDetailPage from "./pages/classWaitlists/ClassWaitlistDetailPage";
import ClassWaitlistListPage from "./pages/classWaitlists/ClassWaitlistListPage";
import MembershipFreezeCreatePage from "./pages/membershipFreezes/MembershipFreezeCreatePage";
import MembershipFreezeDetailPage from "./pages/membershipFreezes/MembershipFreezeDetailPage";
import MembershipFreezeListPage from "./pages/membershipFreezes/MembershipFreezeListPage";
import NutritionLogCreatePage from "./pages/nutritionLogs/NutritionLogCreatePage";
import NutritionLogDetailPage from "./pages/nutritionLogs/NutritionLogDetailPage";
import NutritionLogListPage from "./pages/nutritionLogs/NutritionLogListPage";
import PersonalRecordCreatePage from "./pages/personalRecords/PersonalRecordCreatePage";
import PersonalRecordDetailPage from "./pages/personalRecords/PersonalRecordDetailPage";
import PersonalRecordListPage from "./pages/personalRecords/PersonalRecordListPage";
import RefundRecordCreatePage from "./pages/refundRecords/RefundRecordCreatePage";
import RefundRecordDetailPage from "./pages/refundRecords/RefundRecordDetailPage";
import RefundRecordListPage from "./pages/refundRecords/RefundRecordListPage";
import ClientFeedbackCreatePage from "./pages/clientFeedback/ClientFeedbackCreatePage";
import ClientFeedbackDetailPage from "./pages/clientFeedback/ClientFeedbackDetailPage";
import ClientFeedbackListPage from "./pages/clientFeedback/ClientFeedbackListPage";
import AttachmentDetailPage from "./pages/attachments/AttachmentDetailPage";
import AttachmentListPage from "./pages/attachments/AttachmentListPage";
import AttachmentUploadPage from "./pages/attachments/AttachmentUploadPage";
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
import NotificationsPage from "./pages/notifications/NotificationsPage";
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

          <Route path="/attachments" element={<AttachmentListPage />} />
          <Route path="/attachments/new" element={<AttachmentUploadPage />} />
          <Route path="/attachments/:attachmentId" element={<AttachmentDetailPage />} />

          <Route path="/approvals" element={<ApprovalListPage />} />
          <Route path="/approvals/new" element={<ApprovalCreatePage />} />
          <Route path="/approvals/:requestId" element={<ApprovalDetailPage />} />

          <Route path="/sla-policies" element={<SlaPolicyListPage />} />
          <Route path="/sla-policies/:policyId" element={<SlaPolicyDetailPage />} />
          <Route path="/territory-rules" element={<TerritoryRuleListPage />} />
          <Route path="/territory-rules/:ruleId" element={<TerritoryRuleDetailPage />} />
          <Route path="/forecast" element={<PipelineTrendPage />} />
          <Route path="/duplicates" element={<DuplicateMatchListPage />} />
          <Route path="/lead-scoring-rules" element={<LeadScoringRuleListPage />} />
          <Route path="/lead-scoring-rules/:ruleId" element={<LeadScoringRuleDetailPage />} />
          <Route path="/my-goals" element={<MyGoalsPage />} />
          <Route path="/sales-goals" element={<SalesGoalListPage />} />
          <Route path="/sales-goals/:goalId" element={<SalesGoalDetailPage />} />
          <Route path="/email-templates" element={<EmailTemplateListPage />} />
          <Route path="/email-templates/:templateId" element={<EmailTemplateDetailPage />} />
          <Route path="/regions" element={<RegionListPage />} />
          <Route path="/regions/:regionId" element={<RegionDetailPage />} />
          <Route path="/my-commissions" element={<MyCommissionsPage />} />
          <Route path="/commission-plans" element={<CommissionPlanListPage />} />
          <Route path="/commission-plans/:planId" element={<CommissionPlanDetailPage />} />
          <Route path="/commission-records" element={<CommissionRecordListPage />} />
          <Route path="/data-subject-requests" element={<DataSubjectRequestListPage />} />
          <Route path="/courses" element={<CourseListPage />} />
          <Route path="/courses/new" element={<CourseCreatePage />} />
          <Route path="/courses/:courseId" element={<CourseDetailPage />} />
          <Route path="/certifications" element={<CertificationListPage />} />
          <Route path="/certifications/new" element={<CertificationCreatePage />} />
          <Route path="/certifications/:certificationId" element={<CertificationDetailPage />} />
          <Route path="/sequences" element={<SequenceListPage />} />
          <Route path="/sequences/new" element={<SequenceCreatePage />} />
          <Route path="/sequences/:sequenceId" element={<SequenceDetailPage />} />
          <Route path="/booking-links" element={<BookingLinkListPage />} />
          <Route path="/booking-links/new" element={<BookingLinkCreatePage />} />
          <Route path="/booking-links/:bookingLinkId" element={<BookingLinkDetailPage />} />
          <Route path="/macros" element={<MacroListPage />} />
          <Route path="/macros/new" element={<MacroCreatePage />} />
          <Route path="/macros/:macroId" element={<MacroDetailPage />} />
          <Route path="/contracts" element={<ContractListPage />} />
          <Route path="/contracts/new" element={<ContractCreatePage />} />
          <Route path="/contracts/:contractId" element={<ContractDetailPage />} />
          <Route path="/client-goals" element={<ClientGoalListPage />} />
          <Route path="/client-goals/new" element={<ClientGoalCreatePage />} />
          <Route path="/client-goals/:clientGoalId" element={<ClientGoalDetailPage />} />
          <Route path="/training-sessions" element={<TrainingSessionListPage />} />
          <Route path="/training-sessions/new" element={<TrainingSessionCreatePage />} />
          <Route path="/training-sessions/:trainingSessionId" element={<TrainingSessionDetailPage />} />
          <Route path="/exercises" element={<ExerciseListPage />} />
          <Route path="/exercises/new" element={<ExerciseCreatePage />} />
          <Route path="/exercises/:exerciseId" element={<ExerciseDetailPage />} />
          <Route path="/nutrition-plans" element={<NutritionPlanListPage />} />
          <Route path="/nutrition-plans/new" element={<NutritionPlanCreatePage />} />
          <Route path="/nutrition-plans/:nutritionPlanId" element={<NutritionPlanDetailPage />} />
          <Route path="/body-measurements" element={<BodyMeasurementListPage />} />
          <Route path="/body-measurements/new" element={<BodyMeasurementCreatePage />} />
          <Route path="/body-measurements/:bodyMeasurementId" element={<BodyMeasurementDetailPage />} />
          <Route path="/membership-plans" element={<MembershipPlanListPage />} />
          <Route path="/membership-plans/new" element={<MembershipPlanCreatePage />} />
          <Route path="/membership-plans/:membershipPlanId" element={<MembershipPlanDetailPage />} />
          <Route path="/memberships" element={<MembershipListPage />} />
          <Route path="/memberships/new" element={<MembershipCreatePage />} />
          <Route path="/memberships/:membershipId" element={<MembershipDetailPage />} />
          <Route path="/group-classes" element={<GroupClassListPage />} />
          <Route path="/group-classes/new" element={<GroupClassCreatePage />} />
          <Route path="/group-classes/:groupClassId" element={<GroupClassDetailPage />} />
          <Route path="/class-sessions" element={<ClassSessionListPage />} />
          <Route path="/class-sessions/new" element={<ClassSessionCreatePage />} />
          <Route path="/class-sessions/:classSessionId" element={<ClassSessionDetailPage />} />
          <Route path="/equipment" element={<EquipmentListPage />} />
          <Route path="/equipment/new" element={<EquipmentCreatePage />} />
          <Route path="/equipment/:equipmentId" element={<EquipmentDetailPage />} />
          <Route path="/maintenance-logs" element={<MaintenanceLogListPage />} />
          <Route path="/maintenance-logs/new" element={<MaintenanceLogCreatePage />} />
          <Route path="/maintenance-logs/:maintenanceLogId" element={<MaintenanceLogDetailPage />} />
          <Route path="/shift-templates" element={<ShiftTemplateListPage />} />
          <Route path="/shift-templates/new" element={<ShiftTemplateCreatePage />} />
          <Route path="/shift-templates/:shiftTemplateId" element={<ShiftTemplateDetailPage />} />
          <Route path="/shifts" element={<ShiftListPage />} />
          <Route path="/shifts/new" element={<ShiftCreatePage />} />
          <Route path="/shifts/:shiftId" element={<ShiftDetailPage />} />
          <Route path="/referrals" element={<ReferralListPage />} />
          <Route path="/referrals/new" element={<ReferralCreatePage />} />
          <Route path="/referrals/:referralId" element={<ReferralDetailPage />} />
          <Route path="/time-off-requests" element={<TimeOffRequestListPage />} />
          <Route path="/time-off-requests/new" element={<TimeOffRequestCreatePage />} />
          <Route path="/time-off-requests/:timeOffRequestId" element={<TimeOffRequestDetailPage />} />
          <Route path="/lockers" element={<LockerListPage />} />
          <Route path="/lockers/new" element={<LockerCreatePage />} />
          <Route path="/lockers/:lockerId" element={<LockerDetailPage />} />
          <Route path="/locker-assignments" element={<LockerAssignmentListPage />} />
          <Route path="/locker-assignments/new" element={<LockerAssignmentCreatePage />} />
          <Route path="/locker-assignments/:lockerAssignmentId" element={<LockerAssignmentDetailPage />} />
          <Route path="/promo-codes" element={<PromoCodeListPage />} />
          <Route path="/promo-codes/new" element={<PromoCodeCreatePage />} />
          <Route path="/promo-codes/:promoCodeId" element={<PromoCodeDetailPage />} />
          <Route path="/promo-redemptions" element={<PromoRedemptionListPage />} />
          <Route path="/promo-redemptions/new" element={<PromoRedemptionCreatePage />} />
          <Route path="/promo-redemptions/:promoRedemptionId" element={<PromoRedemptionDetailPage />} />
          <Route path="/client-check-ins" element={<ClientCheckInListPage />} />
          <Route path="/client-check-ins/new" element={<ClientCheckInCreatePage />} />
          <Route path="/client-check-ins/:clientCheckInId" element={<ClientCheckInDetailPage />} />
          <Route path="/rooms" element={<RoomListPage />} />
          <Route path="/rooms/new" element={<RoomCreatePage />} />
          <Route path="/rooms/:roomId" element={<RoomDetailPage />} />
          <Route path="/room-bookings" element={<RoomBookingListPage />} />
          <Route path="/room-bookings/new" element={<RoomBookingCreatePage />} />
          <Route path="/room-bookings/:roomBookingId" element={<RoomBookingDetailPage />} />
          <Route path="/gift-cards" element={<GiftCardListPage />} />
          <Route path="/gift-cards/new" element={<GiftCardCreatePage />} />
          <Route path="/gift-cards/:giftCardId" element={<GiftCardDetailPage />} />
          <Route path="/progress-photos" element={<ProgressPhotoListPage />} />
          <Route path="/progress-photos/new" element={<ProgressPhotoCreatePage />} />
          <Route path="/progress-photos/:progressPhotoId" element={<ProgressPhotoDetailPage />} />
          <Route path="/equipment-reservations" element={<EquipmentReservationListPage />} />
          <Route path="/equipment-reservations/new" element={<EquipmentReservationCreatePage />} />
          <Route path="/equipment-reservations/:equipmentReservationId" element={<EquipmentReservationDetailPage />} />
          <Route path="/compensation-records" element={<CompensationRecordListPage />} />
          <Route path="/compensation-records/new" element={<CompensationRecordCreatePage />} />
          <Route path="/compensation-records/:compensationRecordId" element={<CompensationRecordDetailPage />} />
          <Route path="/no-show-records" element={<NoShowRecordListPage />} />
          <Route path="/no-show-records/new" element={<NoShowRecordCreatePage />} />
          <Route path="/no-show-records/:noShowRecordId" element={<NoShowRecordDetailPage />} />
          <Route path="/loyalty-transactions" element={<LoyaltyTransactionListPage />} />
          <Route path="/loyalty-transactions/new" element={<LoyaltyTransactionCreatePage />} />
          <Route path="/loyalty-transactions/:loyaltyTransactionId" element={<LoyaltyTransactionDetailPage />} />
          <Route path="/intake-forms" element={<IntakeFormListPage />} />
          <Route path="/intake-forms/new" element={<IntakeFormCreatePage />} />
          <Route path="/intake-forms/:intakeFormId" element={<IntakeFormDetailPage />} />
          <Route path="/intake-form-submissions" element={<IntakeFormSubmissionListPage />} />
          <Route path="/intake-form-submissions/new" element={<IntakeFormSubmissionCreatePage />} />
          <Route path="/intake-form-submissions/:intakeFormSubmissionId" element={<IntakeFormSubmissionDetailPage />} />
          <Route path="/class-waitlists" element={<ClassWaitlistListPage />} />
          <Route path="/class-waitlists/new" element={<ClassWaitlistCreatePage />} />
          <Route path="/class-waitlists/:classWaitlistId" element={<ClassWaitlistDetailPage />} />
          <Route path="/membership-freezes" element={<MembershipFreezeListPage />} />
          <Route path="/membership-freezes/new" element={<MembershipFreezeCreatePage />} />
          <Route path="/membership-freezes/:membershipFreezeId" element={<MembershipFreezeDetailPage />} />
          <Route path="/nutrition-logs" element={<NutritionLogListPage />} />
          <Route path="/nutrition-logs/new" element={<NutritionLogCreatePage />} />
          <Route path="/nutrition-logs/:nutritionLogId" element={<NutritionLogDetailPage />} />
          <Route path="/personal-records" element={<PersonalRecordListPage />} />
          <Route path="/personal-records/new" element={<PersonalRecordCreatePage />} />
          <Route path="/personal-records/:personalRecordId" element={<PersonalRecordDetailPage />} />
          <Route path="/refund-records" element={<RefundRecordListPage />} />
          <Route path="/refund-records/new" element={<RefundRecordCreatePage />} />
          <Route path="/refund-records/:refundRecordId" element={<RefundRecordDetailPage />} />
          <Route path="/client-feedback" element={<ClientFeedbackListPage />} />
          <Route path="/client-feedback/new" element={<ClientFeedbackCreatePage />} />
          <Route path="/client-feedback/:clientFeedbackId" element={<ClientFeedbackDetailPage />} />
          <Route path="/vendors" element={<VendorListPage />} />
          <Route path="/vendors/new" element={<VendorCreatePage />} />
          <Route path="/vendors/:vendorId" element={<VendorDetailPage />} />
          <Route path="/purchase-orders" element={<PurchaseOrderListPage />} />
          <Route path="/purchase-orders/new" element={<PurchaseOrderCreatePage />} />
          <Route path="/purchase-orders/:purchaseOrderId" element={<PurchaseOrderDetailPage />} />
          <Route path="/client-documents" element={<ClientDocumentListPage />} />
          <Route path="/client-documents/new" element={<ClientDocumentCreatePage />} />
          <Route path="/client-documents/:clientDocumentId" element={<ClientDocumentDetailPage />} />

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

          <Route path="/notifications" element={<NotificationsPage />} />

          <Route path="/profile" element={<ProfilePage />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
