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

/** teamId is deliberately optional/nullable, not required - null unassigns rather than being rejected as missing input. See UpdateUserTeamRequest's backend javadoc. */
export interface UpdateUserTeamRequest {
  teamId?: string | null;
}

// ---- Organization: Team ----
//
// Team/teams.team_id existed since the platform's very first migration
// purely so ScopeAuthorizationService had something to resolve TEAM/
// DEPARTMENT-scope visibility against - there was no management API for a
// long time, so in practice no user ever had a team. TeamController (CRUD)
// and PATCH /users/{id}/team (assignment, above) closed that gap - see
// backend/crm-platform/README.md's module layout for `organization`/`user`.

export interface TeamDto {
  id: string;
  name: string;
  department: string | null;
  leadUserId: string | null;
}

export interface CreateTeamRequest {
  name: string;
  department?: string | null;
  leadUserId?: string | null;
}

export type UpdateTeamRequest = CreateTeamRequest;

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

// ---- CRM: Ticket ----
// The resource that had a full permission set seeded (V2__seed_permission_catalog.sql) but no
// module anywhere until this session found the gap while building bulk import/export - see
// backend/crm-platform/README.md's module layout for `ticket`. Status is a free (non-linear)
// transition, unlike Lead's one-way CONVERTED - reopening a resolved ticket is normal, so there's
// no REASSIGNABLE_STATUSES-style filtered subset the way LeadDetailPage needs for CONVERTED.

export type TicketStatus = "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CLOSED";

export const TICKET_STATUSES: TicketStatus[] = ["OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"];

export type TicketPriority = "LOW" | "MEDIUM" | "HIGH" | "URGENT";

export const TICKET_PRIORITIES: TicketPriority[] = ["LOW", "MEDIUM", "HIGH", "URGENT"];

export interface TicketDto {
  id: string;
  accountId: string | null;
  contactId: string | null;
  subject: string;
  description: string | null;
  status: TicketStatus;
  priority: TicketPriority;
  ownerId: string;
  resolvedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTicketRequest {
  subject: string;
  description?: string | null;
  priority: TicketPriority;
  accountId?: string | null;
  contactId?: string | null;
  ownerId?: string | null;
}

export interface UpdateTicketRequest {
  subject: string;
  description?: string | null;
  priority: TicketPriority;
  accountId?: string | null;
  contactId?: string | null;
}

export interface UpdateTicketStatusRequest {
  status: TicketStatus;
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

// ---- CRM: Email & Calendar ----
//
// EMAIL_MESSAGE/CALENDAR_EVENT are a genuinely new permission-catalog
// resource pair (seeded in V15, not a pre-existing gap like Ticket) that
// coexist with Activity's own EMAIL/MEETING types rather than replace them -
// see backend/crm-platform/README.md's module layout for `email`/`calendar`.
// CrmRecordType is the same idea as RelatedToType above but with TICKET
// added, since both new modules can be logged against a Ticket and
// Activity's RelatedToType predates the Ticket module entirely.

export type CrmRecordType = "ACCOUNT" | "CONTACT" | "OPPORTUNITY" | "LEAD" | "TICKET";

export const CRM_RECORD_TYPES: CrmRecordType[] = ["ACCOUNT", "CONTACT", "OPPORTUNITY", "LEAD", "TICKET"];

export type EmailDirection = "INBOUND" | "OUTBOUND";

export const EMAIL_DIRECTIONS: EmailDirection[] = ["INBOUND", "OUTBOUND"];

export interface EmailMessageDto {
  id: string;
  direction: EmailDirection;
  subject: string;
  body: string | null;
  fromAddress: string;
  toAddresses: string;
  ccAddresses: string | null;
  relatedToType: CrmRecordType;
  relatedToId: string;
  sentAt: string;
  ownerId: string;
  createdAt: string;
  updatedAt: string;
}

/** Backs both create (POST) and update (PUT) on the backend - see LogEmailRequest's javadoc. */
export interface LogEmailRequest {
  direction: EmailDirection;
  subject: string;
  body?: string | null;
  fromAddress: string;
  toAddresses: string;
  ccAddresses?: string | null;
  relatedToType: CrmRecordType;
  relatedToId: string;
  sentAt?: string | null;
  ownerId?: string | null;
}

export type CalendarAttendeeResponseStatus = "NEEDS_ACTION" | "ACCEPTED" | "DECLINED" | "TENTATIVE";

export const CALENDAR_ATTENDEE_RESPONSE_STATUSES: CalendarAttendeeResponseStatus[] = [
  "NEEDS_ACTION",
  "ACCEPTED",
  "DECLINED",
  "TENTATIVE",
];

export interface CalendarEventAttendeeDto {
  id: string;
  userId: string | null;
  externalEmail: string | null;
  responseStatus: CalendarAttendeeResponseStatus;
}

export interface CalendarEventDto {
  id: string;
  title: string;
  description: string | null;
  location: string | null;
  startAt: string;
  endAt: string;
  allDay: boolean;
  relatedToType: CrmRecordType | null;
  relatedToId: string | null;
  ownerId: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCalendarEventRequest {
  title: string;
  description?: string | null;
  location?: string | null;
  startAt: string;
  endAt: string;
  allDay: boolean;
  relatedToType?: CrmRecordType | null;
  relatedToId?: string | null;
  ownerId?: string | null;
}

export type UpdateCalendarEventRequest = Omit<CreateCalendarEventRequest, "ownerId">;

/** Exactly one of userId/externalEmail - see AddAttendeeRequest's javadoc. */
export interface AddAttendeeRequest {
  userId?: string | null;
  externalEmail?: string | null;
}

export interface UpdateAttendeeResponseRequest {
  responseStatus: CalendarAttendeeResponseStatus;
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

// ---- Automation: workflows ----
// WORKFLOW is owner-scoped (OWN/TEAM/ORGANIZATION, no DEPARTMENT) - unlike
// Campaign/KnowledgeArticle/CustomField/CustomObject above, a workflow
// belongs to whoever created it, same shape as Account/Contact/Lead/
// Opportunity (see the backend's Workflow entity javadoc).

export type WorkflowTriggerResource = "LEAD" | "CONTACT" | "ACCOUNT" | "OPPORTUNITY";

export const WORKFLOW_TRIGGER_RESOURCES: WorkflowTriggerResource[] = ["LEAD", "CONTACT", "ACCOUNT", "OPPORTUNITY"];

export type WorkflowTriggerEvent = "CREATED" | "UPDATED" | "DELETED";

export const WORKFLOW_TRIGGER_EVENTS: WorkflowTriggerEvent[] = ["CREATED", "UPDATED", "DELETED"];

export type WorkflowActionType = "CREATE_TASK";

export interface WorkflowDto {
  id: string;
  ownerId: string;
  name: string;
  description: string | null;
  triggerResource: WorkflowTriggerResource;
  triggerEvent: WorkflowTriggerEvent;
  actionType: WorkflowActionType;
  taskSubject: string;
  taskAssigneeUserId: string | null;
  active: boolean;
  runCount: number;
  lastRunAt: string | null;
  createdAt: string;
}

export interface CreateWorkflowRequest {
  name: string;
  description?: string | null;
  triggerResource: WorkflowTriggerResource;
  triggerEvent: WorkflowTriggerEvent;
  taskSubject: string;
  taskAssigneeUserId?: string | null;
  ownerId?: string | null;
}

export interface UpdateWorkflowRequest {
  name: string;
  description?: string | null;
  taskSubject: string;
  taskAssigneeUserId?: string | null;
}

export interface SetWorkflowActiveRequest {
  active: boolean;
}

export interface RunWorkflowRequest {
  resourceId: string;
}

export type WorkflowRunStatus = "SUCCEEDED" | "FAILED";

export interface WorkflowRunDto {
  id: string;
  resourceId: string;
  createdActivityId: string | null;
  status: WorkflowRunStatus;
  errorMessage: string | null;
  ranAt: string;
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

// ---- Dashboards ----
// DASHBOARD is owner-scoped (OWN/TEAM/ORGANIZATION, no DEPARTMENT) like
// Workflow above. A widget's `data` shape depends on its reportType - see
// DashboardWidgetType below - the same discriminated-union-by-tag pattern
// the backend's DashboardWidgetDataDto javadoc describes.

export type DashboardWidgetReportType = "PIPELINE_BY_STAGE" | "LEAD_FUNNEL" | "LEADERBOARD";

export const DASHBOARD_WIDGET_REPORT_TYPES: DashboardWidgetReportType[] = ["PIPELINE_BY_STAGE", "LEAD_FUNNEL", "LEADERBOARD"];

export interface DashboardWidgetDto {
  id: string;
  reportType: DashboardWidgetReportType;
  title: string;
  displayOrder: number;
  width: number;
  height: number;
}

export interface DashboardDto {
  id: string;
  ownerId: string;
  name: string;
  description: string | null;
  isDefault: boolean;
  createdAt: string;
  widgets: DashboardWidgetDto[];
}

export interface CreateDashboardRequest {
  name: string;
  description?: string | null;
  ownerId?: string | null;
}

export type UpdateDashboardRequest = Omit<CreateDashboardRequest, "ownerId">;

export interface CreateDashboardWidgetRequest {
  reportType: DashboardWidgetReportType;
  title?: string;
  displayOrder?: number;
  width?: number;
  height?: number;
}

export interface UpdateDashboardWidgetRequest {
  title?: string;
  displayOrder: number;
  width: number;
  height: number;
}

/** `data`'s shape depends on `reportType` - PipelineStageSummaryDto[] for PIPELINE_BY_STAGE, LeadFunnelStageDto[] for LEAD_FUNNEL, RepLeaderboardEntryDto[] for LEADERBOARD. Narrow with a switch on reportType before reading it, same as the pages do. */
export interface DashboardWidgetDataDto {
  id: string;
  reportType: DashboardWidgetReportType;
  title: string;
  displayOrder: number;
  width: number;
  height: number;
  data: PipelineStageSummaryDto[] | LeadFunnelStageDto[] | RepLeaderboardEntryDto[];
}

export interface DashboardDataDto {
  dashboardId: string;
  name: string;
  widgets: DashboardWidgetDataDto[];
}

// ---- Bulk CSV import/export (Account/Contact/Lead) ----
// LEAD/CONTACT/ACCOUNT/OPPORTUNITY/ACTIVITY/QUOTE/TICKET all got IMPORT and
// EXPORT permissions seeded alongside their other CRUD actions, but only
// Account/Contact/Lead have a real implementation so far - see
// ImportExportService's javadoc on the backend. IMPORT/EXPORT aren't in
// MEMBER's default permission set (RoleService#isCoreCrmResource only
// grants CREATE/READ/UPDATE), so this lives in AppLayout's admin-only nav
// group, same reasoning as Reports/Workflows/Dashboards.

export type ImportEntityType = "ACCOUNT" | "CONTACT" | "LEAD" | "TICKET";

export const IMPORT_ENTITY_TYPES: ImportEntityType[] = ["ACCOUNT", "CONTACT", "LEAD", "TICKET"];

export type ImportJobStatus = "COMPLETED" | "FAILED";

export interface ImportRowErrorDto {
  rowNumber: number;
  message: string;
}

export interface ImportJobDto {
  id: string;
  entityType: ImportEntityType;
  status: ImportJobStatus;
  totalRows: number;
  successCount: number;
  errorCount: number;
  createdAt: string;
  errors: ImportRowErrorDto[];
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

// ---- Notification ----
//
// A fourth, simpler access pattern than everything above - see
// backend/crm-platform/README.md's module layout for `notification` and
// Notification's javadoc. There is no owner/scope concept here at all: the
// backend hard-scopes every read/write to "recipientUserId == the caller,"
// so NotificationDto never even includes a recipientUserId field - it's
// always implicitly "mine," the same way a fetched UserDto from /users/me
// doesn't need to say whose profile it is.

export type NotificationType = "ASSIGNMENT" | "MENTION" | "REMINDER" | "GENERAL";

export const NOTIFICATION_TYPES: NotificationType[] = ["ASSIGNMENT", "MENTION", "REMINDER", "GENERAL"];

export interface NotificationDto {
  id: string;
  senderUserId: string | null;
  type: NotificationType;
  title: string;
  body: string | null;
  relatedToType: CrmRecordType | null;
  relatedToId: string | null;
  readAt: string | null;
  createdAt: string;
}

/** Sends a notification to a teammate - relatedToType/relatedToId are optional, both-null-or-both-set, same shape as CreateCalendarEventRequest's. */
export interface CreateNotificationRequest {
  recipientUserId: string;
  type: NotificationType;
  title: string;
  body?: string | null;
  relatedToType?: CrmRecordType | null;
  relatedToId?: string | null;
}

export interface UnreadCountResponse {
  unreadCount: number;
}

// ---- Attachment ----
//
// Back to owner-scoped (see backend/crm-platform/README.md's module layout for `attachment`) -
// unlike Notification, an uploaded file is a normal team-visible CRM record. storageKey is
// deliberately never part of this type - the backend's AttachmentDto never serializes it either;
// the only way back to the bytes is GET /attachments/{id}/download.

export interface AttachmentDto {
  id: string;
  fileName: string;
  contentType: string | null;
  fileSizeBytes: number;
  description: string | null;
  relatedToType: CrmRecordType;
  relatedToId: string;
  ownerId: string;
  createdAt: string;
  updatedAt: string;
}

/** Metadata-only - matches UpdateAttachmentRequest on the backend. There's no CreateAttachmentRequest type; upload goes through FormData directly (see api/attachments.ts's uploadAttachment), not a JSON body. */
export interface UpdateAttachmentRequest {
  fileName: string;
  description?: string | null;
  relatedToType: CrmRecordType;
  relatedToId: string;
}
