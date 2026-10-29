# WineGuard Backend — Vinharia Agnello

Backend Java do projeto acadêmico WineGuard. Gerencia ambientes, dispositivos, limites, usuários e configurações enviadas ao ESP. Integra o Orion, o STH-Comet e o IoT Agent UltraLight.

O PostgreSQL guarda os cadastros e o acompanhamento das configurações. As leituras atuais vêm do Orion; o histórico de medições permanece no STH-Comet.

## Arquitetura

```text
Painel React ── HTTPS / Nginx
                    ├─ /api/auth/* ────────── Java / Spring Security
                    ├─ /api/environments* ─── Java
                    ├─ /api/devices* ──────── Java
                    ├─ /api/admin/users ──── Java, somente ADMIN
                    ├─ /api/wineries ─────── Java, mapa autenticado
                    └─ /api/sensors/* ────── bloqueado no Nginx (404)

Java ── PostgreSQL: cadastros, usuários, limites e configurações
     ├─ Orion: estado atual e envio de comandos
     ├─ STH-Comet: histórico de medições
     └─ IoT Agent: provisionamento e remoção de dispositivos

ESP32 ⇄ MQTT/TLS ⇄ Mosquitto ⇄ IoT Agent ⇄ Orion
```

O painel foi adaptado para consultar leituras e histórico pela API Java autenticada. O Express pode permanecer em execução interna, mas suas antigas rotas de sensores estão bloqueadas no Nginx em HTTP e HTTPS.

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

As rotas funcionais Java exigem autenticação. A saúde básica em `/actuator/health` e a obtenção de CSRF são públicas. Existem dois perfis: ADMIN administra contas e acessa todos os dispositivos; USER acessa somente os dispositivos que cadastrou. O backend associa o proprietário à conta autenticada, sem aceitar essa escolha pelo cliente. Dispositivos antigos sem proprietário ficam acessíveis somente ao administrador.

A autorização por proprietário também protege limites, leituras, histórico, comandos, provisionamento, remoção, configurações e reenvios. Acesso a dispositivo de outra conta retorna 404. Ambientes continuam sendo cadastros compartilhados; não há propriedade de ambientes nesta versão.

GET /api/auth/me retorna username e role. Novas contas criadas pela API administrativa recebem perfil USER; o cliente não escolhe nem promove o perfil.

## Rotas Java

| Método | Rota | Finalidade |
|---|---|---|
| GET | `/api/auth/csrf` | Obter token CSRF |
| POST | `/api/auth/login` | Autenticar |
| GET | `/api/auth/me` | Consultar usuário da sessão |
| POST | `/api/auth/logout` | Encerrar sessão |
| GET / POST | `/api/admin/users` | Listar / criar contas, somente ADMIN |
| GET | `/api/wineries` | Consultar localizações compartilhadas, com autenticação |
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
| POST | `/api/devices/{id}/configuration/{revision}/retry` | Reenviar explicitamente a mesma revisão e seus valores armazenados |

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

As rotas antigas /api/sensors e /api/sensors/* retornam 404 no Nginx em HTTP e HTTPS. Elas não devem ser reabertas, pois não aplicam a autorização por proprietário.

O painel consulta as rotas Java do dispositivo selecionado, sem fallback para o Express. Uma conta sem dispositivos não consulta telemetria. Dados da seleção anterior ficam ocultos ao trocar de conta ou dispositivo.

O histórico do painel consulta uma janela de 24 horas, limitada à primeira página de 20 registros; isso não garante os 20 pontos mais recentes. O navegador não acessa diretamente MQTT, Orion ou STH.

## Alertas e configuração do ESP

O comando manual usa `POST /api/devices/{id}/commands/alert`:

```json
{"command":"temperature"}
```

Valores aceitos: `normal`, `temperature`, `humidity` e `luminosity`. A resposta `202` indica aceitação do envio, não comprova acionamento físico. No firmware, `normal` encerra somente alertas remotos; alertas locais persistem enquanto sua condição continuar ativa.

`PUT /limits` apenas persiste limites por métrica. Para alterar os limites locais do ESP, envie uma configuração completa para `POST /configuration`. O backend gera a revisão e acompanha `PENDING`, `APPLIED`, `REJECTED` ou `UNCONFIRMED`.

Uma configuração só passa para `APPLIED` após a telemetria corresponder à revisão e aos cinco valores registrados. A reconciliação e a verificação do prazo de 30 segundos ocorrem na consulta GET; não há tarefa periódica nesta versão.

Resultados indefinidos bloqueiam novos envios. Não há reenvio automático. O reenvio explícito usa `POST /api/devices/{id}/configuration/{revision}/retry`, preservando a revisão e os valores. Consulte [o contrato](docs/configuracao-do-esp.md). Consulte o estado após falha de rede, timeout ou `502`, pois a operação pode ter sido parcialmente executada.

O firmware utiliza limites padrão na primeira inicialização, histerese local e armazenamento NVS das configurações. A persistência após reinício ainda precisa de validação física nesta rodada de integração. Os valores usados nos testes são de bancada, não limites operacionais aprovados para vinhos.

Consulte o contrato completo, os tópicos MQTT e as limitações em [docs/configuracao-do-esp.md](docs/configuracao-do-esp.md).

## Cadastro de usuários e mapa de vinícolas

Somente ADMIN pode listar e criar contas pelas rotas GET e POST
/api/admin/users. O cadastro cria uma conta USER com senha e localização
obrigatórias. O cliente não escolhe o perfil.

Exemplo fictício do corpo de criação:

```json
{
  "username": "usuario.exemplo",
  "password": "Senha-ficticia-123!",
  "location": {
    "wineryName": "Vinícola de exemplo",
    "city": "São Paulo",
    "region": "São Paulo",
    "country": "Brasil",
    "latitude": -23.55052,
    "longitude": -46.633308
  }
}
```

A senha acima é apenas um exemplo. A API não retorna senha nem hash.
O nome de usuário diferencia maiúsculas e minúsculas e aceita de 3 a
100 caracteres: letras sem acentos, números, ponto, hífen e sublinhado.
A senha exige pelo menos 12 caracteres e no máximo 72 bytes em UTF-8.

Nome da vinícola aceita até 150 caracteres; cidade, região e país,
até 100 cada. Latitude aceita -90..90 e longitude -180..180, com até
seis casas decimais. Esses campos são obrigatórios para novas contas USER.

O painel permite escolher o ponto no mapa ou digitar coordenadas.
Não há geocodificação automática: o administrador deve conferir se
o ponto corresponde ao endereço informado.

A conta ADMIN pode permanecer sem localização. Contas antigas sem
localização continuam válidas, mas não recebem marcadores fictícios.
Esta versão não oferece uma rota para editar a localização dessas contas.

GET /api/wineries exige autenticação. USER e ADMIN veem todas as
localizações retornadas. A resposta contém somente id, wineryName,
city, region, country, latitude e longitude, com Cache-Control: no-store.

A consulta inclui somente contas habilitadas com localização cadastrada.
Não retorna nome de usuário, senha, dispositivos ou leituras.

O Nginx deve encaminhar /api/admin/, /api/wineries e /api/wineries/
para o Java. O proxy local do Vite também encaminha essas rotas para
a mesma API usada na autenticação.

O mapa usa Leaflet e tiles OpenStreetMap com atribuição. Após cadastrar
uma conta com localização, o painel atualiza os marcadores.

## Persistência e migrações

- V1: ambientes, dispositivos e limites por métrica.
- V2: usuários da aplicação.
- V3: identificador MQTT dos dispositivos.
- V4: configurações completas, revisões e estados de confirmação.
- V5: contador de tentativas, última tentativa e bloqueio temporário de envio.
- V6: perfis ADMIN e USER.
- V7: proprietário dos dispositivos; registros antigos permanecem sem atribuição automática.
- V8: localização da vinícola na conta, com restrições de coordenadas e preenchimento completo ou totalmente ausente.

Tabelas da aplicação: `environments`, `devices`, `device_limits`, `app_users` e `device_configurations`. Flyway mantém também `flyway_schema_history`.

Não altere migrações já aplicadas. Mudanças posteriores no schema devem receber uma nova versão. Dados de demonstração não devem ser inseridos automaticamente no ambiente implantado.

## Testes e evidências

```bash
./mvnw test
./mvnw clean package
```

No Windows, use `mvnw.cmd`. A suíte atual tem 77 testes unitários e de controlador/segurança com dependências simuladas, sem exigir PostgreSQL, FIWARE ou ESP em execução:

- `EspConfigurationReportTest`: interpretação da configuração ativa.
- `EspConfigurationResultTest`: interpretação de confirmações e rejeições.
- Testes de controle de tentativas e reenvio de configurações.
- Testes de administração de usuários, hashes e localização obrigatória.
- Testes de autenticação, perfis ADMIN/USER, CSRF e coordenadas inválidas.
- Testes de propriedade dos dispositivos.
- Testes de acesso ao mapa e de respostas sem dados de contas ou sensores.

Repositórios simulados não validam consultas JPA reais nem restrições SQL. A inicialização e os testes na implantação complementam essa cobertura.

Validações manuais realizadas na integração:

- Saúde, cadastros, limites e leituras reais Orion/STH.
- Login, CSRF, acesso sem sessão e logout por HTTPS.
- Provisionamento e remoção de um dispositivo temporário.
- Rejeição de revisão inválida pelo ESP e chegada da resposta ao Orion.
- Aplicação da revisão 1 e reconciliação da API para `APPLIED`.
- Repetição da mesma revisão retornando `already_applied`.
- POST da revisão 2 retornando `202`; ausência de confirmação com ESP desconectado resultando em `UNCONFIRMED`.
- Correção e teste do envio HTTP do Spring com Content-Length, exigido pelo Orion instalado.
- Inicialização do backend com as migrações V6, V7 e V8.
- Bloqueio das rotas antigas de sensores com 404 em HTTP e HTTPS.
- Conta comum sem dispositivos não exibindo sensores nem leituras; ADMIN consultando WineGuard 002.
- Cadastro de nova conta com localização e exibição do marcador no mapa.
- Nova conta visualizando o mapa compartilhado, sem administração nem sensores de outras contas.

Os testes automatizados não substituem os testes físicos. Nesta rodada, foram verificados o alerta de temperatura pelo endpoint Java, seu encerramento com normal, a recuperação da revisão 2 e a manutenção da configuração após o reinício solicitado do ESP.

O frontend foi publicado em https://wineguard-peach.vercel.app/. Pela Vercel, foram verificados login ADMIN e USER, sessão após recarga, logout, mapa, restrições por perfil e salvamento de limite com CSRF. O alerta físico e a recuperação da revisão 2 também foram executados pelo painel publicado.

## Implantação e documentação

Os modelos de Nginx, systemd e renovação de certificados estão em [deploy/README.md](deploy/README.md). O Java e o PostgreSQL permanecem em loopback, atrás do Nginx HTTPS. Os serviços dependem da disponibilidade das instâncias do laboratório AWS.

O repositório representa o componente Java e será reunido aos demais componentes pela equipe. Não se presume publicação automática dos commits locais na branch remota.

A coleção Postman atualizada e suas evidências estão em docs/postman. A execução registrada foi parcial, não uma execução integral da coleção. Outros documentos antigos, como o guia da equipe, o diagrama e as páginas iniciais de API/banco/execução, ainda precisam ser conferidos antes da entrega final.
