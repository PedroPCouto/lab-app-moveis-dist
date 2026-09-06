package br.com.pucminas.iceibank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.pucminas.iceibank.config.AgenciaConfig;
import br.com.pucminas.iceibank.config.IceibankProperties;
import br.com.pucminas.iceibank.exception.ApiException;
import br.com.pucminas.iceibank.model.TipoToken;
import br.com.pucminas.iceibank.model.Usuario;
import br.com.pucminas.iceibank.security.JwtService;
import br.com.pucminas.iceibank.security.UsuarioService;

class JwtServiceTest {
    private static final Usuario ANA = new Usuario("ana", "Ana", UsuarioService.hash("ana123"));

    private JwtService servico(Duration expiracao) {
        var propriedades = new IceibankProperties(0, 0, "data",
                new IceibankProperties.Jwt(IceibankProperties.SEGREDO_PADRAO, expiracao, Duration.ofSeconds(60)),
                List.of());
        return new JwtService(propriedades, new AgenciaConfig(propriedades));
    }

    @Test
    void tokenValidoCarregaOSujeitoEOTipoCliente() {
        var autenticacao = servico(Duration.ofMinutes(30))
                .validar(servico(Duration.ofMinutes(30)).gerarTokenCliente(ANA));

        assertThat(autenticacao.sujeito()).isEqualTo("ana");
        assertThat(autenticacao.tipo()).isEqualTo(TipoToken.CLIENTE);
        assertThat(autenticacao.ehCliente()).isTrue();
    }

    @Test
    void tokenInternoVemMarcadoComoSistema() {
        JwtService jwt = servico(Duration.ofMinutes(30));

        assertThat(jwt.validar(jwt.gerarTokenInterno()).ehSistema()).isTrue();
    }

    @Test
    void tokenExpiradoERejeitado() {
        JwtService jwt = servico(Duration.ofSeconds(-1));

        assertThatThrownBy(() -> jwt.validar(jwt.gerarTokenCliente(ANA)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("expirado");
    }

    @Test
    void tokenAdulteradoERejeitado() {
        JwtService jwt = servico(Duration.ofMinutes(30));
        String token = jwt.gerarTokenCliente(ANA);

        assertThatThrownBy(() -> jwt.validar(token + "abc"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("invalido");
    }

    @Test
    void tokenAssinadoComOutraChaveERejeitado() {
        var outrasPropriedades = new IceibankProperties(0, 0, "data",
                new IceibankProperties.Jwt("uma-chave-completamente-diferente-com-32-bytes",
                        Duration.ofMinutes(30), Duration.ofSeconds(60)),
                List.of());
        String tokenIntruso = new JwtService(outrasPropriedades, new AgenciaConfig(outrasPropriedades))
                .gerarTokenCliente(ANA);

        assertThatThrownBy(() -> servico(Duration.ofMinutes(30)).validar(tokenIntruso))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("invalido");
    }

    @Test
    void senhaErradaNaoAutentica() {
        var propriedades = new IceibankProperties(0, 0, "data", null,
                List.of(new IceibankProperties.UsuarioProps("ana", "Ana", UsuarioService.hash("ana123"))));
        UsuarioService usuarios = new UsuarioService(propriedades);

        assertThat(usuarios.autenticar("ana", "ana123")).isPresent();
        assertThat(usuarios.autenticar("ana", "errada")).isEmpty();
        assertThat(usuarios.autenticar("ninguem", "ana123")).isEmpty();
    }
}
