import assert from "node:assert/strict";
import { test } from "node:test";
import { canonicalMoney, formatMoney, maskFaceValue } from "./money.ts";

test("masks face value with thousand dots and a decimal comma", () => {
  assert.equal(maskFaceValue("100000.00"), "100.000,00");
  assert.equal(maskFaceValue("100000,5"), "100.000,5");
  assert.equal(maskFaceValue("100.000,50"), "100.000,50");
  assert.equal(maskFaceValue("R$ 1.234,567"), "1.234,56");
  assert.equal(maskFaceValue("100.000"), "100.000");
});

test("sends the masked face value as a canonical decimal string", () => {
  assert.equal(canonicalMoney("100.000,00"), "100000.00");
  assert.equal(canonicalMoney("1.234,5"), "1234.50");
  assert.equal(canonicalMoney("100000.00"), "100000.00");
  assert.equal(canonicalMoney("0,00"), null);
  assert.equal(canonicalMoney(""), null);
});

test("formats the API decimal string without binary float", () => {
  assert.equal(formatMoney("92859.94", "BRL"), "R$ 92.859,94");
  assert.equal(formatMoney("17094.67", "USD"), "US$ 17.094,67");
  assert.equal(formatMoney("7140.06", "BRL"), "R$ 7.140,06");
});
