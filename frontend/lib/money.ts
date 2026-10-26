import type { PaymentCurrency } from "@/types/settlement";

/**
 * Máscara pt-BR: ponto na casa de milhar e vírgula nos centavos, no máximo 2 casas.
 * Um ponto seguido de 1 ou 2 dígitos, sem vírgula, é decimal da API (`100000.00`).
 */
export function maskFaceValue(input: string): string {
  const trimmed = input.trim().replace(/\s/g, "").replace(/^R\$/, "");
  const source = /^\d+\.\d{1,2}$/.test(trimmed) ? trimmed.replace(".", ",") : trimmed;
  const cleaned = source.replace(/[^\d,]/g, "");
  const commaAt = cleaned.indexOf(",");
  const wholeRaw = commaAt === -1 ? cleaned : cleaned.slice(0, commaAt);
  const wholeDigits = wholeRaw.replace(/^0+/, "");
  const whole = wholeDigits === "" ? (wholeRaw.length > 0 || commaAt !== -1 ? "0" : "") : wholeDigits;
  const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  if (commaAt === -1) {
    return grouped;
  }
  const fraction = cleaned.slice(commaAt + 1).replace(/\D/g, "").slice(0, 2);
  return `${grouped},${fraction}`;
}

/** Canonical scale-2 decimal, or null when the text is not a positive money amount. */
export function canonicalMoney(input: string): string | null {
  const masked = maskFaceValue(input);
  if (!masked) {
    return null;
  }
  const [grouped, fraction = ""] = masked.split(",");
  const whole = grouped.replace(/\./g, "");
  const cents = (fraction + "00").slice(0, 2);
  if (!/^\d+$/.test(whole) || (/^0+$/.test(whole) && cents === "00")) {
    return null;
  }
  return `${whole}.${cents}`;
}

/** Exibe a string decimal da API. Os centavos não passam por Number/parseFloat (SPEC §2). */
export function formatMoney(value: string, currency: PaymentCurrency): string {
  const trimmed = value.trim();
  const negative = trimmed.startsWith("-");
  const unsigned = negative ? trimmed.slice(1) : trimmed;
  const [wholeRaw, fractionRaw = ""] = unsigned.split(".");
  const whole = wholeRaw.replace(/\D/g, "").replace(/^0+(?=\d)/, "") || "0";
  const cents = (fractionRaw.replace(/\D/g, "") + "00").slice(0, 2);
  const grouped = new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 0 }).format(BigInt(whole));
  const symbol = currency === "USD" ? "US$" : "R$";
  return `${negative ? "−" : ""}${symbol} ${grouped},${cents}`;
}

export function formatRate(value: string): string {
  const [whole, fraction = ""] = value.split(".");
  const trimmed = fraction.replace(/0+$/, "");
  return trimmed.length > 0 ? `${whole},${trimmed}` : whole;
}

export function formatTimestamp(iso: string): string {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short",
    timeZone: "America/Sao_Paulo",
  }).format(new Date(iso));
}

/** Civil day bounds in America/Sao_Paulo, serialized as ISO instants for the report query. */
export function civilDayInstant(date: string, bound: "start" | "end"): string {
  const time = bound === "start" ? "T00:00:00.000-03:00" : "T23:59:59.999-03:00";
  return new Date(`${date}${time}`).toISOString();
}
