# Respostas

Documento para respostas das questões descritas no documento README.md

## 4.1 Revisão laboratório anterior

### 1 O endereço do servidor (localhost, IP, grupo multicast) está escrito diretamente no código do cliente? Isso favorece ou prejudica a transparência de localização?

 Sim, para todos os citados (TCP, UDP, Multicast e Websocket) isso é o caso. O cliente está se conectando a um cliente específico e, para tal, ele precisa descrever o alvo dessa conexão. Podemos dizer que isso tem vantagens e desvantagens. Do ponto de vista do desenvolvedor, essa clareza pode ser muito bem vinda, especialmente em infraestruturas mais frágeis. Porém, a de se reconhecer que, se essa informações pudesse ser totalmente abstraida, a simplicidade do processo seria uma grande vantagem. No final das contas, a transparência de localização está completamente ferida.

 ### 2 Para “perguntar uma coisa” ao servidor, o cliente precisa montar uma string de texto manualmente (e o servidor precisa interpretá-la/fazer parsing)? Isso é meio-termo, presença ou ausência de transparência de acesso?
 
 Sim, ambos os cliente e o servidor precisam de realizar tarefas com a string que é o alvo da conexão. Isso representa a total ausência da transparência de acesso, já que o acesso é uma parte clara do processo, uma informação que não foi abstraída.

 ### 3 O que aconteceria com o cliente se o servidor mudasse de máquina amanhã? Alguma dessas quatro soluções sobreviveria a essa mudança sem alterar o código-fonte do cliente?

 O cliente não funcionaria mais, simples assim. Para todos os casos, sem excessão. O código precisaria ser alterado para que voltasse a funcionar. Uma desvantagem dessa parte do processo ser realizada de modo totalmente explícito.

 ## 4.3 Parte A
 
 ### 1 Dentre os 8 tipos de transparência listados, qual você diria que é a mais visível para o programador que está usando um serviço remoto (e não construindo a infraestrutura por trás dele)? Justifique.

 Para um programador utlizando um serviço remoto, provavelmente a que ficaria mais clara é a ausência da transparência de acesso. Se caso eu acessasse o sistema do SGA e logo de cara visualizasse todas as inúmeras chamadas para serviços diferentes, ficaria claro o uso de multiplas máquinas no sistema.

 ### 2 Transparência total é sempre desejável? Dê um exemplo (pode ser hipotético) de uma situação em que esconder completamente que uma operação é remota atrapalharia mais do que ajudaria (dica: pense em desempenho ou em tratamento de falhas).

 Transparência total tornaria a manutenção de qualquer sistema impraticável, afinal de contas, como seria possível restaurar o desempenho de uma aplicação, que caiu por conta de uma substituição no servidor, se você não tem a menor ideia de onde ele estava ou que parte do processo ele era responsável por?

 ### 3 (Responder depois de concluir as Partes C e D) Comparando o cliente TCP do laboratório anterior com o cliente gRPC que você vai construir agora: qual dos dois exige que você “pense em rede” (sockets, send/receive, parsing de string) e qual permite que você “pense no problema” (chamar uma função e receber um resultado)? A que tipo de transparência isso se relaciona?



 ## Parte B

 ### 1 No laboratório anterior, cada um de vocês definiu o formato das mensagens de forma implícita (comentários e convenção entre quem escreveu o cliente e o servidor). Aqui, o formato está no central.proto. Qual a vantagem de ter esse contrato explícito e gerado automaticamente em vez de combinado apenas “de boca”?

 O contrato definido por meio de um arquivo permite que possamos esperar por um formato específico, pré-definido e previsível, o que reduz a probabilidade de erros em decorrência do formato.

 ### 2 O mesmo arquivo central.proto gerou código para Java e para Python. O que isso sugere sobre como equipes que usam linguagens diferentes podem se comunicar em um sistema distribuído real?

 Eles poderem usar o mesmo arquivo é justamente a finalidade, sistemas distribuidos são "agnósticos", com o objetivo de abstrair detalhes e contribuir para a transparência dos sistemas distribuidos, pois um usuário, mesmo que desenvolvedor, não seria capaz de dizer a linguagem que foi utilizada para o desenvolvimento desses servidores/clientes.

 ### 3 Observe os arquivos gerados (target/generated-sources/.../CentralAtendimentoGrpc.java ou central_pb2_grpc.py). Sem entender todo o código gerado, você consegue identificar onde ficam definidas as operações ConsultarHorario e AcompanharAvisos? Cite o nome de pelo menos uma classe ou método gerado que você reconheceu.

 
