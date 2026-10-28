# Rodar o WineGuard localmente

## Pré-requisitos

- JDK 17 configurado no IntelliJ IDEA.
- Docker Desktop aberto.
- Maven importado pelo `pom.xml` no IntelliJ (ou use `./mvnw` no terminal).
- Plugin Lombok e annotation processing habilitados no IntelliJ.

## 1. Subir o PostgreSQL

No terminal do IntelliJ, aberto na pasta que contém `docker-compose.yml`:

```bash
docker compose up -d postgres
```

O contêiner publica o PostgreSQL em `localhost:5432`. O volume `wineguard-postgres-data` preserva os dados entre reinicializações. Para parar o banco sem apagar os dados, use `docker compose stop postgres`.

## 2. Executar pela classe principal no IntelliJ

1. Abra `src/main/java/br/com/vinhariaagnello/wineguard/WineguardBackendApplication.java`.
2. Clique no triângulo verde ao lado de `main` e escolha **Run 'WineguardBackendApplication'**. O IntelliJ cria uma configuração local automaticamente; ela não precisa ser salva como arquivo compartilhado `.run`.
3. A primeira execução compila e baixa dependências Maven. Flyway aplica os arquivos `src/main/resources/db/migration` e cria as tabelas.

Se não houver triângulo verde, no painel **Maven** use **Add Maven Project** ou **Reload All Maven Projects** e aguarde a sincronização. Confira **Project SDK: 17** e annotation processing do Lombok. Se a IDE mantiver erros falsos em `String` ou nos imports Spring, use **Repair IDE → Rescan Project Indexes**. Como alternativa dentro da IDE, use **Maven → Execute Maven Goal** e digite `spring-boot:run`; no terminal integrado, `./mvnw spring-boot:run`. Se o projeto estiver em Safe Mode, confie na pasta do projeto.

Sem IntelliJ:

```bash
./mvnw spring-boot:run
```

No Windows, use `mvnw.cmd spring-boot:run`.

## 3. Conferir API e conectar o Banco De Dados

- Saúde: `http://localhost:8080/actuator/health`
- Base dos ambientes: `http://localhost:8080/api/environments`
- Base dos dispositivos: `http://localhost:8080/api/devices`

Na IDE De sua preferência, escolha **New Connection → PostgreSQL** e informe:

| Campo | Valor local |
|---|---|
| Host | `localhost` |
| Port | `5432` |
| User | `wineguard` |
| Password | `wineguard-local` |
| Default Database | `wineguard` |
| Connection Name | `WineGuard PostgreSQL local` |

Conecte após o contêiner estar saudável. As tabelas `environments`, `devices` e `device_limits` aparecem depois que o backend inicia e aplica a migração Flyway. `flyway_schema_history` registra a versão aplicada. A senha é apenas o padrão de desenvolvimento deste Compose; não use esse valor em ambiente AWS.

## Endereços FIWARE

Para que os endpoints de leituras funcionem, configure `FIWARE_ORION_BASE_URL` e `FIWARE_STH_BASE_URL` com endereços acessíveis pela máquina local. As leituras atuais vêm do Orion e as séries históricas do STH-Comet. O PostgreSQL armazena configurações da aplicação, não duplica o histórico.
