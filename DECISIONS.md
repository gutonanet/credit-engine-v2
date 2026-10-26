# Decisões de escopo

O perfil staff do desafio permite reduzir a implementação e registrar o corte aqui. As duas decisões abaixo concentram o esforço no que o avaliador precisa ver funcionando: precisão financeira, liquidação idempotente, concorrência e operação local, dentro das 8–16 horas previstas.

## SonarQube fica de fora

A qualidade deste case se prova nos golden cases, na transação da liquidação e no teste de duas liquidações simultâneas do mesmo recebível. Subir um servidor de análise estática, com quality gate e pipeline próprio, consome uma parte relevante desse prazo sem alterar o que o negócio mede: o centavo, o status HTTP e uma única linha de liquidação por recebível.

## As APIs não têm autenticação

Nenhuma rota exige identidade. Não há login, token, API key nem Spring Security. Quem alcança a máquina local chama cotação, cadastro de recebível, liquidação e extrato sem credencial.

Isso é deliberado. O [`SPEC.md`](SPEC.md) deixa a pergunta 8 em aberto — quem autentica a mesa, e quando a API sai do computador local — e restringe esta entrega a esse uso. A validação do corpo, a `Idempotency-Key` e o recebível que só liquida uma vez protegem a operação financeira. Eles não identificam quem chamou a API. Essa camada entra depois da resposta do negócio.
