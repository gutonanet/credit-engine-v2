import OperatorPanelPage from "@/app/page";
import { formatMoney, maskFaceValue } from "@/lib/money";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test } from "vitest";
import { apiCalls, quoteError, settlementError } from "./msw/handlers";
import { server } from "./msw/server";

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

async function openDesk() {
  const user = userEvent.setup();
  render(<OperatorPanelPage />);
  await screen.findByRole("textbox", { name: "Valor de face (BRL)" });
  return user;
}

test("masks the face value as the operator types", async () => {
  const user = await openDesk();
  const face = screen.getByRole("textbox", { name: "Valor de face (BRL)" });

  await user.clear(face);
  await user.type(face, "100000,50");

  expect(face).toHaveValue(maskFaceValue("100000,50"));
  expect(face).toHaveValue("100.000,50");
});

test("shows the quote returned by the API without a local recalculation", async () => {
  await openDesk();

  expect(await screen.findByText("3 meses")).toBeInTheDocument();
  expect(screen.getAllByText(formatMoney("11111.10", "BRL"))).toHaveLength(2);
  expect(screen.getByText(formatMoney("10.00", "BRL"))).toBeInTheDocument();
  expect(screen.queryByText("R$ 92.859,94")).not.toBeInTheDocument();

  await waitFor(() => expect(apiCalls.quotes.length).toBeGreaterThan(0));
  expect(apiCalls.quotes.at(-1)?.faceValue).toBe("100000.00");
});

test("settles with an idempotency key and confirms the payment", async () => {
  const user = await openDesk();
  const assignor = screen.getByRole("textbox", { name: "Cedente" });
  const face = screen.getByRole("textbox", { name: "Valor de face (BRL)" });
  const dueDate = screen.getByLabelText("Vencimento");
  const initialDueDate = (dueDate as HTMLInputElement).value;
  const settle = await screen.findByRole("button", { name: "Liquidar" });
  await waitFor(() => expect(settle).toBeEnabled());

  await user.clear(assignor);
  await user.type(assignor, "cedente-outro");
  await user.clear(face);
  await user.type(face, "25000,00");
  await waitFor(() => expect(settle).toBeEnabled());
  await user.click(settle);

  const alert = await screen.findByRole("alert");
  expect(alert).toHaveTextContent("Liquidação confirmada: R$ 92.859,94");
  expect(alert).toHaveClass("bg-emerald-50");
  expect(apiCalls.settlements).toHaveLength(1);
  expect(apiCalls.settlements[0]?.idempotencyKey).toMatch(UUID_V4);

  expect(assignor).toHaveValue("cedente-demo");
  expect(face).toHaveValue("100.000,00");
  expect(dueDate).toHaveValue(initialDueDate);
  expect(screen.getByText("Informe os dados para simular.")).toBeInTheDocument();
  expect(screen.queryByText("3 meses")).not.toBeInTheDocument();
  expect(settle).toBeDisabled();

  const quotesAfterSettle = apiCalls.quotes.length;
  await new Promise((resolve) => setTimeout(resolve, 500));
  expect(apiCalls.quotes).toHaveLength(quotesAfterSettle);
  expect(screen.getByText("Informe os dados para simular.")).toBeInTheDocument();
});

test("dismisses the success alert", async () => {
  const user = await openDesk();
  const settle = await screen.findByRole("button", { name: "Liquidar" });
  await waitFor(() => expect(settle).toBeEnabled());
  await user.click(settle);

  expect(await screen.findByRole("alert")).toHaveTextContent("Liquidação confirmada: R$ 92.859,94");
  await user.click(screen.getByRole("button", { name: "Fechar" }));

  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  expect(screen.queryByText(/Liquidação confirmada/)).not.toBeInTheDocument();
});

test("prevents submission when mandatory fields are missing", async () => {
  const user = userEvent.setup({ delay: null });
  render(<OperatorPanelPage />);
  const face = screen.getByRole("textbox", { name: "Valor de face (BRL)" });
  const dueDate = screen.getByLabelText("Vencimento");

  await user.clear(face);
  await user.clear(dueDate);

  const settle = screen.getByRole("button", { name: "Liquidar" });
  expect(settle).toBeDisabled();
  await user.click(settle);
  expect(screen.getByText("Informe um valor de face maior que zero, com até 2 casas.")).toBeInTheDocument();

  await new Promise((resolve) => setTimeout(resolve, 500));
  expect(apiCalls.quotes).toHaveLength(0);
  expect(apiCalls.settlements).toHaveLength(0);
});

test("shows a business-rule message when the quote is rejected", async () => {
  server.use(quoteError(422, "dueDate must be after reference date"));
  await openDesk();

  expect(await screen.findByText("Regra de negócio: dueDate must be after reference date")).toBeInTheDocument();
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Liquidar" })).toBeDisabled();
});

test("shows a validation message when settlement is rejected", async () => {
  server.use(settlementError(400, "Idempotency-Key header is required"));
  const user = await openDesk();
  const settle = await screen.findByRole("button", { name: "Liquidar" });
  await waitFor(() => expect(settle).toBeEnabled());

  await user.click(settle);

  expect(await screen.findByRole("alert")).toHaveTextContent("Validação: Idempotency-Key header is required");
});

test("shows a conflict message when settlement is rejected", async () => {
  server.use(settlementError(409, "Receivable already settled"));
  const user = await openDesk();
  const settle = await screen.findByRole("button", { name: "Liquidar" });
  await waitFor(() => expect(settle).toBeEnabled());

  await user.click(settle);

  expect(await screen.findByRole("alert")).toHaveTextContent("Conflito: Receivable already settled");
});
