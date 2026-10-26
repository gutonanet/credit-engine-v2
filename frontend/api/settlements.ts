import type {
  ApiErrorBody,
  CreateReceivableRequest,
  QuoteRequest,
  QuoteResponse,
  ReceivableResponse,
  SettlementReportQuery,
  SettlementReportResponse,
  SettlementResponse,
} from "@/types/settlement";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public field?: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export function toUserMessage(error: unknown): string {
  if (error instanceof ApiError) {
    const title =
      error.status === 400
        ? "Validação"
        : error.status === 409
          ? "Conflito"
          : error.status === 422
            ? "Regra de negócio"
            : "Erro";
    const field = error.field ? ` (${error.field})` : "";
    return `${title}: ${error.message}${field}`;
  }
  return "Falha inesperada ao comunicar com a API.";
}

async function readError(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as ApiErrorBody;
    return new ApiError(
      response.status,
      body.code ?? "ERROR",
      body.message ?? response.statusText,
      body.field,
    );
  } catch {
    return new ApiError(response.status, "ERROR", response.statusText);
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  try {
    const response = await fetch(`${API_URL}${path}`, {
      ...init,
      headers: {
        Accept: "application/json",
        ...(init?.body ? { "Content-Type": "application/json" } : {}),
        ...init?.headers,
      },
    });
    if (!response.ok) {
      throw await readError(response);
    }
    return (await response.json()) as T;
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    throw new ApiError(0, "NETWORK", "Não foi possível contatar o servidor.");
  }
}

export function quoteReceivable(body: QuoteRequest): Promise<QuoteResponse> {
  return request<QuoteResponse>("/api/quotes", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function createReceivable(body: CreateReceivableRequest): Promise<ReceivableResponse> {
  return request<ReceivableResponse>("/api/receivables", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function settleReceivable(
  receivableId: string,
  idempotencyKey: string,
): Promise<SettlementResponse> {
  return request<SettlementResponse>("/settlements", {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey },
    body: JSON.stringify({ receivableId }),
  });
}

export function listSettlements(query: SettlementReportQuery): Promise<SettlementReportResponse> {
  const params = new URLSearchParams({
    page: String(query.page),
    size: String(query.size),
  });
  if (query.from) params.set("from", query.from);
  if (query.to) params.set("to", query.to);
  if (query.assignorId) params.set("assignorId", query.assignorId);
  if (query.paymentCurrency) params.set("paymentCurrency", query.paymentCurrency);
  return request<SettlementReportResponse>(`/api/settlements/report?${params.toString()}`);
}
