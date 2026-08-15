# Respostas — Parte A (TCP)

**1. O que acontece se você iniciar o cliente antes do servidor?**

O cliente falha imediatamente com um erro de conexão recusada (`java.net.ConnectException: Connection refused` em Java, `ConnectionRefusedError` em Python), já na chamada de `new Socket(host, porta)` / `cliente.connect(...)`. Isso ocorre porque o TCP é orientado a conexão: antes de qualquer byte de dados trafegar, é preciso concluir o *handshake* de três vias (SYN → SYN-ACK → ACK). Sem um servidor com `ServerSocket` escutando naquela porta, não existe ninguém para responder ao SYN — o sistema operacional do destino devolve um pacote RST, e a tentativa de conexão é abortada na origem. Ou seja: o erro não é do meu código, é o próprio protocolo se recusando a criar um canal que não tem os dois lados.

**2. Qual mecanismo garante a ordem das mensagens?**

O **número de sequência** presente no cabeçalho de cada segmento TCP. Cada byte enviado recebe uma numeração contínua, e o receptor usa esses números para remontar o fluxo: se os segmentos chegarem fora de ordem (por terem seguido rotas diferentes na rede, por exemplo), o receptor os armazena em um buffer de reordenação e só entrega os dados à aplicação na sequência correta. Esse mecanismo trabalha junto com as confirmações (ACK) e a retransmissão por timeout — o ACK informa ao remetente até qual número de sequência os dados chegaram; o que não for confirmado é reenviado. É por isso que, no cliente, o `entrada.readLine()` sempre lê as respostas na mesma ordem em que o servidor as escreveu.

**3. O que aconteceria se dois clientes tentassem se conectar ao mesmo tempo?**

O código atual **não** atende dois clientes. Em `java/tcp/ServidorTCP.java:9`, o `servidor.accept()` é chamado **uma única vez**, fora de qualquer laço, e o `try-with-resources` que o envolve abre os streams desse único socket. Todo o `while` de troca de mensagens (linhas 16–23) roda dentro desse bloco, atendendo apenas aquele cliente.

O que acontece na prática com um segundo cliente: o `connect` dele **não** dá erro de imediato — o handshake é concluído pelo sistema operacional e a conexão fica parada na *fila de espera* (backlog) do `ServerSocket`. Como o servidor nunca chama `accept()` de novo, essa conexão jamais é atendida: o segundo cliente envia sua mensagem e fica travado no `readLine()` esperando uma resposta que não vem. Pior: quando o primeiro cliente digita `sair`, o `break` encerra o laço, o `try` externo fecha o `ServerSocket` e o programa imprime "Servidor encerrado" — derrubando também a conexão pendente. O mesmo vale para `python/tcp/servidor_tcp.py`, que também faz um único `accept()` (o `listen(1)` inclusive limita a fila).

Para suportar múltiplos clientes seria necessário colocar o `accept()` dentro de um laço infinito e delegar cada socket aceito a uma *thread* separada (`new Thread(...)` em Java, `threading.Thread` ou `asyncio` em Python), de modo que o laço principal volte a aceitar novas conexões enquanto as anteriores continuam ativas.

---

# Respostas — Parte B (UDP)

**1. O que aconteceu ao enviar uma mensagem com o servidor desligado?**

O `socket.send(pacote)` (`java/udp/ClienteUDP.java:21`) executou **normalmente, sem erro nenhum** — o datagrama saiu da máquina e simplesmente se perdeu, porque não havia processo escutando na porta 5001. O cliente só ficou "travado" logo depois, na linha seguinte, no `socket.receive(resposta)` (linha 25): essa chamada é bloqueante e ficou esperando indefinidamente uma resposta que nunca chegaria. Isso **não é um erro de implementação**, é exatamente o comportamento esperado de um protocolo sem conexão — o cliente não tem como saber que o servidor está fora do ar; ele só sabe esperar. (Dependendo do sistema operacional, o envio para `localhost` pode gerar um ICMP *port unreachable* de volta, e aí a versão Python pode levantar `ConnectionResetError` no `recvfrom` — mas isso é uma cortesia da pilha local, não uma garantia do UDP.)

A diferença para o TCP é justamente essa: lá, a falha aparece **antes** de qualquer dado ser enviado, no `connect`, porque existe uma etapa de estabelecimento de conexão que precisa ser confirmada pelos dois lados. No UDP não há nada a estabelecer — cada datagrama é autônomo, o remetente "grita" o endereço de destino e segue em frente, sem confirmação, sem estado e sem aviso de fracasso.

**2. Dois exemplos reais de uso de UDP.**

- **Streaming de vídeo/áudio ao vivo e videoconferência** (Teams, Zoom, transmissões via CDN): retransmitir um pacote de áudio perdido é inútil, porque quando a retransmissão chegasse aquele trecho já teria passado. Aqui a latência baixa vale mais que a integridade: perder alguns pacotes causa uma queda momentânea de qualidade, enquanto o controle de congestionamento e a retransmissão do TCP causariam travamentos e atraso acumulado — o remédio seria pior que a doença.
- **DNS**: uma consulta de resolução de nome cabe em um único datagrama e a resposta também. Abrir uma conexão TCP (três pacotes só de handshake, mais o encerramento) para trocar dois pacotes de dados multiplicaria o custo e a latência de uma operação que acontece dezenas de vezes por página carregada. Se a resposta não vier, o cliente simplesmente repete a pergunta — a confiabilidade é resolvida na aplicação, de forma mais barata.

**3. O servidor UDP não registra "quem está conectado". Seria possível implementar?**

Sim, é possível — mas nada disso existe hoje no código, e é justamente aí que está o ponto: no `ServidorUDP.java` o servidor só olha o `pacoteRecebido.getAddress()` e o `getPort()` para saber para onde devolver **aquela** resposta (linhas 19–21), e descarta essa informação logo em seguida. Não há memória de um datagrama para o outro.

Para manter uma lista de participantes, a responsabilidade teria de subir para a **camada de aplicação**, já que o protocolo de transporte não oferece isso. Na prática mudaria a arquitetura assim:

- manter no servidor uma estrutura (ex.: `Map<String, Instant>`) indexada pelo par **IP + porta** de origem de cada cliente;
- definir um pequeno protocolo de mensagens próprio, com comandos de entrada e saída (ex.: `ENTRAR`, `SAIR`), já que não existe evento de "conectou"/"desconectou";
- como também não existe evento de queda, exigir mensagens periódicas de *keep-alive* (heartbeat) e remover da lista quem ficar em silêncio além de um tempo limite — caso contrário clientes que fecharam o terminal ficariam registrados para sempre;
- tratar duplicações e reordenações por conta própria, se a lógica de sessão depender disso.

Em outras palavras: dá para simular "conexão" sobre UDP, mas todo o estado que o TCP entrega pronto (sessão, detecção de queda, ordem) passa a ser código meu.

---

# Respostas — Parte C (Multicast)

**1. Diferença entre unicast repetido 3 vezes e um único envio multicast.**

No unicast, o remetente precisa **conhecer cada destinatário** e gerar uma cópia do pacote por cliente: 3 clientes = 3 datagramas saindo da máquina de origem, e o mesmo conteúdo atravessa o enlace do remetente três vezes (com 100 clientes, 100 vezes — o gargalo cresce linearmente na origem).

No multicast, o servidor faz **um único `send`** para o endereço de grupo `230.0.0.1:4446` e não sabe quem — nem quantos — vai receber. A replicação é responsabilidade da **infraestrutura de rede**: switches e roteadores duplicam o pacote apenas nos ramos onde há membros inscritos naquele grupo. O tráfego na origem é constante independentemente do número de receptores, e nenhum enlace carrega a mesma mensagem duas vezes desnecessariamente. Vale corrigir um detalhe conceitual: os clientes **não se inscrevem no servidor** — eles se inscrevem no *grupo* (`socket.joinGroup(...)` em Java, `IP_ADD_MEMBERSHIP` em Python), e servidor e clientes nunca ficam sabendo uns dos outros.

**2. O que é o TTL e por que ele importa?**

O TTL (*time-to-live*) é um campo do cabeçalho do pacote IP que funciona como um contador de **saltos** (hops): cada roteador que encaminha o pacote decrementa o valor em 1 e, ao chegar a zero, descarta o pacote em vez de repassá-lo. Sua função original é evitar que pacotes circulem eternamente em caso de loop de roteamento.

Em multicast, porém, o TTL ganha um papel adicional e central: ele delimita o **alcance (escopo)** da transmissão. Como o pacote não tem um destinatário único, sem esse limite um aviso poderia se espalhar muito além do pretendido. O valor padrão é 1, o que confina o tráfego à rede local — nem sai do roteador. Em `python/multicast/servidor_multicast.py:12` o valor é elevado para 2 (`IP_MULTICAST_TTL`), permitindo atravessar um roteador. É assim que se escolhe se o "aviso da turma" fica na sala, no prédio ou vaza para a internet.

**3. Um cliente que ficou offline recebe os avisos perdidos ao voltar?**

Não. O multicast é construído sobre UDP e, portanto, herda o modelo de datagrama efêmero: o servidor envia uma vez, sem manter buffer, sem saber quem recebeu e sem qualquer confirmação. As mensagens enviadas enquanto o cliente estava fora simplesmente não foram replicadas para ele, e não existe nenhum ponto na arquitetura onde elas pudessem estar armazenadas — o servidor do meu código (`ServidorMulticast.java`) nem sequer guarda o que já enviou, apenas incrementa o contador e dorme 2 segundos. Ao voltar e refazer o `joinGroup`, o cliente passa a receber apenas o que for enviado **daquele momento em diante**. Recuperar histórico exigiria uma camada de persistência e reenvio na aplicação (ou um protocolo de multicast confiável), o que é exatamente o tipo de garantia que a comunicação em grupo abre mão em troca de escalabilidade.

---

# Respostas — Parte D (WebSocket)

**1. O que muda na conexão depois do handshake `Upgrade: websocket`?**

A conexão TCP é **a mesma** — nenhum socket novo é aberto. O que muda é o protocolo falado sobre ela. Antes do handshake, o cliente faz uma requisição HTTP comum (`GET` com os cabeçalhos `Upgrade: websocket`, `Connection: Upgrade` e `Sec-WebSocket-Key`); quando o servidor responde `101 Switching Protocols`, aquele canal deixa de seguir o modelo **requisição/resposta** do HTTP e passa a ser um canal **full-duplex, persistente e orientado a mensagens**:

- os dois lados podem enviar a qualquer momento, sem esperar a vez — o servidor pode **empurrar** dados sem que o cliente tenha pedido (é o que faz o `conexao.send(...)` no `onOpen` do `MuralServidor`, saudando o aluno assim que ele entra);
- os dados passam a trafegar em **frames** binários com um cabeçalho mínimo (2 a 14 bytes), em vez dos cabeçalhos HTTP completos reenviados a cada requisição — o overhead por mensagem despenca;
- a conexão permanece aberta até que um dos lados envie um frame de fechamento (`sendClose`), com frames de controle *ping/pong* mantendo-a viva.

**2. Comparação: mural via WebSocket × aviso via Multicast.**

A diferença está em **quem sabe da existência de quem** e em **onde ocorre a replicação**:

- **Multicast:** a entrega é resolvida na **camada de rede**. O emissor envia uma única vez para um endereço de grupo e nunca descobre os destinatários — a associação é anônima e o interesse parte do receptor, que se inscreve no grupo. É a própria rede que duplica o pacote. Não há conexão, não há garantia e não há como responder individualmente a alguém.
- **WebSocket:** a entrega é resolvida na **camada de aplicação**. O servidor conhece cada cliente individualmente, porque mantém uma conexão TCP dedicada e um registro explícito delas — `getConnections()` no `MuralServidor.java` e o `set clientes_conectados` no `mural_servidor.py`. O "broadcast" é, na verdade, um **laço que envia N cópias**, uma por conexão: o custo cresce com o número de alunos, mas em troca a entrega é confiável e ordenada, o servidor sabe quando alguém entra ou sai (`onOpen`/`onClose`) e pode tratar cada cliente de forma diferente.

Resumindo: multicast escala melhor e não conhece ninguém; WebSocket conhece todo mundo e por isso consegue ser confiável e bidirecional.

**3. Por que WebSocket é mais adequado que TCP "cru" neste cenário?**

Ambos são conexões TCP contínuas, mas o TCP entrega um **fluxo de bytes sem fronteiras**, enquanto o WebSocket entrega **mensagens delimitadas**. Sobre TCP cru, eu teria de inventar meu próprio enquadramento para saber onde uma mensagem termina — na Parte A isso é feito de forma improvisada com o `\n` do `readLine()`/`println()`, o que quebraria assim que um aviso contivesse uma quebra de linha. O WebSocket já resolve isso no protocolo: cada frame carrega seu tamanho, e o `onMessage` recebe a mensagem inteira e íntegra, sem código meu para remontá-la.

Além disso, para um mural em tempo real ele traz de graça o que eu teria de construir à mão: *handshake* sobre HTTP (o que permite reaproveitar portas 80/443, proxies, TLS e a infraestrutura web existente, atravessando firewalls que bloqueariam uma porta arbitrária como a 5000), suporte nativo em navegadores via `new WebSocket(...)` — impossível com socket TCP puro —, distinção entre texto e binário, *ping/pong* para detectar conexões mortas e um handshake de fechamento explícito. O TCP cru continua sendo a base, mas o WebSocket é a camada que transforma "bytes que chegam" em "mensagens de um mural".
