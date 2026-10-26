"use client";

import { createReceivable, quoteReceivable, settleReceivable, toUserMessage } from "@/api/settlements";
import { newIdempotencyKey } from "@/lib/idempotency";
import { canonicalMoney, formatMoney, maskFaceValue } from "@/lib/money";
import type { Notice, PaymentCurrency, QuoteResponse, ReceivableType } from "@/types/settlement";
import { useEffect, useRef, useState } from "react";

const INITIAL_ASSIGNOR = "cedente-demo";
const INITIAL_FACE = maskFaceValue("100000.00");

function defaultDueDate(): string {
  const date = new Date();
  date.setMonth(date.getMonth() + 3);
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}

export function useSettlementForm(onSettled?: () => void) {
  const [assignorId, setAssignorIdState] = useState(INITIAL_ASSIGNOR);
  const [faceValue, setFaceValueState] = useState(INITIAL_FACE);
  const [dueDate, setDueDateState] = useState(defaultDueDate);
  const initialDueDateRef = useRef(dueDate);
  const [receivableType, setReceivableTypeState] = useState<ReceivableType>("DUPLICATA_MERCANTIL");
  const [paymentCurrency, setPaymentCurrencyState] = useState<PaymentCurrency>("BRL");

  const [quote, setQuote] = useState<QuoteResponse | null>(null);
  const [quoteLoading, setQuoteLoading] = useState(false);
  const [quoteMessage, setQuoteMessage] = useState<string | null>(null);
  const [settling, setSettling] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);

  const receivableIdRef = useRef<string | null>(null);
  const idempotencyKeyRef = useRef<string | null>(null);
  const quoteRequestRef = useRef(0);
  const pauseQuoteRef = useRef(false);

  function resetIntention() {
    receivableIdRef.current = null;
    idempotencyKeyRef.current = null;
  }

  function resumeQuoting() {
    pauseQuoteRef.current = false;
  }

  function setAssignorId(value: string) {
    resumeQuoting();
    resetIntention();
    setAssignorIdState(value);
  }

  function setFaceValue(value: string) {
    resumeQuoting();
    resetIntention();
    setFaceValueState(maskFaceValue(value));
  }

  function setDueDate(value: string) {
    resumeQuoting();
    resetIntention();
    setDueDateState(value);
  }

  function setReceivableType(value: ReceivableType) {
    resumeQuoting();
    resetIntention();
    setReceivableTypeState(value);
  }

  function setPaymentCurrency(value: PaymentCurrency) {
    resumeQuoting();
    resetIntention();
    setPaymentCurrencyState(value);
  }

  useEffect(() => {
    if (pauseQuoteRef.current) {
      quoteRequestRef.current += 1;
      setQuote(null);
      setQuoteLoading(false);
      setQuoteMessage(null);
      return;
    }

    const money = canonicalMoney(faceValue);
    if (!money || !dueDate) {
      quoteRequestRef.current += 1;
      setQuote(null);
      setQuoteLoading(false);
      setQuoteMessage(money ? null : "Informe um valor de face maior que zero, com até 2 casas.");
      return;
    }

    const requestId = ++quoteRequestRef.current;
    const timer = setTimeout(() => {
      setQuoteLoading(true);
      quoteReceivable({
        faceValue: money,
        dueDate,
        receivableType,
        paymentCurrency,
      })
        .then((data) => {
          if (requestId !== quoteRequestRef.current) return;
          setQuote(data);
          setQuoteMessage(null);
        })
        .catch((error: unknown) => {
          if (requestId !== quoteRequestRef.current) return;
          setQuote(null);
          setQuoteMessage(toUserMessage(error));
        })
        .finally(() => {
          if (requestId === quoteRequestRef.current) setQuoteLoading(false);
        });
    }, 400);

    return () => clearTimeout(timer);
  }, [faceValue, dueDate, receivableType, paymentCurrency]);

  async function submit() {
    const money = canonicalMoney(faceValue);
    const assignor = assignorId.trim();
    if (!assignor) {
      setNotice({ tone: "error", message: "Validação: informe o cedente." });
      return;
    }
    if (!money || !quote) {
      setNotice({ tone: "error", message: "Aguarde uma simulação válida antes de liquidar." });
      return;
    }

    if (!idempotencyKeyRef.current) {
      idempotencyKeyRef.current = newIdempotencyKey();
    }
    const idempotencyKey = idempotencyKeyRef.current;

    setSettling(true);
    try {
      let receivableId = receivableIdRef.current;
      if (!receivableId) {
        const created = await createReceivable({
          assignorId: assignor,
          faceValue: money,
          dueDate,
          receivableType,
          paymentCurrency,
        });
        receivableId = created.id;
        receivableIdRef.current = created.id;
      }
      const result = await settleReceivable(receivableId, idempotencyKey);
      setNotice({
        tone: "success",
        message: `Liquidação confirmada: ${formatMoney(result.paymentAmount, result.paymentCurrency)}`,
      });
      pauseQuoteRef.current = true;
      quoteRequestRef.current += 1;
      setQuote(null);
      setQuoteMessage(null);
      setQuoteLoading(false);
      setAssignorIdState(INITIAL_ASSIGNOR);
      setFaceValueState(INITIAL_FACE);
      setDueDateState(initialDueDateRef.current);
      setReceivableTypeState("DUPLICATA_MERCANTIL");
      setPaymentCurrencyState("BRL");
      resetIntention();
      onSettled?.();
    } catch (error) {
      setNotice({ tone: "error", message: toUserMessage(error) });
    } finally {
      setSettling(false);
    }
  }

  return {
    assignorId,
    faceValue,
    dueDate,
    receivableType,
    paymentCurrency,
    setAssignorId,
    setFaceValue,
    setDueDate,
    setReceivableType,
    setPaymentCurrency,
    quote,
    quoteLoading,
    quoteMessage,
    settling,
    notice,
    dismissNotice: () => setNotice(null),
    submit,
  };
}
