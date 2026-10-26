export type ReceivableType = "DUPLICATA_MERCANTIL" | "CHEQUE_PREDATADO";
export type PaymentCurrency = "BRL" | "USD";

export interface QuoteRequest {
  faceValue: string;
  dueDate: string;
  receivableType: ReceivableType;
  paymentCurrency: PaymentCurrency;
}

export interface QuoteResponse {
  termMonths: number;
  baseRate: string;
  fxRateUsdBrl: string | null;
  presentValueBrl: string;
  discountBrl: string;
  paymentAmount: string;
  paymentCurrency: PaymentCurrency;
}

export interface CreateReceivableRequest {
  assignorId: string;
  faceValue: string;
  dueDate: string;
  receivableType: ReceivableType;
  paymentCurrency: PaymentCurrency;
}

export interface ReceivableResponse {
  id: string;
  assignorId: string;
  faceValue: string;
  dueDate: string;
  receivableType: ReceivableType;
  paymentCurrency: PaymentCurrency;
  status: string;
}

export interface SettleRequest {
  receivableId: string;
}

export interface SettlementResponse {
  id: string;
  receivableId: string;
  paymentAmount: string;
  paymentCurrency: PaymentCurrency;
  presentValueBrl: string;
  discountBrl: string;
}

export interface SettlementReportItem {
  id: string;
  receivableId: string;
  assignorId: string;
  faceValue: string;
  presentValueBrl: string;
  discountBrl: string;
  paymentAmount: string;
  paymentCurrency: PaymentCurrency;
  fxRateUsdBrl: string | null;
  settledAt: string;
}

export interface SettlementReportQuery {
  page: number;
  size: number;
  from?: string;
  to?: string;
  assignorId?: string;
  paymentCurrency?: PaymentCurrency;
}

export interface SettlementReportResponse {
  items: SettlementReportItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ReportFilters {
  assignorId: string;
  paymentCurrency: "" | PaymentCurrency;
  from: string;
  to: string;
}

export type NoticeTone = "error" | "success";

export interface Notice {
  tone: NoticeTone;
  message: string;
}

export interface ApiErrorBody {
  code?: string;
  message?: string;
  field?: string;
}
