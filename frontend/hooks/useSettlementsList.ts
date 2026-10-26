"use client";

import { listSettlements, toUserMessage } from "@/api/settlements";
import { civilDayInstant } from "@/lib/money";
import type { ReportFilters, SettlementReportItem } from "@/types/settlement";
import { useCallback, useEffect, useRef, useState } from "react";

const EMPTY_FILTERS: ReportFilters = {
  assignorId: "",
  paymentCurrency: "",
  from: "",
  to: "",
};

export function useSettlementsList(size = 20) {
  const [page, setPage] = useState(0);
  const [filters, setFilters] = useState<ReportFilters>(EMPTY_FILTERS);
  const [items, setItems] = useState<SettlementReportItem[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const requestRef = useRef(0);

  const load = useCallback(
    async (nextPage: number, nextFilters: ReportFilters) => {
      const requestId = ++requestRef.current;
      setLoading(true);
      try {
        // SPEC: extrato pagina no servidor. page e size seguem na query, default 20.
        const data = await listSettlements({
          page: nextPage,
          size,
          assignorId: nextFilters.assignorId.trim() || undefined,
          paymentCurrency: nextFilters.paymentCurrency || undefined,
          from: nextFilters.from ? civilDayInstant(nextFilters.from, "start") : undefined,
          to: nextFilters.to ? civilDayInstant(nextFilters.to, "end") : undefined,
        });
        if (requestId !== requestRef.current) return;
        setItems(data.items);
        setPage(data.page);
        setTotalPages(data.totalPages);
        setTotalElements(data.totalElements);
        setError(null);
      } catch (caught) {
        if (requestId !== requestRef.current) return;
        setError(toUserMessage(caught));
      } finally {
        if (requestId === requestRef.current) setLoading(false);
      }
    },
    [size],
  );

  useEffect(() => {
    void load(page, filters);
  }, [page, filters, load]);

  function applyFilters(next: ReportFilters) {
    setPage(0);
    setFilters(next);
  }

  function refresh() {
    void load(page, filters);
  }

  function showLatest() {
    if (page === 0) {
      void load(0, filters);
      return;
    }
    setPage(0);
  }

  function previousPage() {
    setPage((current) => Math.max(0, current - 1));
  }

  function nextPage() {
    setPage((current) => (current + 1 < totalPages ? current + 1 : current));
  }

  return {
    page,
    size,
    filters,
    items,
    totalPages,
    totalElements,
    loading,
    error,
    applyFilters,
    refresh,
    showLatest,
    previousPage,
    nextPage,
  };
}
