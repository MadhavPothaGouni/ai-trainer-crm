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

// ---- Sales: Product ----

export interface ProductDto {
  id: string;
  name: string;
  sku: string | null;
  description: string | null;
  unitPrice: number;
  currency: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateProductRequest {
  name: string;
  sku?: string | null;
  description?: string | null;
  unitPrice: number;
  currency?: string | null;
}

export interface UpdateProductRequest extends CreateProductRequest {
  active: boolean;
}

// ---- Sales: Quote ----

export type QuoteStatus = "DRAFT" | "SENT" | "ACCEPTED" | "REJECTED";

export const QUOTE_STATUSES: QuoteStatus[] = ["DRAFT", "SENT", "ACCEPTED", "REJECTED"];

export interface QuoteLineItemDto {
  id: string;
  productId: string | null;
  description: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface QuoteDto {
  id: string;
  opportunityId: string;
  name: string;
  status: QuoteStatus;
  currency: string | null;
  validUntil: string | null;
  subtotal: number;
  discountAmount: number;
  taxAmount: number;
  totalAmount: number;
  ownerId: string;
  createdAt: string;
  updatedAt: string;
  lineItems: QuoteLineItemDto[];
}

export interface CreateQuoteRequest {
  opportunityId: string;
  name: string;
  currency?: string | null;
  validUntil?: string | null;
  discountAmount?: number | null;
  taxAmount?: number | null;
  ownerId?: string | null;
}

export interface UpdateQuoteRequest {
  name: string;
  currency?: string | null;
  validUntil?: string | null;
  discountAmount?: number | null;
  taxAmount?: number | null;
}

export interface UpdateQuoteStatusRequest {
  status: QuoteStatus;
}

export interface CreateQuoteLineItemRequest {
  productId?: string | null;
  description: string;
  quantity: number;
  unitPrice: number;
}

export type UpdateQuoteLineItemRequest = CreateQuoteLineItemRequest;

// ---- Sales: Order ----

export type OrderStatus = "DRAFT" | "CONFIRMED" | "FULFILLED" | "CANCELLED";

export const ORDER_STATUSES: OrderStatus[] = ["DRAFT", "CONFIRMED", "FULFILLED", "CANCELLED"];

export interface OrderLineItemDto {
  id: string;
  productId: string | null;
  description: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface OrderDto {
  id: string;
  quoteId: string | null;
  orderNumber: string;
  status: OrderStatus;
  currency: string | null;
  subtotal: number;
  discountAmount: number;
  taxAmount: number;
  totalAmount: number;
  createdAt: string;
  updatedAt: string;
  lineItems: OrderLineItemDto[];
}

export interface CreateOrderRequest {
  orderNumber: string;
  currency?: string | null;
  discountAmount?: number | null;
  taxAmount?: number | null;
}

export type UpdateOrderRequest = CreateOrderRequest;

export interface CreateOrderFromQuoteRequest {
  orderNumber: string;
}

export interface UpdateOrderStatusRequest {
  status: OrderStatus;
}

export interface CreateOrderLineItemRequest {
  productId?: string | null;
  description: string;
  quantity: number;
  unitPrice: number;
}

export type UpdateOrderLineItemRequest = CreateOrderLineItemRequest;

// ---- Finance: Invoice ----

export type InvoiceStatus = "DRAFT" | "SENT" | "PAID" | "OVERDUE" | "VOID";

export const INVOICE_STATUSES: InvoiceStatus[] = ["DRAFT", "SENT", "PAID", "OVERDUE", "VOID"];

export interface InvoiceLineItemDto {
  id: string;
  productId: string | null;
  description: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface InvoiceDto {
  id: string;
  orderId: string;
  invoiceNumber: string;
  status: InvoiceStatus;
  currency: string | null;
  issueDate: string;
  dueDate: string;
  subtotal: number;
  discountAmount: number;
  taxAmount: number;
  totalAmount: number;
  amountPaid: number;
  balanceDue: number;
  createdAt: string;
  updatedAt: string;
  lineItems: InvoiceLineItemDto[];
}

export interface GenerateInvoiceRequest {
  invoiceNumber: string;
  issueDate?: string | null;
  dueDate?: string | null;
}

export interface UpdateInvoiceRequest {
  invoiceNumber: string;
  currency?: string | null;
  issueDate: string;
  dueDate: string;
  discountAmount?: number | null;
  taxAmount?: number | null;
}

export interface CreateInvoiceLineItemRequest {
  productId?: string | null;
  description: string;
  quantity: number;
  unitPrice: number;
}

export type UpdateInvoiceLineItemRequest = CreateInvoiceLineItemRequest;

// ---- Finance: Payment ----

export type PaymentMethod = "CREDIT_CARD" | "BANK_TRANSFER" | "CASH" | "CHECK" | "OTHER";

export const PAYMENT_METHODS: PaymentMethod[] = ["CREDIT_CARD", "BANK_TRANSFER", "CASH", "CHECK", "OTHER"];

export interface PaymentDto {
  id: string;
  invoiceId: string;
  amount: number;
  method: PaymentMethod;
  reference: string | null;
  paidAt: string;
  notes: string | null;
  createdAt: string;
}

export interface CreatePaymentRequest {
  amount: number;
  method: PaymentMethod;
  reference?: string | null;
  paidAt?: string | null;
  notes?: string | null;
}

// ---- Marketing: Campaign ----

export type CampaignType = "EMAIL" | "WEBINAR" | "EVENT" | "SOCIAL_MEDIA" | "DIRECT_MAIL" | "OTHER";

export const CAMPAIGN_TYPES: CampaignType[] = ["EMAIL", "WEBINAR", "EVENT", "SOCIAL_MEDIA", "DIRECT_MAIL", "OTHER"];

export type CampaignStatus = "PLANNED" | "ACTIVE" | "COMPLETED" | "CANCELLED";

export const CAMPAIGN_STATUSES: CampaignStatus[] = ["PLANNED", "ACTIVE", "COMPLETED", "CANCELLED"];

export type CampaignMemberStatus = "ADDED" | "SENT" | "OPENED" | "CLICKED" | "RESPONDED" | "CONVERTED";

export const CAMPAIGN_MEMBER_STATUSES: CampaignMemberStatus[] = ["ADDED", "SENT", "OPENED", "CLICKED", "RESPONDED", "CONVERTED"];

export interface CampaignDto {
  id: string;
  name: string;
  type: CampaignType;
  status: CampaignStatus;
  startDate: string | null;
  endDate: string | null;
  budget: number | null;
  actualCost: number | null;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCampaignRequest {
  name: string;
  type: CampaignType;
  startDate?: string | null;
  endDate?: string | null;
  budget?: number | null;
  actualCost?: number | null;
  description?: string | null;
}

export type UpdateCampaignRequest = CreateCampaignRequest;

export interface UpdateCampaignStatusRequest {
  status: CampaignStatus;
}

export interface CampaignMemberDto {
  id: string;
  leadId: string | null;
  contactId: string | null;
  status: CampaignMemberStatus;
  respondedAt: string | null;
  createdAt: string;
}

export interface AddCampaignMemberRequest {
  leadId?: string | null;
  contactId?: string | null;
}

export interface UpdateCampaignMemberStatusRequest {
  status: CampaignMemberStatus;
}

export interface CampaignStatsDto {
  campaignId: string;
  totalMembers: number;
  countsByStatus: Record<CampaignMemberStatus, number>;
}

// ---- Support: Knowledge Article ----

export type KnowledgeArticleStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";

export const KNOWLEDGE_ARTICLE_STATUSES: KnowledgeArticleStatus[] = ["DRAFT", "PUBLISHED", "ARCHIVED"];

export interface KnowledgeArticleDto {
  id: string;
  title: string;
  slug: string;
  category: string | null;
  /** Only populated on the single-article GET - list rows use the summary shape and omit it. */
  content?: string;
  status: KnowledgeArticleStatus;
  viewCount: number;
  publishedAt: string | null;
  tags: string[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateKnowledgeArticleRequest {
  title: string;
  category?: string | null;
  content: string;
  tags?: string[];
}

export type UpdateKnowledgeArticleRequest = CreateKnowledgeArticleRequest;

// ---- Platform extensibility: Custom Objects/Fields ----
// CUSTOM_FIELD/CUSTOM_OBJECT are ORGANIZATION-scope-only permissions (see
// V10's migration comment on the backend) - there's no OWN/TEAM/DEPARTMENT
// variant, so unlike Campaign/KnowledgeArticle these pages don't need to
// account for a caller lacking access at some scopes but not others.

export interface CustomObjectDto {
  id: string;
  apiName: string;
  label: string;
  pluralLabel: string;
  description: string | null;
  active: boolean;
  createdAt: string;
}

export interface CreateCustomObjectRequest {
  apiName: string;
  label: string;
  pluralLabel: string;
  description?: string | null;
}

export interface UpdateCustomObjectRequest {
  label: string;
  pluralLabel: string;
  description?: string | null;
  active: boolean;
}

export interface CustomObjectRecordDto {
  id: string;
  customObjectId: string;
  name: string;
  createdAt: string;
  updatedAt: string;
  values: CustomFieldValueDto[];
}

export interface CreateCustomObjectRecordRequest {
  name: string;
  values?: Record<string, string>;
}

export interface UpdateCustomObjectRecordRequest {
  name: string;
}

/** A fixed allow-list on the backend (CustomField.StandardEntityType) - a custom field can attach to one of these, or to a CustomObject, never both. */
export type StandardEntityType = "ACCOUNT" | "CONTACT" | "LEAD" | "OPPORTUNITY" | "CAMPAIGN";

export const STANDARD_ENTITY_TYPES: StandardEntityType[] = ["ACCOUNT", "CONTACT", "LEAD", "OPPORTUNITY", "CAMPAIGN"];

export type CustomFieldType = "TEXT" | "TEXT_AREA" | "NUMBER" | "DATE" | "BOOLEAN" | "PICKLIST";

export const CUSTOM_FIELD_TYPES: CustomFieldType[] = ["TEXT", "TEXT_AREA", "NUMBER", "DATE", "BOOLEAN", "PICKLIST"];

export interface CustomFieldDto {
  id: string;
  standardEntityType: StandardEntityType | null;
  customObjectId: string | null;
  apiName: string;
  label: string;
  fieldType: CustomFieldType;
  required: boolean;
  displayOrder: number;
  active: boolean;
  picklistValues: string[];
}

export interface CreateCustomFieldRequest {
  standardEntityType?: StandardEntityType | null;
  customObjectId?: string | null;
  apiName: string;
  label: string;
  fieldType: CustomFieldType;
  required?: boolean;
  displayOrder?: number;
  picklistValues?: string[];
}

export interface UpdateCustomFieldRequest {
  label: string;
  required: boolean;
  displayOrder: number;
  active: boolean;
  picklistValues?: string[];
}

/** One field definition joined with its (possibly absent) value on the record just fetched - what CustomFieldValueForm renders one row from. */
export interface CustomFieldValueDto {
  customFieldId: string;
  apiName: string;
  label: string;
  fieldType: CustomFieldType;
  required: boolean;
  value: string | null;
}

export interface SetCustomFieldValuesRequest {
  values: Record<string, string | null>;
}

// ---- Reporting ----

export interface PipelineStageSummaryDto {
  stage: OpportunityStage;
  opportunityCount: number;
  totalAmount: number;
}

export interface LeadFunnelStageDto {
  status: LeadStatus;
  leadCount: number;
}

export interface RepLeaderboardEntryDto {
  ownerId: string;
  ownerName: string;
  openCount: number;
  openAmount: number;
  wonCount: number;
  wonAmount: number;
  lostCount: number;
}

// ---- Platform: API keys ----

export interface ApiKeyDto {
  id: string;
  name: string;
  keyPrefix: string;
  createdByUserId: string;
  lastUsedAt: string | null;
  expiresAt: string | null;
  revokedAt: string | null;
  createdAt: string;
  /** Only ever populated on the response to POST /api/v1/api-keys - see the backend ApiKeyDto's javadoc. */
  rawKey?: string | null;
}

export interface CreateApiKeyRequest {
  name: string;
  expiresAt?: string | null;
}

// ---- Platform: webhooks ----

export interface WebhookSubscriptionDto {
  id: string;
  url: string;
  eventType: string | null;
  secret: string;
  active: boolean;
  createdByUserId: string;
  lastTriggeredAt: string | null;
  lastResponseStatus: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateWebhookSubscriptionRequest {
  url: string;
  eventType?: string | null;
}

export interface UpdateWebhookSubscriptionRequest {
  url: string;
  eventType?: string | null;
  active: boolean;
}

// ---- CRM: shared ----

export interface AssignOwnerRequest {
  ownerId: string;
}
