package br.com.pucminas.iceibank.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import br.com.pucminas.iceibank.config.IceibankProperties;
import br.com.pucminas.iceibank.model.Usuario;

@Service
public class UsuarioService {
    private final Map<String, Usuario> usuarios = new LinkedHashMap<>();

    public UsuarioService(IceibankProperties propriedades) {
        propriedades.usuarios().forEach(u ->
                usuarios.put(u.usuario(), new Usuario(u.usuario(), u.nome(), u.senhaHash())));
    }

    public Optional<Usuario> autenticar(String nomeUsuario, String senha) {
        Usuario usuario = usuarios.get(nomeUsuario);
        if (usuario == null) {
            return Optional.empty();
        }
        byte[] informado = hash(senha).getBytes(StandardCharsets.UTF_8);
        byte[] guardado = usuario.senhaHash().toLowerCase().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(informado, guardado) ? Optional.of(usuario) : Optional.empty();
    }

    public Optional<Usuario> buscar(String nomeUsuario) {
        return Optional.ofNullable(usuarios.get(nomeUsuario));
    }

    public static String hash(String texto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM", e);
        }
    }
}
