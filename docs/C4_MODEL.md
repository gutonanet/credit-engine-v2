# C4 — Credit Engine

Diagramas de contexto e de contêineres. O câmbio é um provedor mockado: a API lê a vigência `USD_BRL` atrás de timeout, retry e circuit breaker.

## Nível 1 — System Context

```mermaid
C4Context
  title Credit Engine — contexto

  Person(operator, "Operador da mesa", "Precifica e liquida recebíveis em BRL ou USD")
  System(engine, "Credit Engine", "Calcula o valor presente e registra a liquidação")
  System_Ext(fx, "Provedor de câmbio", "Mock da tesouraria. Entrega a taxa USD/BRL vigente")

  Rel(operator, engine, "Simula e confirma liquidações")
  Rel(engine, fx, "Consulta getLatestRate(USD)", "HTTPS interno")
```

## Nível 2 — Container

```mermaid
C4Container
  title Credit Engine — contêineres

  Person(operator, "Operador da mesa", "Usa o painel")

  Container(web, "Frontend", "Next.js 15, React", "Mesa e extrato. Não recalcula o deságio")
  Container(api, "API", "Kotlin, Spring Boot, Java 21", "Motor de precificação, liquidação ACID, idempotência e métricas")
  ContainerDb(db, "PostgreSQL", "PostgreSQL 16", "Recebíveis, liquidações imutáveis e vigências de taxa")
  System_Ext(fx, "Câmbio mockado", "Vigência USD_BRL atrás de Resilience4j")

  Rel(operator, web, "Opera", "HTTPS")
  Rel(web, api, "JSON", "HTTP :8080")
  Rel(api, db, "Lê e grava", "JDBC")
  Rel(api, fx, "getLatestRate(USD)", "timeout 1s, 2 tentativas")
```
