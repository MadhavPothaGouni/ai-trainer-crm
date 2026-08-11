// Mirrors the backend's response envelopes exactly (see
// backend/crm-platform/.../common/dto/{ApiResponse,ErrorResponse,PageResponse}.java).
// Keeping these in one file makes it obvious when the frontend's assumptions
// about the wire format drift from what the backend actually sends.

export interface ApiResponse<T> {
  success: boolean;
  data: T | null;
  message: string | null;
  timestamp: string;
}

export interface FieldError {
  field: string;
  message: string;
  rejectedValue: unknown;
}

export interface ApiErrorBody {
  success: false;
  errorCode: string;
  message: string;
  status: number;
  path: string;
  timestamp: string;
  fieldErrors: FieldError[] | null;
  traceId: string;
}

export interface PageResponse<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

// ---- Auth ----

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  organizationName?: string | null;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

export interface VerifyEmailRequest {
  token: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  userId: string;
  email: string;
  fullName: string;
}

// ---- Organization ----

export interface OrganizationDto {
  id: string;
  name: string;
  slug: string;
  defaultCurrency: string;
  timezone: string;
  fiscalYearStartMonth: number;
}

export interface UpdateOrganizationRequest {
  name: string;
  defaultCurrency?: string;
  timezone?: string;
  fiscalYearStartMonth: number;
}

// ---- User ----

export type UserStatus = "PENDING_VERIFICATION" | "ACTIVE" | "SUSPENDED" | "DEACTIVATED";

export interface UserDto {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  fullName: string;
  phone: string | null;
  avatarUrl: string | null;
  timezone: string | null;
  locale: string | null;
  status: UserStatus;
  emailVerified: boolean;
  mfaEnabled: boolean;
  teamId: string | null;
  managerId: string | null;
  roles: string[];
  lastLoginAt: string | null;
  createdAt: string;
}

export interface UpdateProfileRequest {
  firstName: string;
  lastName: string;
  phone?: string;
  timezone?: string;
  locale?: string;
}

export interface CreateUserRequest {
  email: string;
  firstName: string;
  lastName: string;
  roleIds?: string[] | null;
}

export interface UpdateUserRolesRequest {
  roleIds: string[];
}

export interface UpdateUserStatusRequest {
  status: UserStatus;
}

// ---- Role / Permission ----

export interface PermissionDto {
  id: string;
  resource: string;
  action: string;
  scope: string;
  description: string;
  authorityName: string;
}

export interface RoleDto {
  id: string;
  name: string;
  description: string | null;
  systemRole: boolean;
  permissions: PermissionDto[];
}

export interface CreateRoleRequest {
  name: string;
  description?: string;
  permissionIds?: string[] | null;
}

export interface UpdateRoleRequest {
  name: string;
  description?: string;
  permissionIds?: string[] | null;
}

// ---- CRM: Account ----

export interface AccountDto {
  id: string;
  name: string;
  industry: string | null;
  website: string | null;
  phone: string | null;
  billingStreet: string | null;
  billingCity: string | null;
  billingState: string | null;
  billingPostalCode: string | null;
  billingCountry: string | null;
  annualRevenue: number | null;
  employeeCount: number | null;
  description: string | null;
  ownerId: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateAccountRequest {
  name: string;
  industry?: string | null;
  website?: string | null;
  phone?: string | null;
  billingStreet?: string | null;
  billingCity?: string | null;
  billingState?: string | null;
  billingPostalCode?: string | null;
  billingCountry?: string | null;
  annualRevenue?: number | null;
  employeeCount?: number | null;
  description?: string | null;
  ownerId?: string | null;
}

export type UpdateAccountRequest = CreateAccountRequest;

// ---- CRM: Contact ----

export interface ContactDto {
  id: string;
  firstName: string;
  lastName: string;
  fullName: string;
  email: string | null;
  phone: string | null;
  title: string | null;
  description: string | null;
  accountId: string | null;
  ownerId: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateContactRequest {
  firstName: string;
  lastName: string;
  email?: string | null;
  phone?: string | null;
  title?: string | null;
  description?: string | null;
  accountId?: string | null;
  ownerId?: string | null;
}

export type UpdateContactRequest = CreateContactRequest;

// ---- CRM: Opportunity ----

export type OpportunityStage =
  | "PROSPECTING"
  | "QUALIFICATION"
  | "PROPOSAL"
  | "NEGOTIATION"
  | "CLOSED_WON"
  | "CLOSED_LOST";

export const OPPORTUNITY_STAGES: OpportunityStage[] = [
  "PROSPECTING",
  "QUALIFICATION",
  "PROPOSAL",
  "NEGOTIATION",
  "CLOSED_WON",
  "CLOSED_LOST",
];

export interface OpportunityDto {
  id: string;
  accountId: string;
  primaryContactId: string | null;
  name: string;
  stage: OpportunityStage;
  amount: number | null;
  currency: string | null;
  expectedCloseDate: string | null;
  actualCloseDate: string | null;
  description: string | null;
  ownerId: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateOpportunityRequest {
  accountId: string;
  primaryContactId?: string | null;
  name: string;
  amount?: number | null;
  currency?: string | null;
  expectedCloseDate?: string | null;
  description?: string | null;
  ownerId?: string | null;
}

export type UpdateOpportunityRequest = CreateOpportunityRequest;

export interface UpdateOpportunityStageRequest {
  stage: OpportunityStage;
}

// ---- CRM: Lead ----

export type LeadStatus = "NEW" | "CONTACTED" | "QUALIFIED" | "UNQUALIFIED" | "CONVERTED";

export const LEAD_STATUSES: LeadStatus[] = ["NEW", "CONTACTED", "QUALIFIED", "UNQUALIFIED", "CONVERTED"];

export type LeadSource = "WEBSITE" | "REFERRAL" | "COLD_CALL" | "EVENT" | "ADVERTISEMENT" | "OTHER";

export const LEAD_SOURCES: LeadSource[] = ["WEBSITE", "REFERRAL", "COLD_CALL", "EVENT", "ADVERTISEMENT", "OTHER"];

export interface LeadDto {
  id: string;
  firstName: string;
  lastName: string;
  fullName: string;
  email: string | null;
  phone: string | null;
  companyName: string | null;
  title: string | null;
  status: LeadStatus;
  source: LeadSource;
  description: string | null;
  ownerId: string;
  convertedAccountId: string | null;
  convertedContactId: string | null;
  convertedOpportunityId: string | null;
  convertedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateLeadRequest {
  firstName: string;
  lastName: string;
  email?: string | null;
  phone?: string | null;
  companyName?: string | null;
  title?: string | null;
  source: LeadSource;
  description?: string | null;
  ownerId?: string | null;
}

export type UpdateLeadRequest = CreateLeadRequest;

export interface UpdateLeadStatusRequest {
  status: LeadStatus;
}

export interface ConvertLeadRequest {
  existingAccountId?: string | null;
  newAccountName?: string | null;
  createOpportunity?: boolean | null;
  opportunityName?: string | null;
  opportunityAmount?: number | null;
  opportunityExpectedCloseDate?: string | null;
}

export interface LeadConversionResult {
  leadId: string;
  accountId: string;
  contactId: string;
  opportunityId: string | null;
}

// ---- CRM: Activity ----

export type ActivityType = "CALL" | "EMAIL" | "MEETING" | "TASK" | "NOTE";

export const ACTIVITY_TYPES: ActivityType[] = ["CALL", "EMAIL", "MEETING", "TASK", "NOTE"];

export type ActivityStatus = "OPEN" | "COMPLETED";

export type ActivityPriority = "LOW" | "MEDIUM" | "HIGH";

export const ACTIVITY_PRIORITIES: ActivityPriority[] = ["LOW", "MEDIUM", "HIGH"];

export type RelatedToType = "ACCOUNT" | "CONTACT" | "OPPORTUNITY" | "LEAD";

export interface ActivityDto {
  id: string;
  type: ActivityType;
  subject: string;
  description: string | null;
  status: ActivityStatus;
  priority: ActivityPriority | null;
  dueAt: string | null;
  completedAt: string | null;
  relatedToType: RelatedToType;
  relatedToId: string;
  ownerId: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateActivityRequest {
  type: ActivityType;
  subject: string;
  description?: string | null;
  priority?: ActivityPriority | null;
  dueAt?: string | null;
  relatedToType: RelatedToType;
  relatedToId: string;
  ownerId?: string | null;
}

export type UpdateActivityRequest = CreateActivityRequest;

export interface UpdateActivityStatusRequest {
  status: ActivityStatus;
}

// ---- CRM: shared ----

export interface AssignOwnerRequest {
  ownerId: string;
}
