# Wineguard — painel web

Painel React e Vite para consultar leituras do dispositivo selecionado e gerenciar ambientes, dispositivos e limites pela API Wineguard. O frontend usa somente rotas relativas `/api/`; Orion, STH-Comet e MQTT não são acessados diretamente pelo navegador.

## Requisitos

- Ambiente local verificado: Node.js 24.21.0 e npm 11.19.0. Para reproduzi-lo, use Node.js 24.x com npm compatível; outras versões precisam respeitar os engines das dependências do lockfile.
- Acesso aos serviços Wineguard para visualizar e gerenciar dados reais.

O arquivo [requirementes.txt](requirementes.txt) corresponde ao nome solicitado no enunciado e descreve ambiente, dependências e comandos. O painel é React/Node.js, portanto as dependências são gerenciadas por `package.json` e `package-lock.json`, não por `pip` ou por um arquivo de pacotes Python.

## Executar localmente

```bash
npm ci
```

Crie `.env.local` na raiz do projeto usando `.env.example` como modelo:

```env
VITE_API_JAVA_TARGET=http://<HOST_DA_API_JAVA>
VITE_API_TIMEOUT_MS=10000
VITE_API_REFRESH_INTERVAL_MS=30000
```

A API Java/Spring Security atende autenticação, administração, gestão, leituras e histórico. Configure `VITE_API_JAVA_TARGET` para a origem HTTPS da API ou para o Java local. Todas as chamadas compartilham o cookie de sessão; o navegador usa apenas rotas relativas `/api/`. As antigas variáveis `VITE_API_TARGET` e `VITE_API_SENSOR_TARGET` não são utilizadas. `VITE_API_TIMEOUT_MS` e `VITE_API_REFRESH_INTERVAL_MS` são prazos em milissegundos. `.env.local` é ignorado pelo Git. Reinicie o Vite após alterar a configuração:

```bash
npm run dev
```

Não inclua endereços do backend nas chamadas do navegador.

## Login, sessão e cookies

Ao abrir o painel, o frontend consulta `GET /api/auth/me`. Se a sessão não existir, apresenta o formulário de login. O envio busca `GET /api/auth/csrf`, envia usuário e senha como `application/x-www-form-urlencoded` para `POST /api/auth/login` e busca um token CSRF novo após o login. O usuário exibido vem da resposta de `/me`. Essas rotas são atendidas pelo Java/Spring Security, no mesmo serviço das rotas de gestão.

O cookie de sessão é gerenciado pelo navegador e enviado com `credentials: same-origin`; o React não o lê nem o grava. Em produção, sirva painel e APIs sob a mesma origem HTTPS e configure o serviço de autenticação para emitir cookie `HttpOnly`, `Secure`, `SameSite=Lax` e `Path=/`, sem `Domain` quando compartilharem a origem. O token CSRF fica somente em memória e é enviado nas escritas protegidas, inclusive no logout. Não há cadastro público ou recuperação de senha. Credenciais não são persistidas em localStorage, no código ou em variáveis `VITE_*`.

No desenvolvimento local, o navegador chama `http://localhost:<porta-do-vite>/api/...` e o proxy encaminha a requisição. Para `/api/auth`, o proxy do Vite remove o atributo `Domain` do cookie de resposta para permitir que o navegador o associe a `localhost`; não remove `Secure`, `HttpOnly` ou `SameSite`. Navegadores modernos tratam `localhost` como contexto potencialmente seguro para cookies `Secure`, mas confirme o comportamento no navegador usado pela equipe; Vite local não substitui o teste final em HTTPS. Não desative CSRF para viabilizar o proxy local.

## APIs

- `GET /api/auth/csrf`, `POST /api/auth/login`, `GET /api/auth/me` e `POST /api/auth/logout`: autenticação por sessão e proteção CSRF, atendidas pelo Java. O frontend também envia o token CSRF atual em todos os POST, PUT e DELETE protegidos; não repete escritas automaticamente.
- `GET /api/devices/{deviceId}/readings/current`: leitura atual autorizada, retornada pelo Orion.
- `GET /api/devices/{deviceId}/readings/history/{metric}?dateFrom=...&dateTo=...`: histórico autorizado, retornado pelo STH-Comet.
- `GET /api/environments` e `POST /api/environments`: lista e cria ambientes. A criação envia `name` e `description`; a resposta inclui `id`, `name` e `description`.
- `GET /api/devices` e `POST /api/devices`: lista e cadastra referências e metadados. A criação envia `name`, `fiwareId`, `mqttDeviceId`, `fiwareType`, `fiwareService`, `fiwareServicePath` e `environmentId`; a resposta também inclui `environmentName` e `active`. `id` é o identificador numérico no banco, `fiwareId` identifica a entidade FIWARE (ex.: `WineSensor002`) e `mqttDeviceId` identifica o cliente MQTT (ex.: `wine002`).
- `GET /api/devices/{deviceId}/limits` e `PUT /api/devices/{deviceId}/limits`: lista e cria/substitui o limite de uma métrica. Corpo: `metric` (`TEMPERATURE`, `HUMIDITY` ou `LUMINOSITY`), `minimumValue`, `maximumValue` e `unit`. É obrigatório informar ao menos um valor; o mínimo não pode superar o máximo.
- `POST /api/devices/{deviceId}/provision`: provisiona no IoT Agent (sem criar usuário, senha ou ACL MQTT); responde `201`.
- `DELETE /api/devices/{deviceId}`: remove o cadastro e responde `204` sem corpo. A interface exige confirmação explícita.
- `POST /api/devices/{deviceId}/commands/alert`: corpo `{"command":"temperature"}`; aceita `normal`, `temperature`, `humidity` ou `luminosity`. HTTP `202` significa aceito para envio, não confirma execução física.
- `POST /api/devices/{deviceId}/configuration`: envia a configuração completa `{temperatureMin, temperatureMax, humidityMin, humidityMax, luminosityMax}` e recebe `202` com uma revisão `PENDING` criada pelo backend. Faixas: temperatura −20..60 °C, no máximo uma casa decimal, intervalo >2 °C; umidade 0..100%, no máximo uma casa decimal, intervalo >6 pontos; luminosidade inteira 200..4095 ADC.
- `GET /api/devices/{deviceId}/configuration`: retorna e reconcilia a configuração mais recente. A UI apresenta `PENDING`, `APPLIED`, `REJECTED` e `UNCONFIRMED`, revisão, valores, `createdAt`, `confirmedAt` e motivo de rejeição. Só chama valores de ativos após `APPLIED`. Após envio, acompanha por GET a cada 5 segundos por até 45 segundos, sem sobreposição.
- `POST /api/devices/{deviceId}/configuration/{revision}/retry`: reenvia a revisão já armazenada, sem corpo nem novos valores, com sessão autenticada e token CSRF atual. `200` indica que a revisão já estava confirmada como aplicada e não inicia novo acompanhamento; `202` indica reenvio aceito como `PENDING`, não aplicação física. A ação da UI aparece somente para revisão conhecida `UNCONFIRMED`: consulta o estado antes do POST e pede confirmação para reenviar os valores armazenados. `APPLIED`, `REJECTED`, `PENDING` ou uma revisão alterada bloqueiam o POST. Após `202`, acompanha somente por GET a cada 5 segundos por até 45 segundos, sem sobreposição. Timeout, falha de rede ou `502` nunca repetem automaticamente o POST e provocam uma consulta de estado; `409` apresenta o bloqueio e permite nova consulta.

As leituras usam `GET /api/devices/{deviceId}/readings/current`, com resposta Orion `keyValues` (`TimeInstant` normalizado como `measuredAt`). O histórico usa `GET /api/devices/{deviceId}/readings/history/{metric}?dateFrom=...&dateTo=...`, com métricas `TEMPERATURE`, `HUMIDITY` e `LUMINOSITY`. O painel consulta uma janela móvel de 24 horas e exibe até 20 registros da primeira página retornada pelo backend, sem alegar que são os 20 mais recentes. A resposta bruta do STH-Comet é convertida em pontos e ordenada por horário.

A lista de dispositivos vem da API Java: USER acessa seus próprios sensores e ADMIN acessa todos. A autorização deve ser aplicada no backend para cada operação. Sem dispositivos, o painel não consulta telemetria. Leituras e histórico ficam vinculados à conta e ao dispositivo selecionado, com cancelamento de consultas antigas ao trocar de seleção; dados da sessão anterior são descartados no novo login. Não existe fallback para `/api/sensors/*`. Na implantação, mantenha essas rotas antigas bloqueadas em HTTP e HTTPS e as portas internas inacessíveis publicamente.

A luminosidade é mostrada como ADC bruto, sem unidade física calibrada. Horários usam o fuso do navegador.

Leituras e histórico têm estados independentes de carregamento, erro, timeout e ausência de dados; uma falha de atualização preserva a última resposta bem-sucedida. A navegação mobile oferece links diretos para as seções disponíveis.

Leituras atuais e histórico são consultados a cada 30 segundos, sem sobreposição, e o polling é suspenso enquanto a página está oculta. O painel não consulta `/api/auth/me` em intervalos: verifica a sessão ao abrir e ao retornar a uma página antes oculta (ou ao voltar o foco após um intervalo). Uma resposta HTTP 401 de uma chamada de dados encerra a área autenticada centralmente e pede novo login. Uma falha de atualização mantém visível a última resposta bem-sucedida.

O polling das leituras é uma consulta de dados, não um mecanismo de keep-alive intencional. O serviço de autenticação precisa definir com a equipe se chamadas de leitura renovam o timeout ocioso e garantir que polling não mantenha sessões vivas indefinidamente. O teste de sessão só está concluído após verificar essa regra em ambiente real.

## Usuários e mapa global

Somente ADMIN acessa a seção Usuários e as rotas `/api/admin/users`. Novas contas têm perfil USER; o formulário não permite escolher um perfil superior. O cadastro envia `{username, password, location}`. `location` contém `wineryName`, `city`, `region`, `country`, `latitude` e `longitude`. Os textos são obrigatórios; coordenadas aceitam até seis casas decimais, latitude −90..90 e longitude −180..180. A conta ADMIN existente não precisa de localização. Contas antigas sem localização continuam listadas na administração, mas não recebem um ponto inventado no mapa.

O administrador informa a localização manualmente e pode clicar no mapa ou digitar coordenadas. O mapa não faz geocodificação nem verifica se o endereço digitado corresponde ao ponto escolhido; confirme a posição antes de cadastrar. Senhas não são armazenadas no navegador e são limpas após a tentativa de envio. Uma resposta ambígua nunca repete automaticamente o cadastro: confira a lista antes de tentar novamente.

O mapa global consulta `GET /api/wineries` com a sessão autenticada. Todos os perfis veem as vinícolas retornadas, com nome, cidade, região, país e coordenadas; a resposta não deve incluir contas ou sensores. Não há marcadores ilustrativos de fallback. A tela oferece carregamento, erro, lista vazia e atualização manual. Após criar uma conta, o mapa é atualizado. Leaflet usa tiles do OpenStreetMap, com atribuição exibida.

Na publicação, encaminhe `/api/wineries` e `/api/wineries/` para a mesma API Java usada pela autenticação, como já ocorre no proxy local do Vite. O backend deve filtrar contas desabilitadas ou sem localização; o frontend não substitui essa regra.

## Ambientes, dispositivos, limites e ESP

As telas seguem os contratos Java enumerados acima; confirme que a revisão implantada oferece esses endpoints antes de publicar. Cadastro no banco e provisionamento no IoT Agent são ações distintas; uma falha de rede no provisionamento não apaga o cadastro. Limites por métrica são persistência separada e não alteram a configuração ativa do ESP. A configuração completa cria uma revisão e seus valores só passam a ser exibidos como ativos depois que a API Java confirma `APPLIED`. `202` de comando indica aceitação para envio, não execução comprovada. A interface não lê `configure_status` do Orion como confirmação. Avaliação de limites e atuação automática precisam ocorrer no backend/dispositivo, inclusive com o painel fechado.

As consultas limitadas de estado da configuração (até 45 segundos por envio) são necessárias para reconciliar o ESP; o dashboard não usa polling como keep-alive geral. A política de expiração ociosa e a atividade contabilizada por leitura/consulta devem ser confirmadas com o responsável pelo backend antes de afirmar que o polling nunca prolonga sessões.

## Validação

```bash
npm run lint
npm test
npm run build
```

`npm test` executa testes locais com `fetch` simulado; não valida login real, cookie/CSRF no navegador, AWS, HTTPS nem execução física do ESP. O teste final de login, recarga, sessão, rotação CSRF e logout deve ser feito separadamente no ambiente HTTPS ativo, confirmando também o comportamento do cookie no Vite local. Não use credenciais reais em testes versionados.

## Publicação dos serviços

O Java e o Node/Express coexistem; o Java não substitui automaticamente o serviço Express existente na EC2. As notas do backend descrevem uma topologia sugerida para AWS, não uma implantação realizada ou uma pipeline confirmada. Publique cada serviço pela pipeline definida pela equipe; não use o servidor de desenvolvimento do Vite em produção.

1. **Rotas antigas:** bloqueie `/api/sensors` e `/api/sensors/*` em todos os acessos públicos, inclusive HTTP e HTTPS. O painel não utiliza mais a telemetria pública do Express.
2. **API Java:** publique a aplicação Spring Boot com `/api/auth/*`, `/api/environments` e `/api/devices/*`, PostgreSQL/Flyway e conectividade às dependências FIWARE necessárias; confira `/actuator/health`. Armazene segredos no serviço de execução, não no repositório ou imagem.
3. **Frontend:** execute `npm ci`, `npm run lint`, `npm test` e `npm run build`; publique o conteúdo de `dist` no serviço web estático e configure fallback para `index.html` se necessário.

No Nginx/gateway que atende o frontend, encaminhe `/api/auth/`, `/api/environments` e `/api/devices/` à mesma API Java; `/api/admin/` também deve ir para o Java. As rotas antigas `/api/sensors` e `/api/sensors/` devem retornar 404. Preserve prefixos e caminhos. Exemplo conceitual:

```nginx
location /api/auth/ {
    proxy_pass http://wineguard_java;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}

location = /api/sensors { return 404; }
location /api/sensors/ { return 404; }

location /api/admin/ {
    proxy_pass http://wineguard_java;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}

location /api/environments {
    proxy_pass http://wineguard_java;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}

location /api/devices {
    proxy_pass http://wineguard_java;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}
```

Mantenha painel e APIs na mesma origem HTTPS pública para que o cookie de sessão funcione com `credentials: same-origin`. Configure endereços de serviço no ambiente de publicação; não versione hostnames privados, credenciais, tokens ou valores `VITE_*` de produção.


## Publicação na Vercel

No repositório final, o dashboard fica em `frontend/`. Ao importar o repositório na Vercel, selecione essa pasta como **Root Directory**, com framework Vite. O arquivo `vercel.json` define instalação por `npm ci`, build por `npm run build` e saída `dist`.

O proxy de desenvolvimento do Vite não faz parte dos arquivos publicados. Na Vercel, a regra externa de `vercel.json` encaminha `/api/*` para `https://184.192.182.39/api/*`, mantendo os caminhos. Esse IP é o endereço público da API, não uma credencial. Ajuste o destino se a infraestrutura mudar. Não é necessário configurar `VITE_API_JAVA_TARGET` para esse encaminhamento na Vercel.

O navegador deve continuar chamando apenas `/api/...` na origem do painel. Não substitua essas chamadas por URLs diretas da AWS nem desative CSRF. As respostas de API recebem a instrução `Cache-Control: private, no-store`. O cookie de sessão depende do encaminhamento correto de Cookie e Set-Cookie; o backend deve continuar emitindo o cookie sem Domain, com Secure, HttpOnly e SameSite=Lax.

Antes de considerar a publicação validada, testar no endereço HTTPS da Vercel: obter CSRF, login ADMIN e USER, recarga mantendo sessão, administração restrita, mapa, isolamento de sensores, uma escrita autorizada com CSRF e logout seguido de 401. O build local não comprova esse fluxo no proxy da Vercel.

A API ainda depende das instâncias AWS ativas e de certificado HTTPS válido. A publicação estática do frontend não mantém o laboratório ligado. Não publique `.env.local`, arquivos de cookies, credenciais ou a pasta `.vercel`.
