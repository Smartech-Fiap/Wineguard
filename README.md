# WineGuard Backend — Vinharia Agnello

Backend Java do projeto acadêmico WineGuard. Gerencia ambientes, dispositivos, limites, usuários e configurações enviadas ao ESP. Integra o Orion, o STH-Comet e o IoT Agent UltraLight.

O PostgreSQL guarda os cadastros e o acompanhamento das configurações. As leituras atuais vêm do Orion; o histórico de medições permanece no STH-Comet.

## Arquitetura

```text
Painel React ── HTTPS / Nginx
                    ├─ /api/auth/* ────────── Java / Spring Security
                    ├─ /api/environments* ─── Java
                    ├─ /api/devices* ──────── Java
                    └─ /api/sensors/* ─────── Node.js / Express

Java ── PostgreSQL: cadastros, usuários, limites e configurações
     ├─ Orion: estado atual e envio de comandos
     ├─ STH-Comet: histórico de medições
     └─ IoT Agent: provisionamento e remoção de dispositivos

ESP32 ⇄ MQTT/TLS ⇄ Mosquitto ⇄ IoT Agent ⇄ Orion
```

Java e Express coexistem. As rotas Express de leituras/histórico têm contratos próprios; não devem ser substituídas pelas rotas Java sem adaptar o painel.

## Tecnologias

- Java 17, Maven Wrapper e Spring Boot 4.0.9.
- Spring MVC, Security, Data JPA, Validation e Actuator.
- PostgreSQL 16 e migrações Flyway.
- Lombok e Spring Boot Starter Test.
- Docker Compose para o PostgreSQL.

## Identificadores e unidades

| Campo | Exemplo | Uso |
|---|---|---|
| `id` | `1` | Identificador numérico no PostgreSQL e nas rotas Java |
| `fiwareId` | `WineSensor002` | ID da entidade Orion |
| `fiwareType` | `WineSensor` | Tipo da entidade Orion |
| `mqttDeviceId` | `wine002` | Identificador do dispositivo no IoT Agent/MQTT |
| `fiwareService` | `vinheria` | Contexto FIWARE |
| `fiwareServicePath` | `/` | Caminho do contexto FIWARE |

O firmware utiliza BME680 e LDR. Temperatura é expressa em °C; umidade, em %. Luminosidade é uma leitura ADC de 0 a 4095, não lux. Medição de fumaça não faz parte do escopo atual.

## Executar localmente

Requisitos: JDK 17, Docker com Compose e acesso à internet para baixar dependências na primeira execução.

1. Copie `.env.example` para `.env` e ajuste os valores. Não versione o `.env` real.
2. Inicie o PostgreSQL:

```bash
docker compose up -d postgres
```

3. Carregue as variáveis no processo Java. O Docker Compose lê `.env`, mas Spring Boot não carrega esse arquivo automaticamente. Em Linux/Bash, com um arquivo local confiável e compatível com a sintaxe do shell:

```bash
set -a
. ./.env
set +a
./mvnw spring-boot:run
```

No Windows/IntelliJ, configure as mesmas variáveis no ambiente de execução. Use `mvnw.cmd` no lugar de `./mvnw`.

4. Consulte `http://127.0.0.1:8080/actuator/health`.

Flyway aplica as migrações na inicialização. O usuário PostgreSQL não é um usuário de login da API: os usuários da aplicação ficam em `app_users`. A ferramenta administrativa [tools/CreateAppUser.java](tools/CreateAppUser.java) cria usuários; confira suas instruções e requisitos antes da execução. Não existe cadastro público de usuários.

O padrão do cookie de sessão é `Secure=true`. Para testes exclusivamente locais por HTTP, configure `SESSION_COOKIE_SECURE=false` no ambiente local; em produção HTTPS mantenha `true`. Não desabilite CSRF.

O Compose publica o PostgreSQL apenas em `127.0.0.1:5432`, usa volume persistente e política `restart: unless-stopped`. Não remova o volume para atualizar a aplicação. Alterar a senha no `.env` não muda automaticamente a senha de um banco já inicializado.

## Variáveis de ambiente

| Variável | Finalidade |
|---|---|
| `SPRING_DATASOURCE_URL` | URL JDBC do PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | Usuário do PostgreSQL |
| `SPRING_DATASOURCE_PASSWORD` | Senha do PostgreSQL; o exemplo é somente para desenvolvimento |
| `SERVER_PORT` | Porta Java; padrão `8080` |
| `SERVER_ADDRESS` | Endereço de escuta; padrão `127.0.0.1` |
| `SESSION_COOKIE_SECURE` | Cookie somente por conexão segura; padrão `true` |
| `FIWARE_ORION_BASE_URL` | URL do Orion |
| `FIWARE_STH_BASE_URL` | URL do STH-Comet |
| `FIWARE_IOTA_BASE_URL` | URL administrativa do IoT Agent |
| `FIWARE_IOTA_API_KEY` | Chave do grupo de dispositivos no IoT Agent |
| `FIWARE_SERVICE` | Contexto FIWARE configurado; padrão `vinheria` |
| `FIWARE_SERVICE_PATH` | Caminho FIWARE configurado; padrão `/` |
| `FIWARE_CONFIGURATION_ENABLED` | Habilita o envio de limites ao ESP; padrão `false` |

Configure endereços alcançáveis a partir da máquina que executa o Java. Em produção, mantenha banco e portas administrativas FIWARE restritos à rede necessária. Não coloque senhas, tokens, arquivos de cookies ou chaves privadas no Git ou no frontend.

## Autenticação

A API Java implementa autenticação por sessão com Spring Security e usuários persistidos no PostgreSQL. As senhas são armazenadas como hashes; a ferramenta administrativa usa BCrypt.

Fluxo para o painel ou cliente HTTP:

1. `GET /api/auth/csrf`: guardar o cookie e o token retornados.
2. `POST /api/auth/login`: enviar `username` e `password` como `application/x-www-form-urlencoded`, com o cookie e o cabeçalho CSRF informado pela API.
3. Após o login, guardar o cookie atualizado e buscar um novo token CSRF.
4. Enviar o cookie nas rotas protegidas. POST, PUT e DELETE também exigem CSRF.
5. `POST /api/auth/logout`: encerrar a sessão com o token atual.

O cookie é `HttpOnly`, `SameSite=Lax` e, por padrão, `Secure`. A sessão tem timeout de 30 minutos de inatividade e é encerrada ao reiniciar o backend. Essa implementação não persiste sessões no PostgreSQL.

As rotas funcionais Java exigem autenticação. A saúde básica em `/actuator/health` e a obtenção de CSRF são públicas. Não há diferenciação de permissões por usuário nesta versão; os usuários habilitados compartilham o mesmo nível de acesso.

## Rotas Java

| Método | Rota | Finalidade |
|---|---|---|
| GET | `/api/auth/csrf` | Obter token CSRF |
| POST | `/api/auth/login` | Autenticar |
| GET | `/api/auth/me` | Consultar usuário da sessão |
| POST | `/api/auth/logout` | Encerrar sessão |
| GET / POST | `/api/environments` | Listar / criar ambientes |
| GET / POST | `/api/devices` | Listar / cadastrar dispositivos |
| DELETE | `/api/devices/{id}` | Remover dispositivo do IoT Agent, Orion e cadastro local |
| POST | `/api/devices/{id}/provision` | Provisionar o cadastro no IoT Agent |
| GET / PUT | `/api/devices/{id}/limits` | Consultar / persistir limites por métrica |
| GET | `/api/devices/{id}/readings/current` | Consultar estado atual no Orion |
| GET | `/api/devices/{id}/readings/history/{metric}` | Consultar histórico no STH-Comet |
| POST | `/api/devices/{id}/commands/alert` | Enviar comando manual de alerta |
| POST | `/api/devices/{id}/configuration` | Registrar e enviar configuração completa ao ESP |
| GET | `/api/devices/{id}/configuration` | Consultar e reconciliar a configuração mais recente |

Exemplo de cadastro, usando um ambiente já existente:

```json
{
  "name": "WineGuard 002",
  "fiwareId": "WineSensor002",
  "mqttDeviceId": "wine002",
  "fiwareType": "WineSensor",
  "fiwareService": "vinheria",
  "fiwareServicePath": "/",
  "environmentId": 1
}
```

Cadastro no PostgreSQL e provisionamento no IoT Agent são etapas distintas. O provisionamento responde `201`, mas não cria usuário, senha ou ACL no broker MQTT. O cadastro rejeita duplicidades FIWARE e MQTT no mesmo contexto.

A remoção responde `204`, sem JSON. Ela verifica o vínculo no IoT Agent, remove os registros externos e depois o cadastro local. Os limites e registros de configuração associados são removidos por cascata no PostgreSQL; o histórico STH não é apagado. Credenciais e ACLs MQTT não são revogadas automaticamente.

As operações envolvendo FIWARE e PostgreSQL não têm transação distribuída. Falhas podem deixar operações parcialmente concluídas; não presuma reversão automática nem repita chamadas de escrita indiscriminadamente.

## Leituras e histórico

O Java retorna a leitura Orion no formato `keyValues`. O histórico usa `dateFrom` e `dateTo` obrigatórios, com início anterior ao fim, e métrica `TEMPERATURE`, `HUMIDITY` ou `LUMINOSITY`.

A consulta STH atual fixa `hLimit=20` e `hOffset=0`: retorna a primeira página do intervalo, não garante os vinte pontos mais recentes nem a série inteira. Não há paginação exposta nessa rota.

As rotas Express mantidas para o dashboard são:

- `GET /api/sensors/WineSensor002/readings`
- `GET /api/sensors/WineSensor002/history?attribute=temperature&limit=20`

Essas rotas Express de leitura continuam públicas na implantação atual. A proteção da API Java não se aplica automaticamente a elas. O frontend usa a mesma origem HTTPS pública e não acessa diretamente MQTT, Orion ou STH.

## Alertas e configuração do ESP

O comando manual usa `POST /api/devices/{id}/commands/alert`:

```json
{"command":"temperature"}
```

Valores aceitos: `normal`, `temperature`, `humidity` e `luminosity`. A resposta `202` indica aceitação do envio, não comprova acionamento físico. No firmware, `normal` encerra somente alertas remotos; alertas locais persistem enquanto sua condição continuar ativa.

`PUT /limits` apenas persiste limites por métrica. Para alterar os limites locais do ESP, envie uma configuração completa para `POST /configuration`. O backend gera a revisão e acompanha `PENDING`, `APPLIED`, `REJECTED` ou `UNCONFIRMED`.

Uma configuração só passa para `APPLIED` após a telemetria corresponder à revisão e aos cinco valores registrados. A reconciliação e a verificação do prazo de 30 segundos ocorrem na consulta GET; não há tarefa periódica nesta versão.

Resultados indefinidos bloqueiam novos envios. Não há reenvio automático nem endpoint de reenvio da mesma revisão. Consulte o estado após falha de rede, timeout ou `502`, pois a operação pode ter sido parcialmente executada.

O firmware utiliza limites padrão na primeira inicialização, histerese local e armazenamento NVS das configurações. A persistência após reinício ainda precisa de validação física nesta rodada de integração. Os valores usados nos testes são de bancada, não limites operacionais aprovados para vinhos.

Consulte o contrato completo, os tópicos MQTT e as limitações em [docs/configuracao-do-esp.md](docs/configuracao-do-esp.md).

## Persistência e migrações

- V1: ambientes, dispositivos e limites por métrica.
- V2: usuários da aplicação.
- V3: identificador MQTT dos dispositivos.
- V4: configurações completas, revisões e estados de confirmação.

Tabelas da aplicação: `environments`, `devices`, `device_limits`, `app_users` e `device_configurations`. Flyway mantém também `flyway_schema_history`.

Não altere migrações já aplicadas. Mudanças posteriores no schema devem receber uma nova versão. Dados de demonstração não devem ser inseridos automaticamente no ambiente implantado.

## Testes e evidências

```bash
./mvnw test
./mvnw clean package
```

No Windows, use `mvnw.cmd`. A suíte atual tem 18 testes unitários, sem dependência de banco, FIWARE ou ESP:

- `EspConfigurationReportTest`: interpretação da configuração ativa.
- `EspConfigurationResultTest`: interpretação de confirmações e rejeições.

Validações manuais realizadas na integração:

- Saúde, cadastros, limites e leituras reais Orion/STH.
- Login, CSRF, acesso sem sessão e logout por HTTPS.
- Provisionamento e remoção de um dispositivo temporário.
- Rejeição de revisão inválida pelo ESP e chegada da resposta ao Orion.
- Aplicação da revisão 1 e reconciliação da API para `APPLIED`.
- Repetição da mesma revisão retornando `already_applied`.
- POST da revisão 2 retornando `202`; ausência de confirmação com ESP desconectado resultando em `UNCONFIRMED`.
- Correção e teste do envio HTTP do Spring com Content-Length, exigido pelo Orion instalado.

Os testes unitários não substituem os testes físicos. Ficam pendentes a conclusão do fluxo da revisão 2, a persistência após reinício do ESP e a integração da versão atualizada do dashboard. O acionamento físico pelo novo endpoint Java de alerta também precisa de validação conjunta.

## Implantação e documentação

Os modelos de Nginx, systemd e renovação de certificados estão em [deploy/README.md](deploy/README.md). O Java e o PostgreSQL permanecem em loopback, atrás do Nginx HTTPS. Os serviços dependem da disponibilidade das instâncias do laboratório AWS.

O repositório representa o componente Java e será reunido aos demais componentes pela equipe. Não se presume publicação automática dos commits locais na branch remota.

Documentos anteriores, como a coleção Postman de nove endpoints, o guia da equipe, o diagrama e as páginas iniciais de API/banco/execução, podem descrever o contrato antigo. Precisam ser revisados para incluir autenticação, mqttDeviceId e as novas rotas; não devem ser usados como evidência de cobertura completa da versão atual.
