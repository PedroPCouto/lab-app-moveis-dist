# Roteiro de Projeto - Sprint 3: ICEIBank

## Contexto da disciplina

- **Disciplina:** Laboratório de Desenvolvimento de Aplicações Móveis e Distribuídas
- **Unidade:** U4 - Desenvolvimento Móvel
- **Professores:** T1 - Cleiton Tavares Silva · T2 - Cristiano de Macedo Neto
- **Modalidade:** projeto individual, desenvolvido ao longo do sprint (não é uma atividade de aula única)
- **Valor:** 20 pontos (Sprint 3 de 4)

## Pré-requisitos

Os Sprints 1 e 2 devem estar concluídos. O código existente do ICEIBank já contempla:

- Particionamento
- Relógio de Lamport e relógio vetorial
- API REST / MVC
- Autenticação JWT
- Frontend web
- Mensageria via RabbitMQ

Este sprint deve ser construído sobre essa base, sem quebrar o que já existe.

## Visão geral do projeto: ICEIBank

Mapeamento completo dos 4 sprints:

| Sprint | Unidade da ementa | Tecnologia | Conceito de Sistemas Distribuídos aplicado |
|--------|-------------------|------------|--------------------------------------------|
| 1 | U2 - Desenvolvimento Web | API REST / MVC | Relógio lógico de Lamport |
| 2 | U3 - Comunicação indireta | Mensageria / Pub-Sub | Relógio vetorial |
| **3 (este)** | **U4 - Desenvolvimento Móvel** | **App Flutter** | **Consenso (eleição de líder)** |
| 4 | U5 - Computação em Nuvem | Containers | Transações distribuídas (2PC/Saga) |

## Foco do Sprint 3

- Desenvolver o aplicativo móvel do ICEIBank em **Flutter**.
- Aplicar o conceito de **consenso por eleição de líder** entre as agências do sistema distribuído.
- Validar o comportamento com as 3 agências reais: derrubar a líder e observar a reeleição automática.
