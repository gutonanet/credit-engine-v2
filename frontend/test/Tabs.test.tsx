import OperatorPanelPage from "@/app/page";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test } from "vitest";

test("switches between the operation desk and the settlements report", async () => {
  const user = userEvent.setup();
  render(<OperatorPanelPage />);

  expect(screen.getByRole("tab", { name: "Mesa (Operação)", selected: true })).toBeInTheDocument();
  expect(screen.getByRole("textbox", { name: "Valor de face (BRL)" })).toBeInTheDocument();
  expect(screen.queryByRole("heading", { name: "Extrato de liquidações" })).not.toBeInTheDocument();

  await user.click(screen.getByRole("tab", { name: "Extrato de Liquidações" }));

  expect(screen.getByRole("tab", { name: "Extrato de Liquidações", selected: true })).toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "Extrato de liquidações" })).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Filtrar" })).toBeInTheDocument();
  expect(screen.queryByRole("textbox", { name: "Valor de face (BRL)" })).not.toBeInTheDocument();

  await user.click(screen.getByRole("tab", { name: "Mesa (Operação)" }));

  expect(screen.getByRole("tab", { name: "Mesa (Operação)", selected: true })).toBeInTheDocument();
  expect(screen.getByRole("textbox", { name: "Valor de face (BRL)" })).toBeInTheDocument();
  expect(screen.queryByRole("heading", { name: "Extrato de liquidações" })).not.toBeInTheDocument();
});

test("keeps the settlement alert visible after leaving the desk", async () => {
  const user = userEvent.setup();
  render(<OperatorPanelPage />);

  const settle = await screen.findByRole("button", { name: "Liquidar" });
  await waitFor(() => expect(settle).toBeEnabled());
  await user.click(settle);
  expect(await screen.findByRole("alert")).toHaveTextContent("Liquidação confirmada: R$ 92.859,94");

  await user.click(screen.getByRole("tab", { name: "Extrato de Liquidações" }));

  expect(screen.getByRole("heading", { name: "Extrato de liquidações" })).toBeInTheDocument();
  expect(screen.getByRole("alert")).toHaveTextContent("Liquidação confirmada: R$ 92.859,94");
  expect(screen.getByRole("button", { name: "Fechar" })).toBeInTheDocument();
});
