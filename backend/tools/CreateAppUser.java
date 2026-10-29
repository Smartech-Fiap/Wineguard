import java.io.Console;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Arrays;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class CreateAppUser {

    public static void main(String[] args) throws Exception {
        Console console = System.console();

        if (console == null) {
            throw new IllegalStateException(
                "Execute diretamente em um terminal interativo.");
        }

        String username = console.readLine("Nome do usuário: ").trim();

        if (!username.matches("[A-Za-z0-9._-]{3,100}")) {
            console.printf(
                "Use de 3 a 100 letras, números, pontos, hífens ou sublinhados.%n");
            return;
        }

        char[] password = console.readPassword("Senha: ");
        char[] confirmation = console.readPassword("Confirme a senha: ");

        String hash;
        try {
            if (!Arrays.equals(password, confirmation)) {
                console.printf("As senhas não coincidem.%n");
                return;
            }

            String rawPassword = new String(password);

            if (password.length < 12 ||
                    rawPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
                console.printf(
                    "Use pelo menos 12 caracteres e no máximo 72 bytes UTF-8.%n");
                return;
            }

            hash = "{bcrypt}" +
                new BCryptPasswordEncoder(12).encode(rawPassword);
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');
        }

        String sql = """
            INSERT INTO app_users (username, password_hash, enabled)
            VALUES (?, ?, TRUE)
            """;

        try (Connection connection = DriverManager.getConnection(
                System.getenv("SPRING_DATASOURCE_URL"),
                System.getenv("SPRING_DATASOURCE_USERNAME"),
                System.getenv("SPRING_DATASOURCE_PASSWORD"));
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, username);
            statement.setString(2, hash);
            statement.executeUpdate();
            console.printf("Usuário criado com sucesso.%n");

        } catch (SQLException exception) {
            if ("23505".equals(exception.getSQLState())) {
                console.printf("Esse nome de usuário já existe.%n");
            } else {
                throw new IllegalStateException(
                    "Falha ao cadastrar. SQLState: " + exception.getSQLState());
            }
        }
    }
}
