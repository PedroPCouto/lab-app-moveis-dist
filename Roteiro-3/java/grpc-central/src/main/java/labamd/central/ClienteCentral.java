package br.pucminas.labdamd.central;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.Scanner;

public class ClienteCentral {
    // TODO: substitua pelo seu OFFSET pessoal - use o MESMO valor do servidor
    static final int OFFSET = 0;

    public static void main(String[] args) {
        int porta = 50051 + OFFSET;

        ManagedChannel canal = ManagedChannelBuilder.forAddress("localhost", porta)
                .usePlaintext()
                .build();

        try {
            CentralAtendimentoGrpc.CentralAtendimentoBlockingStub stub =
                    CentralAtendimentoGrpc.newBlockingStub(canal);

            Scanner teclado = new Scanner(System.in);
            System.out.print("Digite seu nome: ");
            String nome = teclado.nextLine();

            // Chamada unária: parece uma chamada de método local, mas atravessa a rede
            PerguntaHorario pergunta = PerguntaHorario.newBuilder().setNomeAluno(nome).build();
            RespostaHorario resposta = stub.consultarHorario(pergunta);
            System.out.println("[gRPC] " + resposta.getMensagem());
        } finally {
            canal.shutdown();
        }
    }
}