# Respostas

Documento para respostas das questões descritas no documento README.md

## 4.1 Revisão laboratório anterior

### 1 O endereço do servidor (localhost, IP, grupo multicast) está escrito diretamente no código do cliente? Isso favorece ou prejudica a transparência de localização?

 Sim, para todos os citados (TCP, UDP, Multicast e Websocket) isso é o caso. O cliente está se conectando a um cliente específico e, para tal, ele precisa descrever o alvo dessa conexão. Podemos dizer que isso tem vantagens e desvantagens. Do ponto de vista do desenvolvedor, essa clareza pode ser muito bem vinda, especialmente em infraestruturas mais frágeis. Porém, a de se reconhecer que, se essa informações pudesse ser totalmente abstraida, a simplicidade do processo seria uma grande vantagem. No final das contas, a transparência de localização está completamente ferida.

 ### 2 Para “perguntar uma coisa” ao servidor, o cliente precisa montar uma string de texto manualmente (e o servidor precisa interpretá-la/fazer parsing)? Isso é meio-termo, presença ou ausência de transparência de acesso?
 
 Sim, ambos os cliente e o servidor precisam de realizar tarefas com a string que é o alvo da conexão.
