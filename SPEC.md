# SPEC — Credit Engine (Fase 0)

Contrato de precificação e liquidação. O código da Fase 1 implementa este documento. Onde o enunciado é ambíguo, vale a premissa da seção 1. Os golden cases aferem o motor com insumos já normalizados (seção 5); a conversão de data e a leitura de vigência ficam na aplicação, fora dessa aferição.

Stack fixada para a Fase 1: backend Kotlin + Spring Boot (Java 21), PostgreSQL, frontend React + Next.js (App Router, TypeScript).

## 1. Premissas

Fórmula única, juros compostos mensais, taxas na mesma unidade do prazo:

```text
PV = ValorDeFace / (1 + TaxaBase + Spread) ^ PrazoMeses
```

Taxa base e spread são frações mensais e entram **somadas** no fator. Duplicata mercantil: spread `0,015`. Cheque pré-datado: spread `0,025`.

| Ambiguidade | Premissa adotada |
|---|---|
| Unidade do prazo | Inteiro de meses. O motor não recebe dias nem expoente fracionário. |
| Data de vencimento → meses | Meses civis completos por aniversário, no fuso `America/Sao_Paulo`, entre a data civil da cotação/liquidação e o vencimento. `2026-01-15` → `2026-04-15` = 3; `2026-01-15` → `2026-04-14` = 2. Dia inexistente cai no último dia do mês (`31/01` → `28/02` conta o mês). Prazo mínimo 1. Vencimento na data de referência ou antes é recusado. Sem calendário de feriados e sem pró-rata. |
| Origem da taxa base | Parâmetro versionado da mesa (`effective_at` + valor), no mesmo desenho da taxa de câmbio. Seed: `0,01` (1,00% a.m.). A liquidação copia a vigência cujo `effective_at` é o maior ainda menor ou igual ao instante da liquidação. |
| Câmbio na liquidação | Par `USD_BRL`: quantos BRL valem 1 USD (coluna “BRL/USD” do enunciado; `5,4321` = R$ 5,4321 por US$ 1). Vale a taxa vigente no instante da liquidação, copiada para o registro. A simulação usa a vigência do instante da simulação e não grava liquidação. |
| Moeda de face | BRL nesta entrega. Pagamento em BRL ou USD. |
| Cross-currency | Converte o PV em BRL **já arredondado**: `pagamentoUSD = PV_BRL / brl_per_usd`, e só então arredonda o USD a 2 casas. |
| Deságio | Sempre na moeda de face: `face − PV_BRL arredondado`. No caso USD o deságio permanece em BRL. |
| O que é uma liquidação | Integral, uma por recebível, imutável. Cedente identifica o filtro do extrato e não altera spread. Estorno, liquidação parcial e lote ficam fora. |
| Quem calcula | Só o backend. O painel envia o formulário, exibe a cotação e, ao confirmar, reutiliza uma única `Idempotency-Key` por intenção de liquidação. |

## 2. Precisão numérica

Dinheiro e taxa nascem de string decimal. `float`/`double` e `Math.pow` não entram no domínio, na persistência, no JSON nem no frontend.

| Camada | Representação |
|---|---|
| Aplicação (Kotlin) | `BigDecimal`. Fator e divisão com `MathContext.DECIMAL128`. Potência com `BigDecimal.pow(Int)`. |
| Banco | `NUMERIC(19,2)` para dinheiro. `NUMERIC(18,8)` para taxa base, spread e câmbio. Sem `FLOAT`/`REAL`/`DOUBLE PRECISION`. |
| API JSON | String decimal canônica (`"92859.94"`, `"0.01000000"`, `"5.4321"`). |
| Frontend | Formata com `Intl.NumberFormat("pt-BR")`. Não recalcula. |

Arredondamento: `RoundingMode.HALF_EVEN`, escala 2, **uma vez por resultado monetário de saída**.

1. Calcula o fator e o quociente em precisão 34, sem `setScale` no meio.
2. Arredonda o PV para escala 2. Esse é o valor presente em BRL.
3. Deságio = face (escala 2) − PV (escala 2), exato, sem novo arredondamento.
4. Pagamento BRL = esse PV. Pagamento USD = `(PV escala 2) / câmbio`, arredondado de novo a 2 casas. A taxa de câmbio é gravada como recebida, até 8 casas, sem arredondamento próprio.

C1, C2 e C3 não caem em empate de half-even (o dígito decisivo não é 5 seguido de zeros). Um teste de política cobre o empate: `1.005` → `1.00`, `1.015` → `1.02`. Outro trava a ordem da conversão, porque em C3 as duas ordens coincidem em `17.094,67`: com o PV bruto de C1 e câmbio `5.0037`, arredondar o BRL antes produz `18.558,25`; dividir o bruto produz `18.558,26`. O sistema fica com `18.558,25`.

## 3. Perguntas ao negócio

Até haver resposta, a implementação segue a premissa da seção 1.

1. O prazo da mesa é mês civil completo, dias corridos/30 ou dias úteis? Muda o valor líquido de quase toda duplicata que não vence no aniversário.
2. A taxa base é parâmetro da mesa, percentual do CDI ou curva do fundo? Quem altera, com que alçada, e a vigência é intradiária?
3. O câmbio da liquidação é a taxa da tesouraria no clique, a PTAX de fechamento ou a do vencimento? Há spread de conversão além do spread do ativo?
4. O fundo compra título que já nasce em USD, ou só paga em USD um título em BRL?
5. Existe liquidação parcial, lote de recebíveis ou estorno contábil? Se existe, a imutabilidade vira lançamento de compensação, não `UPDATE`.
6. Spread depende de cedente, rating ou prazo, além do tipo do ativo?
7. Vencimento em fim de semana ou feriado antecipa a data?
8. Quem autentica no painel (mesa interna, perfil único) e a API sai da máquina local?

## 4. Critérios de aceite

**Usabilidade.** O operador informa valor de face, vencimento, tipo e moeda de pagamento e vê PV, deságio e, em USD, taxa e valor em dólar antes de confirmar. A confirmação usa a última cotação exibida. Erro de validação aponta o campo. Extrato filtra por período, cedente e moeda, com paginação no servidor.

**Segurança.** Consultas parametrizadas. Tipo, moeda e identificadores são enums ou UUID, nunca concatenados em SQL. Valor de face `> 0` e com no máximo 2 casas; prazo derivado `>= 1`; moeda de pagamento só `BRL` ou `USD`; taxa de câmbio `> 0` quando a moeda é USD. Falha de validação responde `400`; regra de negócio (sem vigência, vencimento inválido) responde `422`; conflito de estado responde `409`; ausência responde `404`. Corpo de erro não traz stack nem SQL. Não há operação de alterar ou apagar liquidação; o registro nasce completo na mesma transação em que o recebível passa a `SETTLED`. Autenticação fica fora desta entrega, restrita ao uso local, até a resposta da pergunta 8.

**Idempotência.** `POST /settlements` exige `Idempotency-Key` (UUID). A chave é persistida com o hash canônico do corpo. Mesma chave e mesmo corpo devolvem a liquidação original, com uma única linha. Mesma chave e corpo diferente respondem `409`. Outra chave para recebível já liquidado responde `409`. Erro de validação não consome a chave. Concorrência: coluna `version` no recebível; das duas liquidações simultâneas, uma confirma e a outra recebe `409`. As duas escritas (liquidação + status) compartilham uma transação; falha reverte as duas. O registro imutável guarda face, prazo, tipo, cedente, taxa base, spread, PV, deságio, moeda e valor de pagamento, câmbio usado, timestamps e a chave de idempotência.

**Desempenho.** Cotação: uma leitura de vigência e o cálculo, sem escrita. Liquidação: uma transação curta, no máximo as leituras de vigência e as duas escritas. Extrato: SQL explícito (o relatório não percorre grafo ORM), página default 20, máximo 100, índices em `(effective_at)` das vigências e em `(settled_at, assignor_id, payment_currency)` das liquidações. Meta local, base de avaliação com até 100 mil liquidações: cotação p95 < 50 ms, liquidação p95 < 200 ms, página do extrato p95 < 300 ms.

## 5. Golden cases — validação

Um único motor executa produção e aferição. O teste chama o motor com prazo, taxa base e câmbio já resolvidos. Não passa por calendário, banco nem “última taxa”.

Insumos fixos da aferição: taxa base `0,01`; prazo inteiro; half-even na saída; conversão pelo PV em BRL já arredondado.

| Caso | Chamada do motor | Fator exato | Bruto | Assert |
|---|---|---|---|---|
| C1 | face `100000.00`, duplicata, 3 meses, BRL | `1,025^3 = 1,076890625` | `92859,9410…` | PV `92859.94`, deságio `7140.06`, pagamento BRL `92859.94` |
| C2 | face `25000.00`, cheque, 2 meses, BRL | `1,035^2 = 1,071225` | `23337,7675…` | PV `23337.77`, deságio `1662.23`, pagamento BRL `23337.77` |
| C3 | mesmos insumos de C1, pagamento USD, câmbio `5.4321` | o de C1 | PV BRL como C1 | pagamento `17094.67`, deságio BRL `7140.06`, câmbio gravado `5.4321` |

`100000.00 − 92859.94 = 7140.06` e `25000.00 − 23337.77 = 1662.23` fecham por subtração de escala 2. C3 reutiliza o PV de C1; o deságio não é recalculado a partir do dólar.

O teste automatizado (`PricingEngineGoldenCasesTest`) falha se qualquer valor divergir de `0,01`. Junto dele rodam o empate half-even e o contraexemplo de ordem (`5.0037` → `18558.25`). Suíte verde — os três golden cases e os dois testes de política — é condição de aceite do motor.
