package br.pucminas.labdamd.central;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ServidorCentral {
    // TODO: substitua pelo seu OFFSET pessoal (ver seção 3.3)
    static final int OFFSET = 0;

    public static void main(String[] args) throws IOException, InterruptedException {
        int porta = 50051 + OFFSET;

        Server servidor = ServerBuilder.forPort(porta)
                .addService(new CentralAtendimentoImpl())
                .build();

        servidor.start();
        System.out.println("[gRPC] Servidor da Central ouvindo na porta " + porta);
        servidor.awaitTermination();
    }

    static class CentralAtendimentoImpl extends CentralAtendimentoGrpc.CentralAtendimentoImplBase {

        @Override
        public void consultarHorario(PerguntaHorario pedido, StreamObserver<RespostaHorario> observador) {
            String horario = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            System.out.println("[gRPC] ConsultarHorario chamado por: " + pedido.getNomeAluno());

            RespostaHorario resposta = RespostaHorario.newBuilder()
                    .setHorarioAtual(horario)
                    .setMensagem("Olá, " + pedido.getNomeAluno() + "! Agora são " + horario + ".")
                    .build();

            observador.onNext(resposta);
            observador.onCompleted();
        }
    }
}