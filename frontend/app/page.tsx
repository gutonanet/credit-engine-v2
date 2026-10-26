"use client";

import { ReceivableForm } from "@/components/desk/ReceivableForm";
import { SimulationCard } from "@/components/desk/SimulationCard";
import { SettlementGrid } from "@/components/report/SettlementGrid";
import { Alert } from "@/components/ui/Alert";
import { Tabs } from "@/components/ui/Tabs";
import { useSettlementForm } from "@/hooks/useSettlementForm";
import { useSettlementsList } from "@/hooks/useSettlementsList";
import { useState } from "react";

type PanelTab = "desk" | "report";

const TABS = [
  { id: "desk" as const, label: "Mesa (Operação)" },
  { id: "report" as const, label: "Extrato de Liquidações" },
];

export default function OperatorPanelPage() {
  const [tab, setTab] = useState<PanelTab>("desk");
  const report = useSettlementsList(20);
  const form = useSettlementForm(() => {
    report.showLatest();
  });

  return (
    <main className="min-h-screen">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-5">
          <div>
            <h1 className="text-2xl font-semibold text-brand-900">Credit Engine — Mesa</h1>
          </div>
          <p className="text-sm text-slate-500">Precificação e liquidação multimoedas</p>
        </div>
      </header>

      <div className="mx-auto max-w-6xl space-y-6 px-6 py-8">
        <Tabs tabs={TABS} active={tab} onChange={setTab} />

        {form.notice && <Alert notice={form.notice} onClose={form.dismissNotice} />}

        <div role="tabpanel" id="panel-desk" aria-labelledby="tab-desk" hidden={tab !== "desk"}>
          <div className="grid items-start gap-6 lg:grid-cols-2">
            <ReceivableForm
              assignorId={form.assignorId}
              faceValue={form.faceValue}
              dueDate={form.dueDate}
              receivableType={form.receivableType}
              paymentCurrency={form.paymentCurrency}
              settling={form.settling}
              canSubmit={Boolean(form.quote) && !form.quoteLoading}
              onAssignorId={form.setAssignorId}
              onFaceValue={form.setFaceValue}
              onDueDate={form.setDueDate}
              onReceivableType={form.setReceivableType}
              onPaymentCurrency={form.setPaymentCurrency}
              onSubmit={() => {
                void form.submit();
              }}
            />
            <SimulationCard quote={form.quote} loading={form.quoteLoading} message={form.quoteMessage} />
          </div>
        </div>

        <div role="tabpanel" id="panel-report" aria-labelledby="tab-report" hidden={tab !== "report"}>
          <SettlementGrid
            items={report.items}
            page={report.page}
            size={report.size}
            totalPages={report.totalPages}
            totalElements={report.totalElements}
            loading={report.loading}
            error={report.error}
            filters={report.filters}
            onApplyFilters={report.applyFilters}
            onRefresh={report.refresh}
            onPrevious={report.previousPage}
            onNext={report.nextPage}
          />
        </div>
      </div>
    </main>
  );
}
