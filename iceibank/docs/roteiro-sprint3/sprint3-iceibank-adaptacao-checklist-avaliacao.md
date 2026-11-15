# ICEIBank - Sprint 3: Adaptação do backend, checklist, avaliação e referências

## 11. Adaptando o backend para Java ou Python

O algoritmo de Bully em si (quem vence, quando alguém se torna líder, como reagir a uma eleição recebida) já está completo e testado nas seções 5 e 6, em Node. A lógica de decisão é curta o bastante para servir de referência direta, independente da linguagem. O que muda, na prática, é a forma de fazer chamadas HTTP com timeout entre as agências.

### Java

- Usar `HttpClient` (pacote `java.net.http`, nativo desde o Java 11) com `.timeout(Duration.ofSeconds(1))` na requisição, ou `RestTemplate`/`WebClient` se já estiver usando Spring.
- Atenção à concorrência: o heartbeat roda em uma thread periódica (`ScheduledExecutorService`, por exemplo) enquanto requisições HTTP chegam em outras threads. Proteger o acesso ao estado `liderAtual` com `synchronized` ou um tipo atômico (`AtomicInteger`), do mesmo jeito que o relógio de Lamport/vetorial precisou de cuidado com concorrência no Sprint 1.

### Python

- Usar `requests` com o parâmetro `timeout=1` em cada chamada (`requests.post(..., timeout=1)`), capturando `requests.exceptions.RequestException` para tratar tanto timeout quanto conexão recusada.
- Para o heartbeat periódico, um `threading.Timer` recorrente ou um loop com `time.sleep` em uma thread separada resolve. Como em Java, proteger o `lider_atual` compartilhado com um `threading.Lock`.

### Regras comuns às duas linguagens

- A estrutura de rotas (`POST /bully/eleicao`, `POST /bully/coordenador`, `GET /bully/lider`) e o formato das mensagens (JSON com `origemId`/`liderId` e o vetor de Lamport) devem ser mantidos iguais, para que a lógica de decisão (a parte realmente nova e importante deste sprint) seja a mesma em qualquer linguagem.
- O endpoint de sincronização offline-first (seção 9) é ainda mais direto de adaptar: a função `compararVetores` é pura (sem efeitos colaterais, sem chamadas de rede) e pode ser transcrita quase literalmente para Java ou Python. É a mesma lógica já usada no `mesclar-logs.js` do Sprint 2, só que chamada em tempo real dentro de uma rota em vez de rodar sobre um arquivo de log.

## 12. Checklist de entrega

- [ ] Mensagens de eleição e coordenador implementadas e funcionando (Parte A)
- [ ] As 3 agências convergem para a mesma líder ao subir, mesmo com a "tempestade" de eleições inicial
- [ ] Heartbeat detectando a queda da líder e disparando reeleição automática (Parte B), sem intervenção manual
- [ ] Script `historico-lideranca.js` reconstruindo corretamente a linha do tempo de lideranças (Parte C)
- [ ] App Flutter com o fluxo bancário completo, reaproveitando a API já existente
- [ ] App Flutter com painel de liderança, atualizado automaticamente, incluindo o failover visível na tela, não só no backend
- [ ] Endpoint de sincronização no backend (Parte E), detectando conflito via relógio vetorial, não só o caminho feliz
- [ ] App Flutter em modo offline-first (Parte F): armazenamento local, fila de pendentes, UI otimista e sincronização automática ao reconectar
- [ ] O conflito de sincronização (HTTP 409) é tratado de forma visível no app, não escondido ou ignorado
- [ ] JWT, frontend web (Sprint 1) e mensageria/relógio vetorial (Sprint 2) continuam funcionando sem regressão
- [ ] Pelo menos uma funcionalidade adicional implementada e documentada (seção 2.1)
- [ ] Pasta `evidencias/sprint3/` com os 8 prints indicados na seção 4.2
- [ ] Arquivo `RESPOSTAS.md` atualizado com as questões das seções 5.5, 6.4, 7.3, 8.3, 9.6 e 10.3, e a descrição da funcionalidade adicional

## 13. Critérios de avaliação (20 pontos)

| Critério | Pontos | O que é observado |
|----------|--------|-------------------|
| Eleição de líder (Bully) entre as 3 agências | 4 | Mensagens de eleição/coordenador corretas; convergência garantida para o ID mais alto |
| Detecção de falha da líder + reeleição automática | 2 | Heartbeat funcionando; nova eleição disparada sem intervenção manual |
| App Flutter - fluxo bancário completo | 2 | Login, saldo, depósito, saque e transferência funcionando pela interface mobile |
| App Flutter - painel da líder + failover na UI | 2 | Líder atual exibida e atualizada automaticamente, inclusive durante uma queda real |
| Sincronização offline-first no backend | 3 | Endpoint de sincronização detectando corretamente conflitos via relógio vetorial, não só o caminho feliz |
| App Flutter - modo offline-first | 3 | Armazenamento local, fila de pendentes, UI otimista e sincronização automática funcionando de ponta a ponta |
| Continuidade (JWT, frontend web, mensageria/vetorial) | 1 | O que já funcionava nos Sprints 1-2 continua funcionando sem regressão |
| Funcionalidade adicional | 1 | Funcionalidade nova e genuína (seção 2.1), funcionando e documentada |
| Commits | 1 | Histórico incremental ao longo do sprint |
| Respostas às questões | 1 | Compreensão demonstrada, com referência ao comportamento observado no código |

A ponderação exata é definida pelo professor responsável pela turma (T1 ou T2), conforme os critérios apresentados em sala.

## 14. Referências

- GARCIA-MOLINA, Hector. *Elections in a Distributed Computing System*. IEEE Transactions on Computers, v. C-31, n. 1, 1982. (O artigo original do algoritmo de Bully.)
- COULOURIS, George et al. *Distributed Systems: Concepts and Design*. 5th ed. Addison-Wesley, 2011.
- TANENBAUM, A. S.; VAN STEEN, M. *Sistemas Distribuídos: Princípios e Paradigmas*. Tradução da 2ª edição. Pearson, 2007.
- Flutter. Documentação oficial - Get started. Disponível em: https://docs.flutter.dev/get-started/install
- Flutter. Networking e consumo de APIs REST. Disponível em: https://docs.flutter.dev/cookbook/networking/fetch-data
- sqflite (Flutter). Pacote de banco de dados local SQLite. Disponível em: https://pub.dev/packages/sqflite
- Hive (Flutter). Banco de dados local NoSQL leve. Disponível em: https://pub.dev/packages/hive
- connectivity_plus (Flutter). Detecção de conectividade de rede. Disponível em: https://pub.dev/packages/connectivity_plus
- KLEPPMANN, Martin. *Designing Data-Intensive Applications*. O'Reilly Media, 2017. (Cap. 5 - Replication - e 9 - Consistency and Consensus - para quem quiser se aprofundar em consistência eventual e resolução de conflitos além do que este sprint exige.)
