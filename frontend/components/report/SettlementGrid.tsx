import { formatMoney, formatTimestamp } from "@/lib/money";
import type { PaymentCurrency, ReportFilters, SettlementReportItem } from "@/types/settlement";
import { useState } from "react";

const fieldClass =
  "mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm outline-none ring-brand-600/20 focus:border-brand-600 focus:ring-2";

export function SettlementGrid({
  items,
  page,
  size,
  totalPages,
  totalElements,
  loading,
  error,
  filters,
  onApplyFilters,
  onRefresh,
  onPrevious,
  onNext,
}: {
  items: SettlementReportItem[];
  page: number;
  size: number;
  totalPages: number;
  totalElements: number;
  loading: boolean;
  error: string | null;
  filters: ReportFilters;
  onApplyFilters: (filters: ReportFilters) => void;
  onRefresh: () => void;
  onPrevious: () => void;
  onNext: () => void;
}) {
  const [draft, setDraft] = useState<ReportFilters>(filters);

  return (
    <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-medium text-slate-900">Extrato de liquidações</h2>
          <p className="mt-1 text-sm text-slate-500">
            Página {totalPages === 0 ? 0 : page + 1} de {totalPages} · {totalElements} registro(s) · {size} por página
          </p>
        </div>
        <button
          type="button"
          onClick={onRefresh}
          className="rounded-lg border border-slate-300 px-3 py-2 text-sm font-medium text-brand-600 hover:bg-slate-50"
        >
          Atualizar
        </button>
      </div>

      <form
        className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-5"
        onSubmit={(event) => {
          event.preventDefault();
          onApplyFilters(draft);
        }}
      >
        <label className="text-sm text-slate-600">
          Cedente
          <input
            className={fieldClass}
            value={draft.assignorId}
            onChange={(event) => setDraft({ ...draft, assignorId: event.target.value })}
          />
        </label>
        <label className="text-sm text-slate-600">
          Moeda
          <select
            className={fieldClass}
            value={draft.paymentCurrency}
            onChange={(event) =>
              setDraft({ ...draft, paymentCurrency: event.target.value as ReportFilters["paymentCurrency"] })
            }
          >
            <option value="">Todas</option>
            <option value="BRL">BRL</option>
            <option value="USD">USD</option>
          </select>
        </label>
        <label className="text-sm text-slate-600">
          De
          <input
            type="date"
            className={fieldClass}
            value={draft.from}
            onChange={(event) => setDraft({ ...draft, from: event.target.value })}
          />
        </label>
        <label className="text-sm text-slate-600">
          Até
          <input
            type="date"
            className={fieldClass}
            value={draft.to}
            onChange={(event) => setDraft({ ...draft, to: event.target.value })}
          />
        </label>
        <div className="flex items-end">
          <button type="submit" className="w-full rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white">
            Filtrar
          </button>
        </div>
      </form>

      {error && <p className="mb-3 text-sm text-red-700">{error}</p>}

      <div className="overflow-x-auto">
        <table className="min-w-full text-left text-sm">
          <thead className="border-b border-slate-200 text-slate-600">
            <tr>
              <th className="px-2 py-2 font-medium">Data</th>
              <th className="px-2 py-2 font-medium">Cedente</th>
              <th className="px-2 py-2 font-medium">Face</th>
              <th className="px-2 py-2 font-medium">VP (BRL)</th>
              <th className="px-2 py-2 font-medium">Deságio</th>
              <th className="px-2 py-2 font-medium">Pagamento</th>
            </tr>
          </thead>
          <tbody>
            {items.map((row) => (
              <tr key={row.id} className="border-b border-slate-100">
                <td className="whitespace-nowrap px-2 py-2">{formatTimestamp(row.settledAt)}</td>
                <td className="px-2 py-2">{row.assignorId}</td>
                <td className="px-2 py-2">{formatMoney(row.faceValue, "BRL")}</td>
                <td className="px-2 py-2">{formatMoney(row.presentValueBrl, "BRL")}</td>
                <td className="px-2 py-2">{formatMoney(row.discountBrl, "BRL")}</td>
                <td className="px-2 py-2">
                  {formatMoney(row.paymentAmount, row.paymentCurrency as PaymentCurrency)}
                </td>
              </tr>
            ))}
            {!loading && items.length === 0 && (
              <tr>
                <td colSpan={6} className="px-2 py-8 text-center text-slate-500">
                  Nenhuma liquidação registrada.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {loading && <p className="mt-3 text-sm text-slate-500">Carregando extrato…</p>}

      <div className="mt-4 flex items-center justify-end gap-2">
        <button
          type="button"
          disabled={page <= 0 || loading}
          onClick={onPrevious}
          className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm disabled:opacity-40"
        >
          Anterior
        </button>
        <button
          type="button"
          disabled={loading || page + 1 >= totalPages}
          onClick={onNext}
          className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm disabled:opacity-40"
        >
          Próxima
        </button>
      </div>
    </section>
  );
}
