# Instruções para agentes de IA (e pessoas) que continuarem este projeto

ICEIBank: projeto da disciplina Laboratório de Desenvolvimento de Aplicações Móveis e
Distribuídas (PUC Minas), 4 sprints. Backend em **Java 25 + Spring Boot 4.1** (`agencia/`),
frontend web sem build (`frontend/`), app **Flutter** (`app_mobile/`, Sprint 3).

Leia, nesta ordem:

1. [`docs/SPRINT3-ESTADO.md`](docs/SPRINT3-ESTADO.md): o que já foi feito, decisões e
   limitações do ambiente (JDK/Flutter portáteis, sem emulador, sem RabbitMQ). **Mantenha-o
   atualizado** a cada etapa: é a memória do projeto entre sessões.
2. [`README.md`](README.md): arquitetura e como executar.
3. [`RESPOSTAS.md`](RESPOSTAS.md): respostas às perguntas dos roteiros (citar sempre o
   comportamento observado nas execuções reais, com data/hora das evidências).
4. Roteiros: `Roteiro_Projeto_Sprint2_ICEIBank.md` e `docs/roteiro-sprint3/`.

Convenções:

- Código, comentários e mensagens em português, sem acentos no código Java (como o existente).
- Commits pequenos, no formato `tipo(escopo): descricao` dos roteiros.
- Evidências em `evidencias/sprintN/`: prints reais com data/hora visível; as saídas em texto
  ficam em `evidencias/sprintN/saidas/`.
- A `RABBITMQ_URL` contém senha: nunca grave em arquivo versionado nem passe em linha de comando.
- `agencia/data/` (logs) é gerado em tempo de execução e fica fora do Git.
