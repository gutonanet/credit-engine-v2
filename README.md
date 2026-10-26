# Credit Engine

Plataforma de cessão de crédito multimoedas da mesa de operações. Recebe um recebível em BRL, calcula o valor presente com deságio e registra a liquidação — em BRL ou USD — com trilha imutável.

O contrato de negócio está em [`SPEC.md`](SPEC.md). O motor de produção e a suíte de aferição são o mesmo código.

## Visão geral

O fundo adquire duplicatas e cheques pré-datados e paga o cedente pelo valor presente. A taxa de desconto junta uma taxa base da mesa e um spread do tipo de ativo. Quando o pagamento é em dólar, o valor presente em reais já arredondado é convertido pela taxa `USD_BRL` vigente no instante da liquidação.

O operador simula no painel, confirma, e o backend grava uma única liquidação por recebível. Repetir a mesma requisição devolve o registro original.

## Stack tecnológica

| Camada | Escolha | Por quê |
|---|---|---|
| Linguagem do backend | Kotlin 2.1 em Java 21 | Tipagem forte e `BigDecimal` nativo da plataforma, sem biblioteca paralela de dinheiro. |
| Framework | Spring Boot 3.4.2 | Web, validação, transação e JPA maduros para um monolito financeiro. Build Maven. |
| Banco | PostgreSQL 16 | Relacional, ACID, `NUMERIC` de precisão fixa. |
| Migração | Flyway | Schema versionado no repositório. O Hibernate valida; não cria tabela em runtime. |
| API | REST + springdoc | Verbos e status semânticos, contrato em `/swagger-ui.html`. |
| Frontend | Next.js 15 (App Router), React 19, TypeScript | Painel do operador separado da regra de cálculo. |
| Estilo | Tailwind CSS | Layout do painel e do extrato sem um design system extra. |
| Execução | Docker Compose | API, banco e painel sobem juntos, na ordem em que o Postgres fica saudável. |
| Operação | Actuator, Micrometer, Resilience4j | Métricas em Prometheus, timeout e circuit breaker no câmbio. |
| CI | GitHub Actions | `mvn test` no backend e lint + teste no frontend. |

Dinheiro e taxa nascem de string decimal e circulam como `BigDecimal` (`MathContext.DECIMAL128`). `Double`, `Float` e `Math.pow` ficam fora do domínio, da persistência, do JSON e do cálculo do painel. O frontend formata com `Intl.NumberFormat("pt-BR")` e exibe a cotação que a API devolveu.

## Decisões de design

### Camadas

`domain` calcula. `application` abre a transação, resolve vigência e orquestra a liquidação. `infrastructure` persiste. `presentation` valida o HTTP e mapeia o status. O extrato (`GET /api/settlements/report`) lê SQL explícito, com índice em `(settled_at, assignor_id, payment_currency)`, e não percorre grafo JPA.

O desenho de contexto e de contêineres está em [`docs/C4_MODEL.md`](docs/C4_MODEL.md).

### Strategy no motor

A fórmula é uma só:

```text
PV = ValorDeFace / (1 + TaxaBase + Spread) ^ PrazoMeses
```

O que muda por ativo é o spread. `DuplicataStrategy` aplica `0,015`. `ChequeStrategy` aplica `0,025`. `PricingStrategyRegistry` escolhe a strategy pelo tipo. Um tipo desconhecido não tem default silencioso: o `when` é exaustivo. Incluir um terceiro recebível é uma strategy nova e um golden case, sem alterar a divisão nem o arredondamento.

A taxa base não é constante. É vigência (`effective_at` + valor). O seed é `0,01` (1,00% a.m.). A liquidação copia a vigência cujo `effective_at` é o maior ainda menor ou igual ao instante da operação. O câmbio `USD_BRL` segue a mesma regra: quantos BRL valem 1 USD.

O prazo é inteiro de meses civis, por aniversário, no fuso `America/Sao_Paulo`. O motor de aferição recebe o prazo já resolvido e não consulta calendário nem banco.

### Precisão numérica

1. Fator e divisão correm em precisão 34. Nada de `setScale` no meio do cálculo.
2. O valor presente em BRL é arredondado uma vez: `RoundingMode.HALF_EVEN`, escala 2.
3. O deságio é `face − PV` nessa escala, exato, sempre em BRL — também no pagamento em USD.
4. Pagamento em USD divide o PV já arredondado pela taxa e só então arredonda o dólar a 2 casas. A taxa é gravada como recebida, até 8 casas.

No banco, dinheiro é `NUMERIC(19,2)` e taxa é `NUMERIC(18,8)`. O JSON devolve string decimal (`"92859.94"`, `"0.01000000"`, `"5.4321"`).

### Resiliência e integridade

Liquidação é integral, uma por recebível, imutável. Não há endpoint de alteração nem de estorno.

- **ACID.** O insert da liquidação e a passagem do recebível para `SETTLED` compartilham uma transação. Falha em qualquer passo reverte as duas escritas. O update da versão acontece antes do insert: se o câmbio estoura o timeout, a transação desfaz e o recebível continua `OPEN`.
- **Bloqueio otimista.** `Receivable` tem `@Version`. As duas liquidações leem a mesma versão; o `UPDATE ... WHERE version = ?` deixa uma confirmação (`201`) e a outra recebe `409` com `ObjectOptimisticLockingFailureException`. `ConcurrentSettlementTest` segura as duas transações nesse ponto.
- **Câmbio.** `ResilientFxService.getLatestRate("USD")` consulta o provedor mockado com timeout de 1s, no máximo 2 tentativas e circuit breaker. O fallback não usa taxa antiga: lança regra de negócio e a API responde `422`.
- **Idempotência.** `POST /settlements` exige o header `Idempotency-Key` (UUID). A chave é única, gravada com o hash canônico do corpo. A mesma chave e o mesmo corpo devolvem a liquidação original (`200`) e uma única linha. A mesma chave com outro corpo responde `409`. Outra chave para recebível já liquidado responde `409`. Erro de validação não chega a consumir a chave. A primeira liquidação bem-sucedida responde `201`. O header entra no MDC e sai em cada linha de log.

O registro imutável guarda face, prazo, tipo, cedente, taxa base, spread, valor presente, deságio, moeda e valor de pagamento, câmbio usado, timestamps e a chave de idempotência.

Códigos de erro: `400` validação, `404` recebível ausente, `409` conflito de estado ou de chave, `422` regra de negócio (sem vigência, vencimento inválido). O corpo não traz stack nem SQL.

## Como executar localmente

Na raiz do repositório:

```bash
docker-compose up --build
```

O Compose v2 aceita o mesmo arquivo com `docker compose up --build`. O backend espera o healthcheck do Postgres. Credencial local do banco: usuário `credit`, senha `credit`, database `credit_engine`.

| Serviço | URL |
|---|---|
| Painel do operador | http://localhost:3000 |
| API | http://localhost:8080 |
| Swagger | http://localhost:8080/swagger-ui.html |
| PostgreSQL | `localhost:5432` |
| Métricas | http://localhost:8080/actuator/prometheus |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3001 |

O painel pede valor de face, vencimento, tipo e moeda de pagamento, mostra a simulação e liquida com uma `Idempotency-Key` por intenção. O extrato lista as liquidações com paginação no servidor.

Para encerrar: `docker-compose down`. O volume `postgres_data` preserva o banco entre subidas.

## Como rodar os testes

A suíte sobe um PostgreSQL descartável via Testcontainers. O Docker do host precisa estar acessível.

```bash
docker run --rm \
  -v "$PWD/backend:/app" \
  -w /app \
  -v /var/run/docker.sock:/var/run/docker.sock \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -B test
```

`PricingEngineGoldenCasesTest` chama o motor com prazo, taxa base e câmbio já resolvidos. Falha se qualquer centavo divergir.

| Caso | Assert |
|---|---|
| C1 — duplicata, R$ 100.000,00, 3 meses, BRL | PV `92859.94`, deságio `7140.06` |
| C2 — cheque, R$ 25.000,00, 2 meses, BRL | PV `23337.77`, deságio `1662.23` |
| C3 — duplicata, pagamento USD, câmbio `5.4321` | pagamento `17094.67`, deságio BRL `7140.06` |
| Half-even | `1.005` → `1.00`, `1.015` → `1.02` |
| Ordem da conversão | PV de C1 com câmbio `5.0037` → `18558.25` |

`SettlementServiceIdempotencyTest` cobre o replay da mesma chave e o `409` quando a chave se repete com outro corpo. `ConcurrentSettlementTest` cobre o conflito de versão. `FxServiceResilienceTest` cobre o timeout do câmbio.

Com Java 21 e Maven locais, o equivalente dentro de `backend/` é `mvn test`. O GitHub Actions (`.github/workflows/ci.yml`) roda essa suíte e, no frontend, `npm run lint` e `npm test`. O job falha se `PricingEngineGoldenCasesTest` ou o lint falharem.

### Frontend

Na pasta `frontend/`, com Node.js 22:

```bash
npm install
npm test
npm run test:ui
```

`npm test` roda as funções de dinheiro (`lib/money.test.ts`) e, em seguida, a suíte de interface com Vitest. `npm run test:ui` reexecuta essa suíte de componentes em modo watch. As chamadas HTTP são interceptadas pelo Mock Service Worker; o navegador não precisa da API no ar.

A cobertura da interface inclui a troca entre Mesa e Extrato, a máscara do valor de face, a simulação com `POST /api/quotes` mockado (o card mostra o valor devolvido pela API, sem recálculo local), a liquidação com `Idempotency-Key`, as mensagens de erro `400`, `409` e `422`, e o extrato com paginação `page` no servidor e filtros na query.

## Observabilidade

Com a stack no ar (`docker-compose up`), o Prometheus coleta `backend:8080/actuator/prometheus` a cada 5 segundos. O Grafana já sobe com esse datasource e com o dashboard **Credit Engine Metrics**.

Abra http://localhost:3001 e entre com `admin` / `admin`. O dashboard mostra a taxa de liquidações por segundo e a latência máxima da precificação. Rode o k6 com a stack no ar para ver os painéis se moverem.

O texto cru continua em:

```bash
curl -s http://localhost:8080/actuator/prometheus | grep credit_engine
```

| Nome no código | Nome no Prometheus | O que mede |
|---|---|---|
| `credit_engine.pricing.latency` | `credit_engine_pricing_latency_seconds` | Tempo do cálculo do valor presente, com as tags `receivable_type` e `currency` |
| `credit_engine.settlements.total` | `credit_engine_settlements_total` | Liquidações, com as tags `currency` e `status` (`success` ou `failed`) |

O `# HELP` de cada métrica descreve esse recorte. O log da API é uma linha JSON por evento. O campo `idempotencyKey` carrega o header da liquidação.

## Teste de carga

Com a stack local no ar e o [k6](https://k6.io/) instalado, na raiz do repositório:

```bash
k6 run load-tests/settlement-load-test.js
```

O script cria um recebível por iteração e liquida com uma `Idempotency-Key` nova. Os limites são os do SPEC: cotação com p95 abaixo de 50 ms e liquidação com p95 abaixo de 200 ms. Para apontar outro host:

```bash
k6 run -e BASE_URL=http://localhost:8080 load-tests/settlement-load-test.js
```

## Endpoints principais

Valores monetários e taxas trafegam como string decimal no JSON de resposta. O body de entrada aceita número JSON, que o Jackson materializa em `BigDecimal`.

| Método | Rota | Função |
|---|---|---|
| `POST` | `/api/quotes` | Simula prazo, PV, deságio e valor de pagamento. Não grava. |
| `POST` | `/api/receivables` | Cadastra o recebível em aberto. Responde `201`. |
| `GET` | `/api/receivables/{id}` | Lê um recebível. `404` se não existir. |
| `POST` | `/settlements` | Liquida. Header obrigatório `Idempotency-Key`. Body: `{ "receivableId": "<uuid>" }`. `201` na primeira vez, `200` no replay. |
| `GET` | `/api/settlements/report` | Extrato. Filtros `from`, `to`, `assignorId`, `paymentCurrency`. Página default 20, máximo 100. |
| `POST` | `/api/rates/base` | Publica vigência da taxa base. Body: `{ "rate": "0.01" }`. |
| `POST` | `/api/rates/usd-brl` | Publica vigência do câmbio BRL por 1 USD. |
| `GET` | `/actuator/prometheus` | Métricas do Micrometer. |
| `GET` | `/actuator/health` | Saúde da API. |

Documentação interativa: http://localhost:8080/swagger-ui.html.
