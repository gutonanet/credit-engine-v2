import http from "k6/http";
import { check, sleep } from "k6";
import { uuidv4 } from "https://jslib.k6.io/k6-utils/1.4.0/index.js";

const baseUrl = __ENV.BASE_URL || "http://localhost:8080";

export const options = {
  scenarios: {
    quote: {
      executor: "constant-vus",
      vus: 5,
      duration: "30s",
      exec: "quote",
    },
    settle: {
      executor: "constant-vus",
      vus: 5,
      duration: "30s",
      exec: "settle",
      startTime: "2s",
    },
  },
  thresholds: {
    "http_req_duration{op:quote}": ["p(95)<50"],
    "http_req_duration{op:settle}": ["p(95)<200"],
  },
};

function dueDate() {
  const date = new Date();
  date.setMonth(date.getMonth() + 3);
  return date.toISOString().slice(0, 10);
}

const jsonHeaders = { headers: { "Content-Type": "application/json" } };

export function quote() {
  const body = JSON.stringify({
    faceValue: "100000.00",
    dueDate: dueDate(),
    receivableType: "DUPLICATA_MERCANTIL",
    paymentCurrency: "BRL",
  });
  const response = http.post(`${baseUrl}/api/quotes`, body, { ...jsonHeaders, tags: { op: "quote" } });
  check(response, { "quote 200": (res) => res.status === 200 });
  sleep(0.05);
}

export function settle() {
  const created = http.post(
    `${baseUrl}/api/receivables`,
    JSON.stringify({
      assignorId: `k6-${uuidv4()}`,
      faceValue: "10000.00",
      dueDate: dueDate(),
      receivableType: "DUPLICATA_MERCANTIL",
      paymentCurrency: "BRL",
    }),
    jsonHeaders,
  );
  check(created, { "receivable 201": (res) => res.status === 201 });
  const receivableId = created.json("id");
  const response = http.post(
    `${baseUrl}/settlements`,
    JSON.stringify({ receivableId }),
    {
      headers: {
        "Content-Type": "application/json",
        "Idempotency-Key": uuidv4(),
      },
      tags: { op: "settle" },
    },
  );
  check(response, { "settlement 201": (res) => res.status === 201 });
  sleep(0.05);
}
