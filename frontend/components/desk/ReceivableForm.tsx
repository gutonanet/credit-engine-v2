import type { PaymentCurrency, ReceivableType } from "@/types/settlement";

const fieldClass =
  "mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm outline-none ring-brand-600/20 focus:border-brand-600 focus:ring-2";

export function ReceivableForm({
  assignorId,
  faceValue,
  dueDate,
  receivableType,
  paymentCurrency,
  settling,
  canSubmit,
  onAssignorId,
  onFaceValue,
  onDueDate,
  onReceivableType,
  onPaymentCurrency,
  onSubmit,
}: {
  assignorId: string;
  faceValue: string;
  dueDate: string;
  receivableType: ReceivableType;
  paymentCurrency: PaymentCurrency;
  settling: boolean;
  canSubmit: boolean;
  onAssignorId: (value: string) => void;
  onFaceValue: (value: string) => void;
  onDueDate: (value: string) => void;
  onReceivableType: (value: ReceivableType) => void;
  onPaymentCurrency: (value: PaymentCurrency) => void;
  onSubmit: () => void;
}) {
  return (
    <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
      <h2 className="text-lg font-medium text-slate-900">Novo recebível</h2>
      <p className="mb-4 mt-1 text-sm text-slate-500">A simulação ao lado usa a cotação do motor. A liquidação confirma esse recebível.</p>
      <form
        className="space-y-4"
        onSubmit={(event) => {
          event.preventDefault();
          onSubmit();
        }}
      >
        <label className="block text-sm text-slate-600">
          Cedente
          <input className={fieldClass} value={assignorId} onChange={(event) => onAssignorId(event.target.value)} />
        </label>
        <label className="block text-sm text-slate-600">
          Valor de face (BRL)
          <input
            className={fieldClass}
            inputMode="decimal"
            autoComplete="off"
            placeholder="0,00"
            value={faceValue}
            onChange={(event) => onFaceValue(event.target.value)}
          />
        </label>
        <label className="block text-sm text-slate-600">
          Vencimento
          <input
            type="date"
            className={fieldClass}
            value={dueDate}
            onChange={(event) => onDueDate(event.target.value)}
          />
        </label>
        <label className="block text-sm text-slate-600">
          Tipo
          <select
            className={fieldClass}
            value={receivableType}
            onChange={(event) => onReceivableType(event.target.value as ReceivableType)}
          >
            <option value="DUPLICATA_MERCANTIL">Duplicata mercantil</option>
            <option value="CHEQUE_PREDATADO">Cheque pré-datado</option>
          </select>
        </label>
        <label className="block text-sm text-slate-600">
          Moeda de pagamento
          <select
            className={fieldClass}
            value={paymentCurrency}
            onChange={(event) => onPaymentCurrency(event.target.value as PaymentCurrency)}
          >
            <option value="BRL">BRL</option>
            <option value="USD">USD</option>
          </select>
        </label>
        <button
          type="submit"
          disabled={!canSubmit || settling}
          className="w-full rounded-lg bg-brand-600 px-4 py-2.5 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50"
        >
          {settling ? "Liquidando…" : "Liquidar"}
        </button>
      </form>
    </section>
  );
}
