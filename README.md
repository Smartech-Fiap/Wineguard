# WineGuard

Projeto acadêmico de uma equipe de quatro integrantes para monitorar temperatura, umidade e luminosidade em ambientes de armazenamento de vinhos. O ESP32 publica medições via MQTT/TLS, o FIWARE mantém o estado atual e o histórico, e o painel consulta os dados e envia comandos pela API Java.

**Painel publicado:** [wineguard-peach.vercel.app](https://wineguard-peach.vercel.app/).

O acesso exige conta fornecida pela equipe; não existe cadastro público. A API depende das instâncias do laboratório AWS ativas e de certificado HTTPS válido. O site estático na Vercel pode abrir mesmo quando a API ou o ESP estão indisponíveis.

## Equipe e responsabilidades

| Integrante | RM | Responsabilidade informada pela equipe |
|---|---|---|
| Artur de Novazzi Maia | 572624 | FIWARE, integração final e Express |
| João Pedro de Souza | 571437 | Banco de dados e backend Java |
| Bruno Araújo Castro | 572723 | Painel e dashboard |
| Arthur Pinheiro Germano | 574042 | ESP32 e firmware |

Nomes, RMs e responsabilidades foram informados pela equipe. Os históricos dos componentes e os créditos da infraestrutura de origem foram preservados.

## Estado da entrega

O fluxo painel → API Java → FIWARE/MQTT → ESP foi validado manualmente. Frontend, backend e firmware estão reunidos neste repositório, com seus históricos Git preservados. Os modelos FIWARE/MQTT estão em `infra/fiware/` e precisam de reprodução em instalação nova. No firmware, versões do ambiente de compilação, esquema elétrico e arquivos Nextion ainda precisam de confirmação e complementação.

| Componente | Disponibilidade |
|---|---|
| [frontend/](frontend/) | React, Vite, telas, testes e configuração Vercel |
| [backend/](backend/) | API Java, PostgreSQL/Compose, migrações, testes e documentação |
| [firmware/](firmware/) | Sketch ESP32 importado, modelos locais e instruções; versões, esquema elétrico, arquivos Nextion e recompilação reproduzível pendentes |
| [infra/fiware/](infra/fiware/) | Compose validado estruturalmente, MQTT/TLS, templates, gerador privado e licença da base; sete testes offline aprovados no PC e na VM, reprodução nova pendente |

Os modelos Nginx, systemd e renovação de certificados da API estão em [backend/deploy/](backend/deploy/). Eles não substituem os arquivos da infraestrutura FIWARE/MQTT.

## Arquitetura

A [arquitetura em camadas](docs/arquitetura-em-camadas.md) detalha apresentação, aplicação/API, integração IoT, persistência e dispositivo, com referências ao código e aos fluxos de dados. O diagrama abaixo resume a integração entre componentes.

```mermaid
flowchart LR
    Browser["Navegador · painel React"] -->|"HTTPS /api/*"| Vercel["Vercel · frontend e proxy"]
    Vercel -->|HTTPS| Nginx["Nginx · EC2 wineguard-app"]
    Nginx --> Java["API Java / Spring Security"]
    Java --> PG["PostgreSQL · cadastros e configurações"]
    Java --> Orion["Orion · estado e comandos"]
    Java --> STH["STH-Comet · histórico"]
    Java --> IOTA["IoT Agent UltraLight · provisionamento"]
    ESP["ESP32 · sensores e alertas"] <-->|"MQTT/TLS"| Broker["Mosquitto · EC2 FIWARE"]
    Broker <--> IOTA
    IOTA <--> Orion
    Orion -->|"Notificações"| STH
```

O PostgreSQL guarda usuários, ambientes, dispositivos, limites e acompanhamento das configurações. As leituras atuais vêm do Orion; o histórico permanece no STH-Comet, com persistência MongoDB na infraestrutura FIWARE.

O navegador usa rotas relativas `/api/`. Na Vercel, [frontend/vercel.json](frontend/vercel.json) encaminha essas chamadas à API HTTPS da AWS. O proxy de desenvolvimento do Vite é separado. O navegador não acessa diretamente MQTT, Orion ou STH-Comet.

## Funcionalidades e permissões

- Login por sessão, identificação do perfil, logout e CSRF nas escritas.
- ADMIN cadastra contas USER e acessa todos os dispositivos.
- USER acessa somente os dispositivos que cadastrou. Acesso direto a sensor de outra conta retorna 404.
- Ambientes continuam compartilhados; o isolamento por proprietário se aplica aos dispositivos e suas operações.
- Novas contas USER incluem nome e localização da vinícola. Todos os usuários autenticados veem o mapa de localizações, sem dados dos sensores de outras contas.
- Leituras atuais, histórico, limites por métrica, provisionamento e remoção.
- Alertas manuais e configuração completa do ESP, com revisão, confirmação e reenvio explícito da mesma revisão.

As rotas legadas `/api/sensors` e `/api/sensors/*` estão bloqueadas com 404 no Nginx em HTTP e HTTPS. O painel utiliza as rotas Java protegidas.

Cadastro e provisionamento no IoT Agent são etapas distintas. Provisionar não cria credenciais ou ACLs MQTT. Remover um dispositivo não revoga automaticamente essas credenciais nem apaga o histórico STH.

## Sensores e configuração

O firmware utiliza ESP32, BME680 e LDR, com display, LEDs e buzzer. Temperatura usa °C; umidade usa %. No firmware e na API, luminosidade é ADC bruto de 0 a 4095, sem calibração em lux; o dashboard apresenta a escala em porcentagem. A área Limites e alertas registra a configuração completa por revisão e a envia ao ESP, diferenciando solicitação de confirmação. Medição de fumaça não faz parte do escopo.

Salvar um limite por métrica no PostgreSQL não configura o ESP. O envio completo usa revisão e estados `PENDING`, `APPLIED`, `REJECTED` e `UNCONFIRMED`. Aceitação HTTP não comprova execução física. Os parâmetros dos testes são de bancada, não faixas operacionais aprovadas para conservação de vinhos.

O comando remoto `normal` encerra o alerta remoto; não deve cancelar uma condição local persistente. Consulte o [contrato de configuração do ESP](backend/docs/configuracao-do-esp.md).

## Executar os componentes

### Frontend

Requisitos: Node.js e npm compatíveis com as dependências, além de acesso à API para dados reais. Na pasta `frontend/`, copie `.env.example` para `.env.local` e configure `VITE_API_JAVA_TARGET` com o endereço da API.

```bash
cd frontend
npm ci
npm run dev
```

Consulte [frontend/README.md](frontend/README.md) para variáveis, cookies, contratos e publicação. Mantenha sessão e CSRF tanto no desenvolvimento quanto na publicação HTTPS.

O arquivo [frontend/requirementes.txt](frontend/requirementes.txt) documenta os requisitos com o nome solicitado no enunciado. As dependências são gerenciadas por npm, com `package.json` e `package-lock.json`; o dashboard não exige pacotes Python.

### Backend

Requisitos: JDK 17, Docker com Compose e acesso às dependências FIWARE para operações de integração. Na pasta `backend/`, copie `.env.example` para `.env`, ajuste as conexões e inicie o PostgreSQL:

```bash
cd backend
docker compose up -d postgres
```

Configure as variáveis no processo Java antes de iniciá-lo: Spring Boot não lê automaticamente `.env`. Os passos para Linux/Bash, Windows/IDE, Maven Wrapper e criação inicial de usuários estão em [backend/README.md](backend/README.md).

Flyway aplica V1–V8 na inicialização. Não altere migrações já aplicadas nem remova o volume PostgreSQL para atualizar o código. A preparação da conta ADMIN é uma operação administrativa do ambiente; não há promoção pública de usuários.

### Firmware e infraestrutura

Abra `firmware/WineGuard_firmware/WineGuard_firmware.ino` no Arduino IDE, crie os headers locais a partir dos exemplos e configure a CA e a rede. Confira as [dependências, pinos e pendências do firmware](firmware/README.md) antes de compilar/gravar. A reprodução exata do ambiente de compilação ainda depende das versões informadas pelo responsável.

Os [modelos FIWARE/MQTT](infra/fiware/README.md) incluem Compose, TLS, ACL, provisionamento e assinatura STH. Sua validação estrutural passou; a reprodução em host novo ainda é necessária. Não aplique o modelo sobre a VM existente sem planejar credenciais, certificados, volumes e interrupção.

## Testes e evidências

Dentro de `frontend/`:

```bash
npm run lint
npm test
npm run build
```

Dentro de `backend/`:

```bash
./mvnw test
./mvnw clean package
```

No Windows, use `mvnw.cmd` no lugar de `./mvnw`.

Na validação de 30/10/2026, passaram **71 testes do frontend**, com HTTP simulado, e, na validação anterior de 29/10/2026, **77 testes do backend**, incluindo regras de serviço e segurança de controladores com dependências simuladas. Essas suítes não exigem ESP ou FIWARE ativos e não substituem testes físicos, consultas JPA reais ou concorrência do PostgreSQL.

Foram verificados manualmente: login ADMIN/USER, sessão após recarga, logout, restrições de acesso, mapa compartilhado, salvamento de limite com CSRF pela Vercel, alerta físico de temperatura, comando `normal`, recuperação da revisão 2 e telemetria da configuração após o reinício solicitado do ESP. A revisão 2 permaneceu aplicada após recarregar o painel.

- [Evidências da Vercel e do ESP](backend/docs/testes-vercel-esp.md).
- [Postman e evidências de execução parcial](backend/docs/postman/README-postman.md).
- [Implantação da API e HTTPS](backend/deploy/README.md).
- [Migrações, rotas e contratos](backend/README.md).
- [Infraestrutura, origem e procedimento de reprodução](infra/fiware/README.md). Compose validado com `docker compose config --quiet` e sete testes offline aprovados também na VM; isso não substitui a execução da stack revisada.
- [Firmware, configuração local e sinais dos componentes](firmware/README.md). A integração física registrada não equivale à recompilação deste sketch ou à comparação do binário gravado com seu commit.

## Entrega acadêmica

O [pacote de entrega](docs/entrega-final.md) reúne identificação da equipe, descrição pronta da solução, links e checklist do Forms. Consulte o [manual consolidado](docs/manual-instalacao-operacao.md) e o [roteiro de apresentação e vídeo](docs/roteiro-apresentacao-video.md).

A apresentação já foi realizada, conforme confirmação da equipe. O vídeo está concluído e será enviado diretamente pelo Forms. As [fotos frontal e interna](docs/evidencias/README.md) documentam o encapsulamento. Os arquivos Nextion ainda não foram fornecidos.

## Próximas etapas

A [matriz de aderência ao Check Point](docs/aderencia-checkpoint.md) relaciona o enunciado, as adaptações informadas como aprovadas, as evidências e os itens ainda necessários para a entrega.

- Confirmar versões de placa/core/bibliotecas e completar esquema elétrico e arquivos Nextion; compilar e registrar o teste do firmware a partir deste repositório.
- Reproduzir a stack de `infra/fiware/` em instalação separada, conferindo TLS, permissões, provisionamento e histórico. A validação estrutural já passou. Não substituir a VM existente com o modelo sem planejar a migração.
- Concluir as operações Postman ainda não executadas, incluindo seu novo cadastro de usuário.
- Conferir documentos antigos, requisitos da faculdade, divisão de autoria e roteiro da apresentação com os quatro integrantes.

O repositório ainda não contém tudo para reproduzir a infraestrutura e o hardware do zero. A integração operacional testada e a consolidação dos arquivos são etapas distintas.

## Credenciais e arquivos locais

Não versione `.env`, `.env.local`, senhas, tokens, cookies, chaves privadas ou exportações Postman com credenciais. Use os arquivos de exemplo e mantenha segredos no ambiente de execução. Endereços públicos do painel e da API não são credenciais.
