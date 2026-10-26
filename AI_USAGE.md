Prompts estatégicos
Atue como um Engenheiro de Software Sênior. Preciso criar o arquivo SPEC.md referente à Fase 0 do desafio antes de escrever qualquer código.
Com base no arquivo do desafio, gere o SPEC.md resolvendo todas as ambiguidades do enunciado, contemplando:
Premissas adotadas (unidade de prazo, origem da taxa base, política de câmbio na liquidação).
Decisões de precisão numérica (uso de BigDecimal/Decimal, arredondamento banker's rounding half-even de 2 casas no resultado final).
Perguntas que seriam feitas ao time de negócio.
Critérios de aceite (segurança, idempotência e performance).
Mapeamento de como os Golden Cases (C1, C2, C3) serão validados.
Considere o backend em kotlin com spring boot e o frontend em React e Next.js.


Atue como um Staff Software Engineer. Você vai criar a solução completa e conteinerizada para o projeto "Credit Engine", guiando-se ESTRITAMENTE pelas regras de negócio e precisão numérica definidas no arquivo @SPEC.md.
Crie o projeto estruturado em um monorepo com duas pastas principais: `/backend` e `/frontend`, além de um `docker-compose.yml` na raiz.
### ETAPA 1: Infraestrutura (Docker)
1. Crie um `docker-compose.yml` na raiz que orquestre 3 serviços:
   - `postgres`: Banco de dados PostgreSQL 15+.
   - `backend`: Aplicação Kotlin/Spring Boot (exposta na porta 8080).
   - `frontend`: Aplicação Next.js (exposta na porta 3000).
2. Crie um `Dockerfile` otimizado dentro da pasta `/backend` (usando Java 21) e outro na pasta `/frontend` (Node.js/Next.js).
### ETAPA 2: Backend (Kotlin + Spring Boot + Java 21)
1. Stack: Spring Web, Spring Data JPA, PostgreSQL Driver, Validation.
2. Crie a estrutura de pacotes limpa (ex: `domain`, `application`, `infrastructure`, `presentation`).
3. REGRAS CRÍTICAS DE NEGÓCIO E PRECISÃO (Leia o SPEC.md):
   - NUNCA use `Double`, `Float` ou `Math.pow`. Use EXCLUSIVAMENTE `BigDecimal` com `MathContext.DECIMAL128`.
   - O cálculo do fator é `BigDecimal.pow(Int)`.
   - O arredondamento final monetário deve ser `RoundingMode.HALF_EVEN` com 2 casas decimais.
   - Implemente o motor de precificação usando o padrão Strategy (`DuplicataStrategy` com spread 0.015 e `ChequeStrategy` com spread 0.025).
4. TDD (Golden Cases):
   - Crie a classe de teste `PricingEngineGoldenCasesTest.kt` contendo os testes C1, C2 e C3 com os valores exatos da tabela do SPEC.md.
   - Adicione testes para garantir o desempate half-even (ex: 1.005 -> 1.00).
5. Persistência e Idempotência:
   - Crie a entidade `Receivable` com `@Version` (Optimistic Locking).
   - Crie a entidade `Settlement` (Registro imutável) com uma coluna `idempotency_key` com constraint UNIQUE.
   - O banco deve usar `NUMERIC(19,2)` para dinheiro e `NUMERIC(18,8)` para taxas.
6. Controllers:
   - Crie `POST /settlements` (exigindo o header `Idempotency-Key` e garantindo transação `@Transactional`).
   - Configure o CORS globalmente para permitir requisições do frontend (porta 3000).
### ETAPA 3: Frontend (React + Next.js App Router + TypeScript)
1. Inicialize um projeto Next.js limpo na pasta `/frontend` (use Tailwind CSS).
2. Crie uma interface profissional de "Painel do Operador" (`app/page.tsx`) contendo:
   - Um formulário para input: Valor de Face, Vencimento, Tipo (Duplicata/Cheque) e Moeda de Pagamento (BRL/USD).
   - Simulação em tempo real (chamando o backend ou calculando o preview localmente com uma biblioteca como `decimal.js`).
   - Um botão de "Liquidar" que gera um UUID (Idempotency-Key) e faz o POST para a API do backend.
   - Tratamento visual de erros (Toast ou Alert) capturando os status 400, 409 e 422 descritos no SPEC.
3. Crie uma listagem simples (Grid) consumindo a rota analítica do extrato.
INSTRUÇÕES DE EXECUÇÃO PARA O AGENTE:
- Trabalhe passo a passo. Gere os arquivos essenciais do backend primeiro, garantindo que a lógica matemática central e os testes estejam perfeitos antes de criar os controllers.
- Mantenha o código limpo, auto-explicativo e sem classes "anêmicas" onde aplicável.
- Não gere testes irrelevantes de "happy path" em massa; foque na matemática, idempotência e locking otimista.


Atue como Staff Software Engineer e reescreva completamente o arquivo `README.md` do projeto Credit Engine. 
O arquivo atual está muito básico. O edital do desafio exige que o README seja "a cara do projeto", cobrindo setup, design arquitetural e decisões. 
Crie um README profissional, limpo e detalhado em Markdown, contendo obrigatoriamente as seguintes seções:
1. **Visão Geral:** Um breve resumo do que é o Credit Engine (Plataforma de Cessão de Crédito Multimoedas).
2. **Stack Tecnológica:** Liste e justifique brevemente as escolhas (Kotlin, Spring Boot 3.4, Java 21, Next.js 15, Tailwind, PostgreSQL, Flyway e Docker). Destaque o uso de `BigDecimal` nativo do Java em vez de ponto flutuante.
3. **Decisões de Design (Arquitetura e Negócio):** 
   - Explique a adoção do padrão *Strategy* no motor de precificação.
   - Destaque a garantia de **Precisão Numérica** (uso estrito de BigDecimal, scale 2, RoundingMode.HALF_EVEN).
   - Destaque as garantias de **Resiliência e Integridade**: transações ACID, bloqueio otimista (@Version) e o mecanismo de Idempotência (`Idempotency-Key`) no endpoint de liquidação.
4. **Como Executar Localmente:**
   - Instruções claras com `docker-compose up --build`.
   - Tabela de portas e URLs (Frontend em 3000, API em 8080, Swagger em 8080/swagger-ui.html).
5. **Como Rodar os Testes (Golden Cases):**
   - Comando explícito para rodar os testes via Docker (usando Maven/Gradle e Testcontainers), enfatizando que a suíte `PricingEngineGoldenCasesTest` garante a precisão exigida no Case.
6. **Endpoints Principais:** Breve documentação das rotas RESTful implementadas.
Mantenha um tom técnico, executivo e direto. Evite jargões desnecessários ou texto gerado em massa ("fluff"). O objetivo é impressionar o time de engenharia logo na primeira página.


Atue como um Desenvolvedor Frontend Sênior (Pleno+). O código atual do frontend em React/Next.js não atende ao padrão de qualidade exigido pelo case técnico. Preciso refatorar o frontend (Next.js 15, App Router, TypeScript, Tailwind CSS) para atingir os critérios de "Pleno+" definidos no SPEC.md, garantindo separação de responsabilidades, tipagem forte e um design limpo.
A interface deve seguir o layout da referência fornecida, mas com as seguintes exigências de arquitetura e UI/UX:
### 1. Refatoração da Arquitetura (Pleno+)
- Separe a lógica de apresentação do estado global/negócio. Crie custom hooks (ex: `useSettlementForm.ts`, `useSettlementsList.ts`) para gerenciar as chamadas de API, simulação e envio de formulário, mantendo os componentes UI puramente visuais.
- Crie arquivos de serviços dedicados (`api/settlements.ts`) para centralizar o fetch e tratar erros (400, 409, 422) usando blocos `try/catch`, repassando mensagens amigáveis para a UI (use uma biblioteca de toast como `react-hot-toast` ou crie um componente de alerta simples).
- Garanta que toda tipagem (Interfaces/Types) dos payloads e retornos da API esteja centralizada em `types/settlement.ts`.
### 2. Painel Principal com Layout de Abas (Tabs)
- Modifique o layout principal para exibir uma interface baseada em abas (Tabs).
- **Aba 1: "Mesa (Operação)"**: Deve conter o formulário de "Novo Recebível" e o card lateral de "Simulação" (exatamente como na imagem de referência).
- **Aba 2: "Extrato de Liquidações" (Grid Pleno+)**: Mova a tabela de "Extrato de liquidações" para esta aba exclusiva.
### 3. Formulário de Novo Recebível e Simulação (Aba 1)
- **Inputs**: Cedente (texto), Valor de face (BRL - numérico), Vencimento (Datepicker), Tipo (Select: Duplicata mercantil / Cheque pré-datado), Moeda de pagamento (Select: BRL / USD).
- A simulação (Prazo, VP, Deságio, Líquido) deve ser atualizada e formatada na tela usando `Intl.NumberFormat("pt-BR")` sem perda de precisão (evite `parseFloat` puro em cálculos locais, se houver cálculo de preview local, use `decimal.js`, ou melhor, exiba a resposta da simulação vinda da API).
- O botão "Liquidar" deve enviar o header `Idempotency-Key` (gere um UUIDv4 no clique).
### 4. Grid de Transações (Aba 2 - Exigência Pleno+)
- A tabela do extrato **DEVE** suportar Paginação Server-Side.
- O componente da tabela deve receber `page` e `size` do estado e disparar o fetch para `GET /api/settlements/report?page=0&size=20`.
- Inclua componentes de controle de paginação (Botões "Anterior" e "Próximo") e um botão "Atualizar" no topo do extrato.
- Formate a exibição da "Data" e dos valores monetários corretamente na tabela.
Gere os componentes limpos, modulares e focados na robustez exigida para sistemas financeiros.


Atue como um Staff Software Engineer e Tech Lead. Faça um Code Review profundo de toda a base de código atual (backend e frontend) cruzando ESTRITAMENTE com as regras do SPEC.md.
Seu objetivo é identificar e CORRIGIR automaticamente qualquer violação dos critérios de aceite. Verifique e refatore os seguintes pontos críticos:
1. Anti-padrões Eliminatórios (Precisão Numérica):
- Varra o backend (Kotlin) e garanta que NÃO exista nenhum uso de `Double`, `Float` ou `Math.pow` para valores monetários ou taxas[cite: 15, 16]. Tudo deve ser `BigDecimal` com `MathContext.DECIMAL128`[cite: 15].
- Garanta que o banco de dados (PostgreSQL/Flyway) esteja usando `NUMERIC(19,2)` para dinheiro e `NUMERIC(18,8)` para taxas[cite: 15].
- Verifique se o arredondamento (`RoundingMode.HALF_EVEN`, 2 casas) está sendo aplicado APENAS no resultado monetário final, e nunca no meio do cálculo do fator[cite: 15].
2. Integridade e Idempotência (Backend):
- O endpoint de liquidação (`POST /settlements`) possui transação ACID (`@Transactional`)? Se alguma etapa falhar (ex: update do recebível), o insert da liquidação deve sofrer rollback[cite: 15].
- A `Idempotency-Key` está sendo recebida no Header, validada e persistida com constraint UNIQUE no banco para evitar liquidação duplicada?[cite: 15, 16] Se não, implemente.
- Verifique o tratamento de erros global: não pode haver exceções "engolidas" retornando status 200[cite: 16]. Aplique os status 400, 404, 409 e 422 conforme o SPEC[cite: 15].
3. Golden Cases Test (TDD):
- Valide se a classe `PricingEngineGoldenCasesTest` está implementada e se os assertions cravam exatamente os centavos da tabela do Case (C1, C2, C3)[cite: 15, 16].
4. Frontend Pleno+ (Next.js):
- Valide se a tabela de Extrato de Liquidações está consumindo a API com paginação Server-Side (`?page=0&size=20`)[cite: 15, 16].
- Garanta que não há cálculos de ponto flutuante quebrando os centavos na UI local[cite: 15].
Gere as correções necessárias em todos os arquivos defeituosos. Caso a arquitetura já esteja correta, faça pequenas refatorações de clean code (SOLID) e adicione comentários explicando que a regra atende ao SPEC.



Atue como Staff Software Engineer e implemente os requisitos de operação e arquitetura (Nível Sênior) do projeto Credit Engine, baseando-se estritamente no @SPEC.md. 
Trabalhe etapa por etapa e gere os seguintes artefatos e implementações no backend (Kotlin/Spring Boot) e na infraestrutura:
### 1. Integração Contínua (GitHub Actions)
- Crie o arquivo `.github/workflows/ci.yml`.
- Configure jobs separados para o Backend (setup Java 21, build, run tests via Gradle/Maven com Testcontainers) e para o Frontend (setup Node.js, npm install, lint, test).
- O CI deve falhar caso o `PricingEngineGoldenCasesTest` ou as validações de linter falhem.
### 2. Concorrência e Optimistic Locking (TDD)
- Garanta que a entidade `Receivable` possui a anotação `@Version` (JPA Optimistic Locking).
- Crie a classe de teste de integração `ConcurrentSettlementTest.kt`.
- Implemente um teste que dispara duas threads/CompletableFutures simultâneas tentando liquidar o mesmo `receivableId`. O teste DEVE validar que uma requisição retorna sucesso (200/201) e a outra lança `ObjectOptimisticLockingFailureException`, resultando em HTTP 409 (Conflict).
### 3. Resiliência e Integração de Câmbio (Currency Engine)
- Integre a biblioteca `Resilience4j` (ou mecanismo nativo do Spring).
- Configure um `CircuitBreaker`, `Retry` (máximo 2 tentativas) e `TimeLimiter` (Timeout de 1s) na chamada do serviço de câmbio (mockado) `fxService.getLatestRate("USD")`.
- Crie um fallback method que falhe graciosamente (lançando exceção de negócio 422, impedindo a liquidação com taxa nula/antiga).
- Crie a classe `FxServiceResilienceTest.kt` simulando latência/queda do provedor e garantindo que o timeout aborta a liquidação e faz rollback.
### 4. Observabilidade (Logs e Métricas)
- Adicione logs estruturados (JSON ou SLF4J com MDC) no `SettlementController` inserindo o `Idempotency-Key` no contexto do log.
- Configure o `Micrometer` para expor o endpoint `/actuator/prometheus`.
- Crie e registre pelo menos duas métricas de negócio customizadas:
  1. `Timer` chamado `credit_engine.pricing.latency` para medir o tempo exato do cálculo matemático.
  2. `Counter` chamado `credit_engine.settlements.total` clusterizado por tag `currency` e `status` (success/failed).
### 5. Teste de Carga (Performance)
- Crie uma pasta `/load-tests` na raiz e gere um script `k6` (`settlement-load-test.js`).
- O script deve simular a liquidação de recebíveis disparando requisições com `Idempotency-Key` únicas, com o objetivo de provar os critérios do SPEC: cotação p95 < 50 ms e liquidação p95 < 200 ms.
### 6. Diagramas de Arquitetura (C4 Model)
- Crie um arquivo `docs/C4_MODEL.md`.
- Escreva em sintaxe `Mermaid` os diagramas C4 Nível 1 (System Context) e Nível 2 (Container), ilustrando os atores (Operador da Mesa), o Frontend (Next.js), a API Backend (Kotlin/Spring), o Banco de Dados (PostgreSQL) e o sistema de Câmbio Mockado.
### 7. Atualização da Documentação (README.md)
- Atualize o `README.md` para refletir as novas implementações de nível Sênior.
- Adicione instruções claras de como visualizar as métricas geradas no endpoint `/actuator/prometheus`.
- Explique brevemente na seção de arquitetura as estratégias de resiliência adotadas (Circuit Breaker) e como a concorrência é garantida (Optimistic Locking).
- Inclua o comando exato e o passo a passo de como rodar o script de teste de carga do `k6` localmente.
- Adicione um link referenciando o novo arquivo `docs/C4_MODEL.md`.
Gere os códigos com qualidade de produção, respeitando o isolamento de camadas e o uso de `BigDecimal`.


Atue como Staff Software Engineer. Analisando a suíte de testes atual do projeto Credit Engine, notamos que o core de negócio (Idempotência, Concorrência, Resiliência e Golden Cases) já está muito bem coberto com testes integrados (Testcontainers) e unitários. 
Porém, faltam os testes da camada de API (Controllers) para garantir o contrato RESTful definido no @README.md.
Por favor, crie as seguintes classes de teste utilizando `@SpringBootTest`, `@AutoConfigureMockMvc` e `@Testcontainers` (PostgreSQL):
1. **`SettlementControllerWebTest.kt` (ou adicione ao existente):**
   - Teste o endpoint `POST /api/quotes`. Valide se ele retorna os mesmos valores de simulação sem persistir dados no banco de dados.
   - Teste validações de borda (HTTP 400 Bad Request) para o `POST /settlements`: envie um payload sem o header `Idempotency-Key`, com um UUID inválido, ou com o corpo vazio.
   - Teste o retorno HTTP 404 (Not Found) ao tentar liquidar um `receivableId` que não existe no banco.
2. **`SettlementReportControllerTest.kt`:**
   - Teste o endpoint `GET /api/settlements/report`.
   - Insira algumas liquidações no banco de teste (variando cedentes e moedas) e faça requisições `MockMvc` validando se os filtros `assignorId` e `paymentCurrency` aplicam as restrições corretas no JSON de saída.
   - Valide se a paginação (page=0, size=20) retorna os metadados corretos de página.
3. **Validação do Exception Handler:**
   - Garanta em ao menos um teste que um erro de negócio conhecido (ex: vencimento no passado) retorne estritamente o HTTP 422 Unprocessable Entity, verificando se o JSON de erro NÃO contém o stack trace do Java ou queries SQL vazadas.
Mantenha o padrão já adotado no projeto: uso de `BigDecimal` estrito, inicialização do Postgres via companion object e injeção do `MockMvc`.


Atue como um Engenheiro Frontend Sênior (Software Development Engineer in Test - SDET). Temos um projeto frontend em React + Next.js 15 (App Router, TypeScript) para o "Credit Engine". 
Atualmente, temos apenas testes unitários nativos do Node (`money.test.ts`) testando as funções puras de formatação e máscara. Precisamos agora criar a suíte de testes de componentes e integração para a interface do usuário (UI), garantindo que ela atenda aos critérios "Pleno+".
### 1. Configuração do Ambiente de Testes React
- Configure o ambiente de testes para componentes React. Como estamos usando Next.js, adicione as dependências do `Vitest` (recomendado por ser rápido) ou `Jest`, junto com o `@testing-library/react` e `@testing-library/user-event`.
- Crie o setup file necessário para mockar o `fetch` global ou configure o `msw` (Mock Service Worker) para interceptar as chamadas de API.
### 2. Testes de Navegação e Layout (Tabs)
- Crie um teste `Tabs.test.tsx` (ou no componente de layout principal) para garantir que o usuário consegue alternar entre a aba "Mesa (Operação)" e a aba "Extrato de Liquidações".
### 3. Testes da Aba "Mesa (Operação)" (Formulário e Simulação)
Crie o arquivo `OperationDesk.test.tsx` abrangendo:
- **Input e Máscaras:** Simule a digitação no campo "Valor de face (BRL)". Valide se o valor na tela aplica a máscara corretamente integrando com a função `maskFaceValue` já existente.
- **Simulação (Mock de API):** Ao preencher o formulário, simule a resposta do endpoint `POST /api/quotes`. Verifique se o card lateral "Simulação" exibe os valores devolvidos pela API (Prazo, VP, Deságio, Líquido) usando `formatMoney`, sem realizar recálculos (ponto flutuante) localmente no componente.
- **Liquidação e Sucesso:** Simule o clique no botão "Liquidar". 
  - Verifique se a requisição `POST /settlements` é disparada contendo o header `Idempotency-Key` (UUIDv4 gerado).
  - Simule o retorno 201/200 da API e verifique se o alerta verde de sucesso ("Liquidação confirmada: R$ X.XXX,XX") aparece no topo da tela.
- **Tratamento de Erros:** Simule retornos de erro da API (400 Bad Request, 422 Unprocessable Entity, 409 Conflict) e garanta que a UI exiba mensagens de erro amigáveis ao invés de quebrar.
### 4. Testes da Aba "Extrato de Liquidações" (Grid e Filtros)
Crie o arquivo `SettlementsReport.test.tsx` abrangendo:
- **Renderização da Tabela:** Simule um retorno `GET /api/settlements/report` com dados mockados. Verifique se a tabela renderiza as colunas (Data, Cedente, Face, VP, Deságio, Pagamento) formatando dinheiro e datas corretamente.
- **Paginação Server-Side:** Simule o clique nos botões "Próxima" e "Anterior". Valide se uma nova chamada de API é feita atualizando o parâmetro `?page=` na URL.
- **Filtros:** Preencha os campos de filtro (Cedente, Moeda "Todas/BRL/USD", Data De/Até) e clique no botão "Filtrar". Valide se a API é chamada com os *query params* corretos.
### 5. Atualização da Documentação (README.md)
- Atualize a seção do frontend no arquivo `README.md` (ou crie um `README.md` específico dentro da pasta `/frontend`).
- Adicione as instruções de como instalar as dependências de teste e como executar a suíte de testes localmente e no modo watch (ex: `npm run test` e `npm run test:ui`).
- Documente brevemente que a cobertura de testes inclui navegação, formulários, simulação de chamadas HTTP (mocking) e validação de paginação server-side.
Gere os arquivos de teste adotando boas práticas de acessibilidade (buscando elementos por Role ou LabelText via Testing Library) e evite lógicas frágeis baseadas em seletores CSS.

Atue como um Engenheiro Frontend Sênior (SDET). Analisando a nossa suíte de testes atual (MSW, Tabs, OperationDesk e SettlementsReport), cobrimos muito bem os "happy paths" e o tratamento de erros da API. 
No entanto, baseando-se no comportamento visual esperado para uma aplicação "Pleno+", precisamos adicionar testes para cobrir os detalhes de UI/UX e validações locais que estão faltando.
Por favor, crie ou atualize os arquivos de teste (ex: `OperationDesk.test.tsx` e `Tabs.test.tsx`) para cobrir os seguintes cenários:
### 1. Interação com o Alerta (Toast)
Nas telas de referência, o alerta de sucesso ("Liquidação confirmada...") possui um botão "Fechar".
- Crie um teste que realiza uma liquidação com sucesso e, em seguida, clica no botão "Fechar" (ou botão de fechar/dismiss) do alerta, garantindo que o elemento seja removido do DOM (`not.toBeInTheDocument`).
### 2. Persistência do Alerta entre Abas
Nas imagens, o alerta de sucesso permanece fixo no topo da tela independentemente da aba selecionada.
- Adicione um teste no `Tabs.test.tsx` (ou em um teste de integração de layout) que dispara o alerta de erro ou sucesso na aba "Mesa", alterna para a aba "Extrato" e verifica se o alerta CONTINUA visível no DOM, garantindo que o estado do toast é global (ou elevado no layout) e não morre ao desmontar o formulário.
### 3. Reset do Formulário Após Liquidação
- Adicione uma asserção no teste de liquidação com sucesso (`"settles with an idempotency key and confirms the payment"`) para garantir que, após o alerta de sucesso aparecer, os campos do formulário (Cedente, Valor de face, Vencimento) sejam limpos (resetados para o estado inicial) e o card de Simulação volte ao estado vazio/zerado, preparando a tela para o próximo recebível.
### 4. Validação Local de Formulário (Frontend Boundary)
Atualmente testamos os erros da API (400, 422), mas precisamos testar a validação do próprio frontend antes da requisição.
- Crie um teste `prevents submission when mandatory fields are missing`. 
- Tente clicar no botão "Liquidar" com o formulário em branco ou incompleto (ex: sem preencher a Data de Vencimento).
- Valide se o frontend impede a submissão (seja mantendo o botão `disabled` ou exibindo mensagens de erro de validação nativas/HTML5 ou do React Hook Form) garantindo que NENHUMA requisição POST para `/settlements` ou `/api/quotes` seja disparada no MSW.
Gere os blocos de teste utilizando `@testing-library/react` e `userEvent`, mantendo a consistência com as funções de mock e setup do arquivo `setup.ts`.


Atue como um Engenheiro de Observabilidade e SRE (Nível Staff). O avaliador técnico solicitou melhorias nas métricas geradas no endpoint `/actuator/prometheus` do nosso projeto "Credit Engine".
A saída atual está correta para o scraper do Prometheus, mas precisamos deixar a observabilidade mais rica e fácil de consumir visualmente, criando um ambiente completo de monitoramento local.
Faça as seguintes implementações:
### 1. Enriquecimento das Métricas Customizadas (Micrometer)
Refatore o serviço ou controller onde os timers e counters customizados são gravados:
- Na métrica `credit_engine.pricing.latency`, adicione tags adicionais obrigatórias: `receivable_type` (ex: DUPLICATA_MERCANTIL) e `currency` (ex: BRL ou USD).
- Melhore as descrições (`description`) configuradas na inicialização do Timer/Counter no Micrometer para que o campo `# HELP` no Prometheus fique mais explicativo (ex: `# HELP credit_engine_pricing_latency_seconds Latency of the pricing core algorithm grouped by type and currency`).
### 2. Adição de Prometheus e Grafana no Docker Compose
O avaliador não deve precisar ler texto puro para ver o resultado do teste de carga.
- Adicione os serviços `prometheus` e `grafana` no `docker-compose.yml` da raiz do projeto.
- O Grafana deve expor a porta `3001` (para não conflitar com o frontend em 3000).
### 3. Configuração Automática (Provisioning)
Crie a pasta `/monitoring` na raiz do projeto com:
- `prometheus.yml`: Configurado para fazer scraping do target `backend:8080/actuator/prometheus` a cada 5 segundos.
- `grafana/provisioning/datasources/datasource.yml`: Configurando o Prometheus recém-criado como datasource padrão.
- `grafana/provisioning/dashboards/`: Crie um dashboard básico em formato JSON (pode ser um template simplificado gerado por você) chamado `credit-engine-dashboard.json`. Este dashboard deve conter painéis visualizando o `credit_engine_settlements_total` (Taxa de liquidações por segundo) e o `credit_engine_pricing_latency_seconds_max` (Latência máxima da precificação).
### 4. Atualização da Documentação
- Atualize a seção de Observabilidade no `README.md` explicando que, após rodar `docker-compose up`, o avaliador pode acessar o Grafana em `http://localhost:3001` (login/senha: admin/admin) para ver o Dashboard "Credit Engine Metrics" popular em tempo real durante a execução do script `k6`.
Não altere a lógica de negócio financeira ou os testes já criados, foque estritamente em refinar as métricas e na infraestrutura do Grafana.