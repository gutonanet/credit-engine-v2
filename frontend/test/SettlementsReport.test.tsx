import OperatorPanelPage from "@/app/page";
import { civilDayInstant } from "@/lib/money";
import type { SettlementReportItem } from "@/types/settlement";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test } from "vitest";
import { apiCalls, reportByPage, reportRow } from "./msw/handlers";
import { server } from "./msw/server";

const secondPageRow: SettlementReportItem = {
  ...reportRow,
  id: "settlement-2",
  assignorId: "cedente-beta",
  faceValue: "25000.00",
  presentValueBrl: "23337.77",
  discountBrl: "1662.23",
  paymentAmount: "23337.77",
  paymentCurrency: "BRL",
};

async function openReport() {
  server.use(reportByPage(secondPageRow));
  const user = userEvent.setup();
  render(<OperatorPanelPage />);
  await user.click(screen.getByRole("tab", { name: "Extrato de Liquidações" }));
  await screen.findByRole("cell", { name: "cedente-alfa" });
  return user;
}

test("renders the report columns with formatted money and date", async () => {
  await openReport();

  for (const column of ["Data", "Cedente", "Face", "VP (BRL)", "Deságio", "Pagamento"]) {
    expect(screen.getByRole("columnheader", { name: column })).toBeInTheDocument();
  }

  expect(screen.getByRole("cell", { name: /10\/03\/2026/ })).toBeInTheDocument();
  expect(screen.getByRole("cell", { name: "cedente-alfa" })).toBeInTheDocument();
  expect(screen.getByRole("cell", { name: "R$ 100.000,00" })).toBeInTheDocument();
  expect(screen.getByRole("cell", { name: "R$ 92.859,94" })).toBeInTheDocument();
  expect(screen.getByRole("cell", { name: "R$ 7.140,06" })).toBeInTheDocument();
  expect(screen.getByRole("cell", { name: "US$ 17.094,67" })).toBeInTheDocument();
});

test("requests the next and previous server pages", async () => {
  const user = await openReport();

  await user.click(screen.getByRole("button", { name: "Próxima" }));
  expect(await screen.findByRole("cell", { name: "cedente-beta" })).toBeInTheDocument();
  await waitFor(() => expect(apiCalls.reports.some((url) => url.searchParams.get("page") === "1")).toBe(true));

  await user.click(screen.getByRole("button", { name: "Anterior" }));
  expect(await screen.findByRole("cell", { name: "cedente-alfa" })).toBeInTheDocument();
  await waitFor(() => {
    const pages = apiCalls.reports.map((url) => url.searchParams.get("page"));
    expect(pages.at(-1)).toBe("0");
    expect(pages).toContain("1");
  });
});

test("sends assignor, currency and civil-day bounds when filtering", async () => {
  const user = await openReport();

  await user.type(screen.getByRole("textbox", { name: "Cedente" }), "cedente-alfa");
  await user.selectOptions(screen.getByRole("combobox", { name: "Moeda" }), "USD");
  await user.type(screen.getByLabelText("De"), "2026-04-01");
  await user.type(screen.getByLabelText("Até"), "2026-04-30");
  await user.click(screen.getByRole("button", { name: "Filtrar" }));

  await waitFor(() => {
    const last = apiCalls.reports.at(-1);
    expect(last?.searchParams.get("assignorId")).toBe("cedente-alfa");
    expect(last?.searchParams.get("paymentCurrency")).toBe("USD");
    expect(last?.searchParams.get("page")).toBe("0");
    expect(last?.searchParams.get("size")).toBe("20");
    expect(last?.searchParams.get("from")).toBe(civilDayInstant("2026-04-01", "start"));
    expect(last?.searchParams.get("to")).toBe(civilDayInstant("2026-04-30", "end"));
  });
});
