# ICEIBank - Sprint 3, Parte D: App Flutter

## Conceito

O app mobile consome a mesma API que o frontend web do Sprint 1 já usa, com um adicional: um **painel mostrando qual agência é a líder atual**, atualizado automaticamente conforme o consenso muda.

Esta parte não vem com código de exemplo. Assim como o frontend web e a autenticação JWT no Sprint 1, a implementação é 100% do aluno, inclusive as decisões de design (estrutura de telas, gerenciamento de estado, como fazer o polling da liderança).

## 8.1 Requisitos obrigatórios

### Fluxo bancário (reaproveitando a lógica do frontend web do Sprint 1)

- Tela de login, autenticando via JWT e guardando o token para as próximas requisições.
- Consulta de saldo.
- Depósito e saque, via formulário.
- Transferência, local e entre agências.
- Tratamento visível de erros retornados pela API (saldo insuficiente, conta não encontrada, 401 por token expirado etc.).

### Painel de liderança (a parte nova deste sprint)

- Um painel (pode ser parte da tela principal, ou uma tela própria) mostrando qual agência é a líder atual, consultando `GET /bully/lider` em alguma agência conhecida.
- O app deve consultar periodicamente (ex.: a cada poucos segundos) para detectar trocas de liderança, sem exigir que a pessoa usuária atualize a tela manualmente.
- Se a agência consultada não responder (está fora do ar), o app deve tentar outra das 3 agências conhecidas antes de desistir.

## 8.2 Tarefa

1. Implementar o fluxo bancário completo no app.
2. Implementar o painel de liderança, com atualização periódica.
3. Com o app rodando e as 3 agências no ar, confirmar que o painel mostra a líder correta. Capturar um print para `evidencias/sprint3/app-lider-atual.png`.
4. Com o app ainda rodando, derrubar a agência líder (como na Parte B) e observar o painel do app. Sem reiniciar o app nem tocar em nada na tela, confirmar que o painel atualiza sozinho para a nova líder dentro de alguns segundos. Capturar um print dessa transição (ou duas capturas, antes e depois) para `evidencias/sprint3/app-failover.png`.
5. Fazer o commit:

   ```powershell
   git add app_mobile evidencias/sprint3
   git commit -m "feat(mobile): implementa app Flutter com painel de lideranca"
   ```

## 8.3 Perguntas - Parte D

Responder em `RESPOSTAS.md`:

1. Como o app decide com que frequência consultar `/bully/lider`? Que trade-off você considerou ao escolher esse intervalo (resposta rápida a mudanças vs. consumo de bateria/rede do celular)?
2. Se, num momento de transição, você consultasse duas agências diferentes e elas respondessem líderes diferentes (uma ainda não sabe da nova eleição), como seu app se comporta? Isso é um problema real, ou se resolve sozinho na próxima consulta?
3. Compare a arquitetura do app Flutter com a do frontend web do Sprint 1: o que foi reaproveitável (conceitos, não necessariamente código) e o que precisou ser pensado de forma diferente por ser uma plataforma mobile?
