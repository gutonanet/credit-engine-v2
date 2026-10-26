import { http, HttpResponse } from "msw";
import type { QuoteRequest, QuoteResponse, SettlementReportItem } from "@/types/settlement";

export const API = "http://localhost:8080";

export const apiCalls = {
  quotes: [] as QuoteRequest[],
  settlements: [] as { idempotencyKey: string | null; body: string }[],
  reports: [] as URL[],
};

export function resetApiCalls(): void {
  apiCalls.quotes.length = 0;
  apiCalls.settlements.length = 0;
  apiCalls.reports.length = 0;
}

export function quotePayload(presentValueBrl: string, discountBrl = "10.00"): QuoteResponse {
  return {
    termMonths: 3,
    baseRate: "0.01000000",
    fxRateUsdBrl: null,
    presentValueBrl,
    discountBrl,
    paymentAmount: presentValueBrl,
    paymentCurrency: "BRL",
  };
}

export const reportRow: SettlementReportItem = {
  id: "settlement-1",
  receivableId: "receivable-1",
  assignorId: "cedente-alfa",
  faceValue: "100000.00",
  presentValueBrl: "92859.94",
  discountBrl: "7140.06",
  paymentAmount: "17094.67",
  paymentCurrency: "USD",
  fxRateUsdBrl: "5.43210000",
  settledAt: "2026-03-10T15:30:00.000Z",
};

export function reportPage(page: number, items: SettlementReportItem[] = [reportRow]) {
  return {
    items,
    page,
    size: 20,
    totalElements: 41,
    totalPages: 3,
  };
}

function problem(status: number, message: string) {
  return HttpResponse.json({ code: "ERROR", message }, { status });
}

export const handlers = [
  http.post(`${API}/api/quotes`, async ({ request }) => {
    apiCalls.quotes.push((await request.json()) as QuoteRequest);
    return HttpResponse.json(quotePayload("11111.10"));
  }),
  http.post(`${API}/api/receivables`, () =>
    HttpResponse.json(
      {
        id: "11111111-1111-4111-8111-111111111111",
        assignorId: "cedente-demo",
        faceValue: "100000.00",
        dueDate: "2027-01-25",
        receivableType: "DUPLICATA_MERCANTIL",
        paymentCurrency: "BRL",
        status: "OPEN",
      },
      { status: 201 },
    ),
  ),
  http.post(`${API}/settlements`, async ({ request }) => {
    apiCalls.settlements.push({
      idempotencyKey: request.headers.get("Idempotency-Key"),
      body: await request.text(),
    });
    return HttpResponse.json(
      {
        id: "22222222-2222-4222-8222-222222222222",
        receivableId: "11111111-1111-4111-8111-111111111111",
        paymentAmount: "92859.94",
        paymentCurrency: "BRL",
        presentValueBrl: "92859.94",
        discountBrl: "7140.06",
      },
      { status: 201 },
    );
  }),
  http.get(`${API}/api/settlements/report`, ({ request }) => {
    const url = new URL(request.url);
    apiCalls.reports.push(url);
    return HttpResponse.json({
      items: [],
      page: Number(url.searchParams.get("page") ?? "0"),
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });
  }),
];

export function quoteError(status: number, message: string) {
  return http.post(`${API}/api/quotes`, async ({ request }) => {
    apiCalls.quotes.push((await request.json()) as QuoteRequest);
    return problem(status, message);
  });
}

export function settlementError(status: number, message: string) {
  return http.post(`${API}/settlements`, async ({ request }) => {
    apiCalls.settlements.push({
      idempotencyKey: request.headers.get("Idempotency-Key"),
      body: await request.text(),
    });
    return problem(status, message);
  });
}

export function reportByPage(secondPageRow: SettlementReportItem) {
  return http.get(`${API}/api/settlements/report`, ({ request }) => {
    const url = new URL(request.url);
    apiCalls.reports.push(url);
    const page = Number(url.searchParams.get("page") ?? "0");
    return HttpResponse.json(reportPage(page, page === 0 ? [reportRow] : [secondPageRow]));
  });
}
