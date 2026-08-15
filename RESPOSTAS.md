# Respostas - Parte A
 1. Ao tentar conectar o cliente antes de haver um servidor disponível, um erro aparecera, o motivo para isso está justamente no comportamento esperado do TCP, ele oferece uma comunicação segura, que apenas acontece quando o cliente consegue se conectar assertivamente em um servidor, caso isso não ocorra, a conexão não é estabelecida, bem como esperado.

 2. É a numeração, cada "pacote" é numerado, garantindo que mesmo que eles cheguem em ordens diferentes, dado por condições externas incontroláveis, eles ainda serão apresentados na ordem em que foram enviados.

 3. Não seria possível, a implementação atual não suporta que mais de um cliente se conecte a um único servidor.