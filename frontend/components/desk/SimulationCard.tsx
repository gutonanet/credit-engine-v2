import { formatMoney, formatRate } from "@/lib/money";
import type { QuoteResponse } from "@/types/settlement";

function Row({ label, value, emphasis = false }: { label: string; value: string; emphasis?: boolean }) {
  return (
    <div className={`flex items-baseline justify-between gap-4 ${emphasis ? "border-t border-slate-100 pt-3 font-medium" : ""}`}>
      <dt className="text-slate-600">{label}</dt>
      <dd className="text-right text-slate-900">{value}</dd>
    </div>
  );
}

export function SimulationCard({
  quote,
  loading,
  message,
}: {
  quote: QuoteResponse | null;
  loading: boolean;
  message: string | null;
}) {
  return (
    <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm" aria-live="polite">
      <h2 className="text-lg font-medium text-slate-900">Simulação</h2>
      <p className="mb-4 mt-1 text-sm text-slate-500">Valores devolvidos pela API, sem recálculo no navegador.</p>
      {loading && <p className="text-sm text-slate-500">Calculando…</p>}
      {!loading && quote && (
        <dl className="space-y-2 text-sm">
          <Row label="Prazo" value={`${quote.termMonths} meses`} />
          <Row label="Valor presente (BRL)" value={formatMoney(quote.presentValueBrl, "BRL")} />
          <Row label="Deságio (BRL)" value={formatMoney(quote.discountBrl, "BRL")} />
          <Row
            label={`Líquido (${quote.paymentCurrency})`}
            value={formatMoney(quote.paymentAmount, quote.paymentCurrency)}
            emphasis
          />
          {quote.fxRateUsdBrl && <Row label="Câmbio USD/BRL" value={formatRate(quote.fxRateUsdBrl)} />}
        </dl>
      )}
      {!loading && !quote && (
        <p className="text-sm text-slate-500">{message ?? "Informe os dados para simular."}</p>
      )}
    </section>
  );
}
