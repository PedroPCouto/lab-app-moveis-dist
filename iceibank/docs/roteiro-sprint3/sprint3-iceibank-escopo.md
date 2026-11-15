# ICEIBank - Sprint 3: Escopo da entrega

## Contexto

Até agora, as 3 agências do ICEIBank operam como pares: nenhuma tem autoridade especial sobre as outras. Neste sprint, as agências passam a **eleger uma líder entre si usando o algoritmo de Bully**. Quando uma agência percebe que não há líder (ou que a líder atual parou de responder), ela inicia uma eleição. A agência de maior identificador entre as que estão ativas sempre vence.

Ao mesmo tempo, nasce o **app Flutter**, o primeiro cliente mobile do projeto. Ele reaproveita a mesma API REST que o frontend web do Sprint 1 já consome. Além do fluxo bancário completo, o app exibe qual agência é a líder atual e atualiza isso automaticamente quando uma reeleição acontece. O consenso deixa de ser só um log no terminal e passa a aparecer na tela.

Por ser o primeiro cliente mobile do projeto, o app segue o paradigma **offline-first**:

- Continua funcionando com dados em cache.
- Permite registrar operações mesmo sem conexão com a agência responsável.
- Sincroniza tudo automaticamente assim que a conectividade volta.

Isso reaproveita diretamente o **relógio vetorial do Sprint 2**. Ele não serve mais para ordenar eventos entre agências, e sim para a agência detectar se uma operação feita offline pelo app ainda é segura de aplicar, ou se o estado da conta mudou enquanto o app estava desconectado.

## O que este sprint entrega

1. **Algoritmo de Bully** implementado nas 3 agências: mensagens de eleição e de anúncio de coordenador.
2. **Heartbeat periódico**: cada agência verifica se a líder atual continua ativa e dispara uma nova eleição automaticamente se não estiver.
3. **Script de histórico de lideranças**: reconstrói o histórico de lideranças a partir dos logs das 3 agências.
4. **App Flutter com fluxo bancário completo**: login, saldo, depósito, saque e transferência, mais um painel mostrando a líder atual em tempo real.
5. **Endpoint de sincronização no backend**: usa o relógio vetorial para detectar conflitos entre operações feitas offline e mudanças ocorridas enquanto o app estava desconectado.
6. **App Flutter offline-first**: armazenamento local, fila de operações pendentes e sincronização automática ao reconectar.

## O que este sprint deliberadamente NÃO resolve

- **A líder eleita é apenas informativa.** Nenhuma operação do banco passa a ser coordenada por ela. Isso é intencional: é exatamente o papel que a líder assumirá no Sprint 4, coordenando transações distribuídas (2PC) entre agências.
- **A detecção de conflito offline-first é propositalmente simples.** Ela sinaliza que algo mudou na agência enquanto o app estava offline, mas não tenta resolver o conflito sozinha. Cabe ao app (ou à pessoa usuária) decidir o que fazer com uma sincronização rejeitada.

## 2.1 Funcionalidade adicional (obrigatória)

Mesma regra dos sprints anteriores (detalhes no roteiro do Sprint 1, seção 2.1).

- Documentar em `RESPOSTAS.md`.
- Evidenciar com print em `evidencias/sprint3/`.
- Fazer um commit próprio.

Exemplos possíveis para este sprint:

- **Histórico de líderes na tela:** além da líder atual, o app exibe as últimas trocas de liderança.
- **Indicador de agências ativas:** o app mostra quais das 3 agências estão respondendo no momento, não só quem é a líder.
- **Eleição sob demanda:** um botão (no app ou um endpoint) para forçar uma nova eleição manualmente, útil para demonstração em sala.
- **Timeout configurável:** tornar o intervalo do heartbeat e o timeout de resposta configuráveis por variável de ambiente, em vez de fixos no código.

## 2.2 Linguagens

- **Backend:** continua na mesma linguagem escolhida no Sprint 1 (Java ou Python), sem mudança. O código de exemplo do roteiro original está em Node.js/Express, apenas como referência ilustrativa dos materiais da disciplina (a seção 9 do roteiro traz orientações para adaptar às duas linguagens de entrega).
- **App mobile:** obrigatoriamente em **Flutter**. É a tecnologia da Unidade 4 e não é opcional. Não há código de exemplo do app Flutter no roteiro (a seção 8 traz os requisitos, não a implementação), a mesma política já usada para o frontend web e a autenticação JWT no Sprint 1.
