import grpc

import central_pb2
import central_pb2_grpc

# TODO: substitua pelo seu OFFSET pessoal - use o MESMO valor do servidor
OFFSET = 0
PORTA = 50061 + OFFSET


def main():
    canal = grpc.insecure_channel(f"localhost:{PORTA}")
    stub = central_pb2_grpc.CentralAtendimentoStub(canal)

    nome = input("Digite seu nome: ")

    # Chamada unária: parece uma chamada de função local, mas atravessa a rede
    resposta = stub.ConsultarHorario(central_pb2.PerguntaHorario(nome_aluno=nome))
    print(f"[gRPC] {resposta.mensagem}")


if __name__ == "__main__":
    main()
