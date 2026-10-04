package br.com.pucminas.iceibank;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "iceibank.agencia-id=0",
        "iceibank.pasta-dados=${java.io.tmpdir}/iceibank-testes",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class ApiIntegracaoTest {
    @Autowired
    private MockMvc mockMvc;

    private String autenticar(String usuario, String senha) throws Exception {
        String corpo = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"%s\",\"senha\":\"%s\"}".formatted(usuario, senha)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$.token");
    }

    @Test
    void semTokenRetorna401() throws Exception {
        mockMvc.perform(get("/contas/0"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    void tokenInvalidoRetorna401() throws Exception {
        mockMvc.perform(get("/contas/0").header(HttpHeaders.AUTHORIZATION, "Bearer nao-e-um-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void statusEPublico() throws Exception {
        mockMvc.perform(get("/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idAgencia").value(0))
                .andExpect(jsonPath("$.numeroAgencias").value(3));
    }

    @Test
    void loginComSenhaErradaRetorna401() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"ana\",\"senha\":\"errada\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fluxoCompletoComTokenValido() throws Exception {
        String token = autenticar("ana", "ana123");

        mockMvc.perform(post("/contas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":9,\"nomeAluno\":\"Ana\",\"saldoInicial\":100}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saldo").value(100.00));

        mockMvc.perform(post("/contas/9/depositar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(150.00));

        mockMvc.perform(get("/contas/9").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dono").value("ana"));
    }

    @Test
    void contaDeOutraParticaoERecusada() throws Exception {
        String token = autenticar("ana", "ana123");

        mockMvc.perform(post("/contas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":7,\"nomeAluno\":\"Ana\",\"saldoInicial\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(
                        org.hamcrest.Matchers.containsString("nao pertence a esta agencia")));
    }

    @Test
    void rotaDeCreditoRemotoDoSprint1DeixouDeExistir() throws Exception {
        String token = autenticar("ana", "ana123");

        mockMvc.perform(post("/contas/0/creditar-remoto")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":10,\"vetorEnvio\":[0,5,0],\"origemAgencia\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    void rotaInexistenteSemTokenTambemRetorna401() throws Exception {
        mockMvc.perform(post("/contas/0/creditar-remoto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":10,\"vetorEnvio\":[0,5,0],\"origemAgencia\":1}"))
                .andExpect(status().isUnauthorized());
    }
}
