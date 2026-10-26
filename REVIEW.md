# REVIEW — `settlement.controller.ts` (Anexo A)

PR rejeitado. O endpoint liquida com valor errado, aceita SQL vindo do cliente e confirma sucesso quando a escrita falha. A ordem abaixo é a de impacto em produção: prejuízo certo em toda liquidação, comprometimento do banco, estado rasgado e falha invisível. A correção de cada item segue o contrato do `SPEC.md`.

Nenhum desses pontos fica para um sprint seguinte. Com este código no ar, o fundo paga o valor errado e não consegue provar depois qual taxa usou.

## Crítico

### 1. Taxa base e spread cravados no código, em unidade de percentual

`BASE_RATE = 1.0` e `spread` `1.5` / `2.5` entram na fórmula como se fossem fração. `1.0` é 100% ao mês, não 1%. O fator da duplicata vira `1 + 1.0 + 1.5 = 3.5`, e `3.5³ = 42.875`.

**Impacto em produção.** Toda liquidação bem-sucedida paga uma fração do valor devido, com HTTP 200. No caso de referência da duplicata de R$ 100.000,00 em 3 meses o cedente recebe **R$ 2.332,36** em vez de **R$ 92.859,94** — R$ 90.527,58 a menos num único título. O cheque de R$ 25.000,00 em 2 meses paga **R$ 1.234,57** em vez de **R$ 23.337,77**. A mesa não vê erro; vê um deságio absurdo. Como a constante só muda com deploy, a mesa não consegue corrigir a curva no dia. Qualquer tipo diferente da string `"DUPLICATA"` — inclusive `"DUPLICATA_MERCANTIL"` — cai no `else` e leva o spread de cheque, na mesma unidade errada.

**Correção proposta.** Taxa base e spread são frações mensais (`0.01`, `0.015`, `0.025`), somadas no fator `(1 + base + spread) ^ prazo`. A base deixa de ser constante: lê-se a vigência com `effective_at` menor ou igual ao instante da liquidação e copia-se o valor para o registro. O spread fica na strategy do tipo, sem default silencioso. Um tipo desconhecido recusa a liquidação. O golden case C1, comparando centavo a centavo, teria barrado este merge.

### 2. SQL injection nas três queries

`receivableId` é interpolado cru no `SELECT`, no `INSERT` e no `UPDATE`. `currency` entra entre aspas simples no `INSERT`. Os dois valores nascem em `req.body`.

**Impacto em produção.** O corpo da requisição passa a fazer parte do SQL. Quem alcança `POST /settlements` lê e altera recebíveis, liquidações e o que mais a credencial da aplicação enxerga — inclusive apagar a trilha que denunciaria o próprio ataque. Não depende do cálculo estar certo. Um id malformado que só quebra a sintaxe já derruba a requisição; o caso grave é o input que a query executa.

**Correção proposta.** Statement parametrizado nas três operações. `receivableId` tipado (UUID ou inteiro) e rejeitado na validação quando o formato não fecha. `currency` só entra no SQL depois de passar por enum `BRL | USD`; valor fora do enum responde 400 e não chega ao driver. Interpolação de SQL no módulo de liquidação quebra o build.

### 3. Sem transação ACID e sem idempotência

O `INSERT` em `settlements` e o `UPDATE` em `receivables` são duas transações com autocommit. Não há `Idempotency-Key`, nem unicidade de recebível na liquidação, nem `version`, nem `UPDATE ... WHERE status = 'OPEN'`. O `SELECT` inicial nem olha o status.

**Impacto em produção.** Duas falhas distintas, as duas pagam duas vezes:

- Escrita rasgada: o insert confirma e o update falha. O recebível continua aberto, com uma liquidação pendurada. O comentário do `catch` descreve exatamente isso e manda seguir.
- Repetição: timeout, retry do cliente ou duplo clique disparam outro `POST`. As duas requisições passam pelo select, as duas inserem, as duas marcam `SETTLED`. Enrolar os dois statements numa transação elimina o rasgo e **não** elimina a segunda liquidação: as duas transações continuam válidas sem trava nem chave única.

É o incidente de liquidação duplicada. O cedente recebe duas vezes; o extrato não tem como saber qual linha é a original, porque a taxa usada nem foi gravada (item 6).

**Correção proposta.** Uma transação só. Dentro dela: trava otimista (`version`) ou `SELECT … FOR UPDATE`, insert da liquidação e `UPDATE` condicionado a `status = 'OPEN'` e à versão lida. Zero linhas atualizadas faz rollback e responde 409. Constraint única em `settlements.receivable_id` e em `idempotency_key`. A mesma chave com o mesmo corpo devolve a liquidação já gravada (uma linha). A mesma chave com outro corpo, ou outra chave para recebível já liquidado, responde 409. Falha em qualquer passo reverte as duas escritas.

## Alto

### 4. `catch` vazio e resposta HTTP 200

O `catch` descarta a exceção. Não há log. Em seguida o handler sempre executa `res.status(200).json({ ok: true, amount })`. O `try` cobre só as duas escritas: falha de precificação ou de câmbio escapa; falha de persistência — a que deixa estado pela metade — é a que o cliente vê como sucesso.

**Impacto em produção.** Operador, gateway e monitor de status HTTP registram sucesso. A taxa de erro fica em zero enquanto o caixa paga dobrado ou acredita ter pago uma liquidação que não existe. `amount` pode ser a string `"Infinity"` ou `"NaN"` (taxa ausente ou zero: em JavaScript o divisor `null` vira 0) e ainda assim a resposta é 200 se o insert recusar o valor. Sem o stack no log, o plantão não reconstrói a sexta-feira à noite.

**Correção proposta.** Remover o `catch` que engole. A transação do item 3 faz rollback. Validação responde 400, regra de negócio (sem vigência, vencimento inválido) 422, conflito 409, recebível ausente 404, falha inesperada 500. Corpo de erro sem stack e sem SQL. Log estruturado com o id da requisição e a causa, na falha. Alerta em liquidação duplicada barrada e em rollback — o status HTTP deixa de ser o único sinal.

### 5. Ponto flutuante (`number`, `Math.pow`, `toFixed`) para dinheiro

`face_value`, a potência e a divisão cambial usam `number` de IEEE-754. `Math.pow` calcula o fator. `toFixed(2)` produz o que vai ao banco e ao JSON, com arredondamento half away from zero, não half-even.

**Impacto em produção.** Com a unidade do item 1, este defeito é o menor dos dois: o título já está dezenas de milhares de reais errado antes do binário importar. Corrigir só a unidade e manter `Math.pow` deixa o motor dependente de um binário que, nestes portes, às vezes acerta o centavo por acaso e erra quando o valor cai no meio do intervalo (half-even) ou quando face e prazo crescem. `toFixed` também devolve string, então o domínio não tem um decimal — tem texto formatado. O enunciado trata ponto flutuante para dinheiro como eliminatório a partir de pleno, e o golden case existe para pegar exatamente este desvio.

**Correção proposta.** `BigDecimal` a partir de string decimal, potência com `pow(Int)`, divisão em `MathContext.DECIMAL128`, `setScale(2, HALF_EVEN)` só no resultado monetário de saída. Banco `NUMERIC(19,2)` para dinheiro e `NUMERIC(18,8)` para taxas. JSON com string decimal. Teste de empate (`1.005` → `1.00`, `1.015` → `1.02`) além de C1–C3, porque os golden cases não caem em empate.

### 6. Câmbio “o mais recente”, fora da transação, sem congelar a taxa

No USD o código chama `fxService.getLatestRate("USD")` e faz `presentValue / rate` com o PV ainda bruto. A taxa não entra no `INSERT`. A chamada fica fora do `try` e fora de qualquer transação com a liquidação.

**Impacto em produção.** “Latest” não é a taxa vigente no instante do negócio: pode ser uma cotação já substituída, de outro par, ou invertida (dólar por real em vez de real por dólar). O pagamento em USD muda sem que o registro explique por quê. Entre a leitura da taxa e o insert, uma atualização de câmbio deixa o valor gravado órfão da taxa que o produziu — auditoria e regulatório não fecham. A ordem também discorda do contrato: converte-se o PV em BRL já arredondado a 2 casas e só então se arredonda o USD. No C3 publicado as duas ordens caem em US$ 17.094,67; com o PV bruto de C1 e câmbio `5.0037`, arredondar antes dá US$ 18.558,25 e dividir o bruto dá US$ 18.558,26. O código implementa a segunda.

**Correção proposta.** Resolver a vigência `USD_BRL` (BRL por 1 USD) com `effective_at <= instante da liquidação`, dentro da mesma transação, e gravar valor e vigência na liquidação. Recusar com 422 se não houver taxa ou se ela não for positiva. Arredondar o PV em BRL (half-even, 2 casas) e só então dividir. O deságio permanece `face − PV_BRL`, também em BRL, no caso USD.

## Médio

### 7. Entrada sem validação e recebível inexistente vira exceção crua

Não se valida face positiva, prazo inteiro `>= 1`, status liquidável nem moeda. O id ausente faz `queryOne` devolver vazio e `receivable.type` lançar `TypeError` fora do `try`. A moeda do body redefine a economia do título: um recebível em BRL liquidado com `USD` é convertido, sem conferir o cadastro.

**Impacto em produção.** Título já liquidado, vencido ou de tipo desconhecido segue até a escrita. Recebível inexistente responde 500 genérico, indistinguível de queda do banco. A moeda livre, mesmo depois de parametrizar o SQL, continua sendo alavanca para pagar no par errado.

**Correção proposta.** Validação na borda, antes de abrir transação: identificador, enum de moeda, tipo conhecido, face `> 0` com 2 casas, prazo `>= 1`. Recebível ausente: 404. Status diferente de aberto: 409. A moeda de pagamento é a do recebível (ou a escolhida na cotação e persistida nele), não um campo solto reprocessado na liquidação. Erro de validação não consome a chave de idempotência.

### 8. Liquidação sem trilha auditável e, no desenho, mutável

O insert grava `receivable_id`, `amount`, `currency`. Ficam de fora face, prazo, tipo, cedente, taxa base, spread, PV em BRL, deságio, câmbio, vigência e o instante do cálculo. Não há ator. Nada no código impede um `UPDATE` posterior dessa linha.

**Impacto em produção.** Diante de divergência com o cedente, o fundo não reconstitui o número. Dois registros do mesmo recebível são indistinguíveis. Qualquer correção “in place” apaga a história — o oposto de uma liquidação de FIDC, que se compensa com lançamento novo, nunca com update.

**Correção proposta.** Registro imutável nascido completo na mesma transação que marca o recebível `SETTLED`: os campos acima, timestamps e a `Idempotency-Key`. Sem endpoint de update ou delete. A aplicação de liquidação não tem método de alteração; privilégio de `UPDATE`/`DELETE` nessa tabela fica fora da role de runtime.

### 9. Nenhuma identificação de quem liquida

O handler não lê operador, papel nem escopo. Se o router também não impõe autenticação, qualquer cliente que alcance a rota liquida carteira.

**Impacto em produção.** Não há como separar erro da mesa, retry automático e ação de quem não deveria operar o caixa. O item 8 não tem a quem atribuir a linha. Se já existir filtro na frente deste arquivo, o gap cai para “o registro continua sem ator” e permanece no item 8.

**Correção proposta.** Autenticação na borda e autorização explícita para liquidar. O sujeito autenticado entra no registro imutável. Fora deste case a API não fica exposta sem esse filtro — a omissão aqui é do código revisado, e o `SPEC.md` já deixa a decisão de identidade como pergunta ao negócio antes de abrir a rede.

## Baixo

### 10. Contrato HTTP opaco e `SELECT *`

No caminho em que as duas escritas funcionam, a resposta ainda é 200 com `{ ok: true, amount }`. O cliente não recebe o id da liquidação, o PV em BRL, o deságio nem a taxa. `SELECT *` acopla o handler a colunas que ele não usa.

**Impacto em produção.** Baixo enquanto os itens críticos existirem; o cliente não consegue conciliar nem exibir o comprovante, e cada coluna nova em `receivables` passa a trafegar no processo que move caixa. `amount` sozinho não distingue valor em BRL de valor em USD além do eco da moeda que o próprio cliente enviou.

**Correção proposta.** Primeira liquidação responde 201 com o recurso (id, valores em string decimal, moeda, câmbio usado, timestamps). Repetição idempotente responde 200 com o mesmo corpo. O repositório projeta só as colunas que a precificação e a trilha usam.

### 11. Precificação, câmbio, persistência e HTTP no mesmo handler

A regra de deságio mora no controller, ao lado do SQL e do status code. Não há strategy por tipo de recebível.

**Impacto em produção.** Baixo de forma isolada: o prejuízo está nos itens 1, 3 e 5, não na pasta. O efeito é de processo. Não dá para aferir C1–C3 sem subir servidor e banco, então o gate que pegaria a taxa `1.0` não tem onde morar. O próximo tipo de recebível será mais um `if` neste arquivo.

**Correção proposta.** Motor puro (face, prazo, tipo, taxa base, câmbio opcional) → resultado decimal, testável sem I/O. Strategy por tipo só informa o spread. A aplicação abre a transação, resolve vigências e grava. O controller valida, chama o caso de uso e mapeia status. Relatório pode ler SQL direto; liquidação, não.

## Prevenção sistêmica

O que teria impedido esta classe de merge, sem comitê novo:

1. **Gate de centavo.** `PricingEngineGoldenCasesTest` (C1, C2, C3, empate half-even, ordem do câmbio em `5.0037`) roda no CI e bloqueia merge. O item 1 fica vermelho antes da review humana.
2. **Gate de dinheiro binário.** Teste de arquitetura falha se o módulo de precificação referencia `Double`, `Float` ou `Math.pow`.
3. **SQL só parametrizado.** Lint recusa interpolação em string de query. Review de liquidação olha bind, não “parece um número”.
4. **Teste de retry e de corrida.** O mesmo `Idempotency-Key` duas vezes produz uma linha; duas chaves simultâneas no mesmo recebível produzem uma liquidação e um 409. Transação só no service, com rollback assertado quando a segunda escrita falha.
5. **Review de PR gerado por IA.** Diff de caixa exige o teste que caracteriza o número, o limite transacional e o status HTTP de falha. Comentário que justifica estado parcial é motivo de rejeição, não de follow-up.
