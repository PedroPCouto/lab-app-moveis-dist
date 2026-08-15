# Respostas - Parte A
 1. Ao tentar conectar o cliente antes de haver um servidor disponível, um erro aparecera, o motivo para isso está justamente no comportamento esperado do TCP, ele oferece uma comunicação segura, que apenas acontece quando o cliente consegue se conectar assertivamente em um servidor, caso isso não ocorra, a conexão não é estabelecida, bem como esperado.

 2. É a numeração, cada "pacote" é numerado, garantindo que mesmo que eles cheguem em ordens diferentes, dado por condições externas incontroláveis, eles ainda serão apresentados na ordem em que foram enviados.

 3. Não seria possível, a implementação atual não suporta que mais de um cliente se conecte a um único servidor. Isso é demonstrado pelo trecho na linha 9 onde o aceite de um cliente está contido em um try, que realiza também validação da leitura do processo, então toda a interação nesse contexto é realizada após esse aceite.


# Respostas - Parte B

 1. Instânciar o cliente e enviar a mensagem foi possível, mas, por motivos claros, não havia resposta, pois não havia conexão. Sem a resposta do servidor, parou de responder de maneira adequada aos meus comandos, mas isso apenas em decorrência de um erro de implementação. No geral, ainda seria possível seguir enviando mensagens até que o servidor estivesse disponível.

 2. UDP tende a ser utilizado em comunicações onde a perda de pacotes não é de grande impacto, exemplos podem ser streamings de vídeos, utilizando CDNs ou aplicações de vídeo conferência, como o Teams. A natureza do UDP garante velocidade, mas não entrega, no entanto, perder alguns pacotes durante o streaming de um vídeo tende a apenas resultar na queda de qualidade, o que tende a ser melhor do que contante travamento. A relação Resposta X Qualidade (Garantia da entrega) tende a ser um critério definidor entre TCP e UDP

 3. Sim, isso é possível (Exatamente o que já está implementado). O fato é que o servidor UDP apenas aceita datagramas, mas sem garantia de conexão, considerando isso, garantir "quem" está enviando seria basicamente enviável, diante da situação onde sequer pode-se garantir conexão.

 # Respostas - Parte C

 1. Menor quantidade de mensagens trafegando e apenas um canal de comunicação. A mensagem é enviada apenas uma vez para os clientes que estão "inscritos" no servidor. Menos esforço de modo geral.

 2. TTL é uma informação enviada no cabeçalho do pacote IP e funciona como a quantidade de saltos que a mensagem pode realizar antes que ela seja descartada. É uma forma de controlar o espalhamento da mensagem pela rede.

 3. Não receberá a mensagem. Multicasting não fornece um "histórico" as mensagens são pacotes efêmeros, enviados para seus destinatários e não salvos na origem ou esperando a conexão de um determinado cliente.


 # Respostas - Parte D

 1. Após o "handshake" a conexão ganha as características de uma ligação "full-duplex", ou seja, persistente e bidirecional. Com essa confiabilidade estabelecida, as mensagens podem trafegar sem os cabeçalhos grande do HTTP tradicional, reduzindo o overhead.

 2. Multicasting funciona como um grupo de inscrição, a mensagem é enviada por uma fonte para os clientes inscritos. No websocket é um pouco diferente, nesse caso um canal de comunicação persistente é criado e que permite que mensagens trafeguem em multiplas direções pelo tempo de vida da conexão.

 3. A principal diferença está na finalidade, TCP "cru" é um protocolo na camada de transporte, seu objetivo é levar os bytes enviados com segurança de um ponto ao outro, apenas isso. Websocket, por outro lado, tem a comunicação de mensagens em "frames" como seu objetivo, rodando na camada de aplicação, o websocket mantém não apenas os bytes, mas a característica individual de cada mensagem.