# WineGuard Backend — Vinharia Agnello

API Java para cadastro de ambientes, dispositivos FIWARE e limites ambientais. O serviço consulta o Orion para a leitura atual e o STH-Comet para histórico; o PostgreSQL guarda somente os dados de configuração da aplicação.

## Acesso rápido

- **Documentação visual da API:** [abrir diagrama, rotas e exemplos](docs/api-visual.md).
- **Guia editável da equipe:** [backend, banco, execução, Postman e integração AWS/FIWARE](docs/guia-da-equipe.docx).
- **Demonstração no Postman:** importe a [coleção com os 9 endpoints](docs/postman/WineGuard-API.postman_collection.json) e execute na ordem pelo Runner.
- **Diagrama do fluxo:** [abrir fluxo-e-execucao.svg](docs/fluxo-e-execucao.svg).
- **Banco de dados:** [modelo, tabelas e relacionamentos](docs/database.md).
- **Execução local:** [passo a passo do IntelliJ, Docker e Beekeeper](docs/rodar-localmente.md).

## Arquitetura

```text
Painel ──HTTP──> Spring Boot / API MVC ──> PostgreSQL
                         │                   ├─ ambientes
                         │                   ├─ dispositivos
                         │                   └─ limites por dispositivo/variável
                         ├──NGSIv2──> Orion (estado atual)
                         └──STH API──> STH-Comet (histórico)
```

O projeto foi iniciado pelo [Spring Initializr](https://start.spring.io/) como Maven, Java 17, Spring Boot 4.0.9 e Spring MVC. Dependências: Spring Web MVC, Spring Data JPA, Validation, PostgreSQL Driver, Flyway, Actuator, Lombok e Spring Boot DevTools. Os arquivos estão separados por responsabilidade: `controller` recebe HTTP, `dto` define os contratos JSON, `service` aplica regras, `repository` acessa o PostgreSQL, `model` contém entidades JPA, `enums` define variáveis ambientais e `integration/fiware` conversa com Orion/STH. `config` guarda configuração, `common` trata erros e `resources/db/migration` contém migrações SQL.

Lombok fornece getters, setters seletivos e construtores de injeção (`@Getter`, `@Setter` e `@RequiredArgsConstructor`). Evitamos `@Data` nas entidades JPA para não gerar `equals`/`toString` em relacionamentos e proxies. Spring Boot DevTools está como dependência opcional para reiniciar a aplicação no desenvolvimento; ele não deve ser ativado em produção.

## Dados e relações

- `environments`: ambientes de armazenamento (nome único e descrição opcional).
- `devices`: nome, ID e tipo FIWARE, serviço, caminho FIWARE, estado ativo e ambiente obrigatório. O ID FIWARE é único dentro da combinação serviço/caminho.
- `device_limits`: uma linha por dispositivo e variável; contém unidade, valor mínimo e/ou máximo. A base rejeita limites vazios ou mínimo maior que máximo.

Cada dispositivo pertence a um ambiente (`devices.environment_id`). Cada limite pertence a um dispositivo (`device_limits.device_id`); as variáveis aceitas são `TEMPERATURE`, `HUMIDITY` e `LUMINOSITY`. Unidades vêm no registro do limite porque a equipe ainda precisa confirmar a convenção do sensor (por exemplo, °C, `%` e lux). Não foi escolhido nenhum valor de limite operacional.

O banco de aplicação **não replica leituras**: o Orion é consultado para o estado atual e o STH-Comet para séries históricas. A integração histórica usa a rota usual `STH/v1/contextEntities/...` com parâmetros `dateFrom` e `dateTo`; confirmem o path, atributo e autenticação do STH instalado pelo grupo antes de integrar ao painel.

## Requisitos

- JDK 17 ou superior.
- Docker Desktop (para PostgreSQL local), ou PostgreSQL 16 compatível.
- A primeira execução do Maven Wrapper precisa baixar o Maven e as dependências da internet.

## Executar localmente

1. Copie `.env.example` para `.env` e ajuste as URLs FIWARE ao ambiente acessível. O arquivo `.env` é ignorado pelo Git.
2. Inicie o banco: `docker compose up -d postgres`.
3. Inicie a API: `./mvnw spring-boot:run` (Windows: `mvnw.cmd spring-boot:run`). Flyway cria o schema na primeira inicialização.
4. Verifique `http://localhost:8080/actuator/health`.

### Abrir e executar no IntelliJ IDEA

Abra a pasta do projeto no IntelliJ, importe o `pom.xml` como Maven e escolha JDK 17 em **File → Project Structure → Project SDK**. Habilite o plugin Lombok e **Settings → Build, Execution, Deployment → Compiler → Annotation Processors → Enable annotation processing**. Abra `src/main/java/br/com/vinhariaagnello/wineguard/WineguardBackendApplication.java` e use o triângulo verde ao lado do método `main` → **Run WineguardBackendApplication**. Não é necessário criar arquivo `.run`: a classe principal é o ponto de entrada do Spring Boot.

Antes de executar a API, inicie o PostgreSQL pelo terminal integrado: `docker compose up -d postgres`. A aplicação usa os padrões locais de `application.yaml` e o Flyway cria as tabelas na inicialização. Se o IntelliJ ainda não mostrar o triângulo verde ou marcar `String`/imports como não resolvidos, sincronize o `pom.xml` no painel **Maven** e use **Repair IDE → Rescan Project Indexes**. A compilação Maven e a inicialização local foram confirmadas; enquanto o índice da IDE se atualiza, rode `spring-boot:run` no painel Maven (**Execute Maven Goal**) ou `./mvnw spring-boot:run` no terminal integrado. Para o DevTools perceber alterações, compile/recompile durante a execução (`Build → Build Project`).

### Rodar sem a IDE

Com JDK 17, Docker e acesso à internet na primeira compilação, execute `docker compose up -d postgres` e depois `./mvnw spring-boot:run` (Windows: `mvnw.cmd spring-boot:run`). A API fica em `http://localhost:8080`; a saúde está em `http://localhost:8080/actuator/health`. Não iniciamos serviço FIWARE local: configure `FIWARE_ORION_BASE_URL` e `FIWARE_STH_BASE_URL` com endereços alcançáveis a partir da máquina onde roda a API. O cadastro e limites continuam disponíveis sem FIWARE, mas leituras atuais/históricas exigem essas VMs e o contrato de rede confirmado com a equipe.

O schema é criado por `src/main/resources/db/migration/V1__create_application_schema.sql`. A migração não insere dispositivos automaticamente. Para dados locais fictícios, execute manualmente `src/main/resources/db/seed/demo-data.sql` depois que o banco estiver iniciado. Nunca rode esse seed como parte da implantação de produção.

No Beekeeper, o contador de entidades inclui tabelas internas do PostgreSQL, como `pg_catalog` e `information_schema`. O schema `public` desta aplicação tem três tabelas de negócio (`environments`, `devices` e `device_limits`) e uma tabela técnica do Flyway (`flyway_schema_history`). Veja a consulta para filtrar o schema da aplicação em [docs/database.md](docs/database.md).

## Variáveis de ambiente

| Variável | Uso | Exemplo local |
|---|---|---|
| `SPRING_DATASOURCE_URL` | JDBC do PostgreSQL | `jdbc:postgresql://localhost:5432/wineguard` |
| `SPRING_DATASOURCE_USERNAME` | usuário da aplicação | `wineguard` |
| `SPRING_DATASOURCE_PASSWORD` | senha local/de execução | `wineguard-local` |
| `SERVER_PORT` | porta HTTP da API | `8080` |
| `FIWARE_ORION_BASE_URL` | base URL do Orion | `http://localhost:1026` |
| `FIWARE_SERVICE` | service padrão documentado pela equipe | `vinheria` |
| `FIWARE_SERVICE_PATH` | service path padrão documentado pela equipe | `/` |
| `FIWARE_STH_BASE_URL` | base URL do STH-Comet | `http://localhost:8666` |

O cadastro de dispositivo guarda serviço e service path individualmente. Para o exemplo da imagem, use `vinheria` e `/` somente após confirmar no Orion que esse contexto está correto.

## API inicial

- `GET /api/environments` e `POST /api/environments`
- `GET /api/devices` e `POST /api/devices`
- `GET /api/devices/{id}/limits` e `PUT /api/devices/{id}/limits`
- `GET /api/devices/{id}/readings/current` (Orion)
- `GET /api/devices/{id}/readings/history/{metric}?dateFrom=...&dateTo=...` (STH-Comet)

Consulte a documentação visual em [`docs/api-visual.md`](docs/api-visual.md) e importe a coleção local [`docs/postman/WineGuard-API.postman_collection.json`](docs/postman/WineGuard-API.postman_collection.json) no Postman. A coleção usa valores fictícios e cobre cadastro, limites e leituras FIWARE.

Para mapear cada requisito de revisão aos arquivos, resultados do Postman e pendências de integração, consulte [`docs/pacote-para-equipe.md`](docs/pacote-para-equipe.md).

## Evidência de verificação manual

Em 27/10/2026, na execução local com PostgreSQL 16, os endpoints de saúde (`200`), listagem (`200`), criação de ambiente/dispositivo (`201`) e gravação/consulta de limite (`200`) foram verificados no Postman. O teste inicialmente identificou e levou à correção da consulta JPA de limites. As duas rotas de leitura FIWARE retornaram `502` porque Orion e STH-Comet não estavam acessíveis neste ambiente; são dependências externas que precisam estar ligadas para validar payloads reais.

A coleção completa também foi executada pelo Runner do Postman: os 9 endpoints rodaram em sequência, com 16 verificações aprovadas e zero erros. Os endpoints de cadastro e limites responderam com sucesso; as duas verificações de leitura aceitaram `502` como resposta controlada para FIWARE indisponível. Isso confirma o tratamento de erro do backend, mas não valida dados reais vindos das VMs.

Os testes de criação deixaram registros fictícios `Postman QA WineGuard` (ambiente) e `Device Postman QA` (dispositivo) no banco local. Os valores não representam dispositivos reais.

Exemplo de payload para cadastrar o dispositivo de referência informado na imagem (ajuste `environmentId` para um ambiente existente):

```json
{
  "name": "WineGuard 002",
  "fiwareId": "wine002",
  "fiwareType": "WineSensor002",
  "fiwareService": "vinheria",
  "fiwareServicePath": "/",
  "environmentId": 1
}
```

Exemplo de limite **ilustrativo**: não use os números como limites reais da adega.

```json
{
  "metric": "TEMPERATURE",
  "minimumValue": 10.000,
  "maximumValue": 18.000,
  "unit": "°C"
}
```

## Segurança e produção

Esta entrega não implementa autenticação/autorização de usuários porque o requisito e o provedor de identidade do projeto ainda não foram definidos. Mantenha a API em rede privada ou atrás da autenticação já usada pelo painel; não a exponha publicamente até integrar controle de acesso. Credenciais reais devem ficar fora do `.env.example` e do Git, em segredo de ambiente/AWS Secrets Manager.

## Itens para alinhar com a equipe antes da integração final

1. URLs e autenticação reais do Orion e do STH-Comet; confirmar versão NGSI, atributos exatos e `Fiware-Service`/`Fiware-ServicePath`.
2. Confirmar unidades e se limites pertencem a cada sensor (modelo atual) ou ao ambiente.
3. Definir autenticação, perfis de usuário e permissões do painel.
4. Confirmar conexão de rede entre os serviços atuais e o backend Java. Não migrar o Node/EC2 até combinar contrato e janela de transição.

## Diagrama de fluxo e guia de execução

Veja o diagrama [fluxo-e-execucao.svg](docs/fluxo-e-execucao.svg) e o guia [rodar-localmente.md](docs/rodar-localmente.md) para o fluxo entre painel, API, PostgreSQL, Orion, STH-Comet, dispositivos e Beekeeper Studio.

## Implantação AWS

Ver [docs/aws-deployment.md](docs/aws-deployment.md) para a implantação sugerida e suas dependências de rede.
