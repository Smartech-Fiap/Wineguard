# Coleção Postman do WineGuard

Esta coleção descreve o backend Java atual: sessão/CSRF, cadastros, leituras, provisionamento, remoção, comandos e configurações do ESP. Contém 20 requisições em oito pastas. As operações de escrita ficam ignoradas por padrão, exceto login e logout.

## Preparação

1. Importe `WineGuard-API.postman_collection.json` no Postman Desktop atualizado.
2. Importe `WineGuard.postman_environment.example.json`, selecione esse ambiente e configure os valores somente na sua cópia local.
3. Ajuste `baseUrl` para a origem do backend, sem `/api` e sem barra final. Na implantação pública, use HTTPS. Não inclua endereços reais no arquivo de exemplo versionado.
4. Informe `username` e `password` de um usuário já criado na API. Esses campos são vazios nos arquivos entregues. Mantenha os valores privados; não compartilhe ou versione a exportação preenchida. O tipo `secret` apenas mascara a apresentação, não torna uma exportação segura.
5. Mantenha o cookie jar habilitado. Cookies de sessão são gerenciados pelo Postman. Não configure Basic Auth ou Bearer.
6. Execute primeiro a pasta **01 - Autenticação**. Refaça o login após expiração da sessão ou reinício do Java.

O Java não carrega `.env` automaticamente fora do serviço configurado: carregue as variáveis no processo ao executar localmente. Para acessar diretamente Java por HTTP local, o ambiente local deve usar `SESSION_COOKIE_SECURE=false`. Na implantação HTTPS mantenha `true`.

## CSRF

O pré-script de cada POST, PUT ou DELETE permitido busca um token atual em `GET /api/auth/csrf` e adiciona o cabeçalho indicado pela resposta, usando a sessão no cookie jar. Isso inclui login/logout e resolve a rotação do token após autenticação.

Os scripts não salvam tokens em variáveis exportáveis da coleção. Falha na obtenção do token impede a requisição principal e registra teste com falha.

Os bloqueios usam `pm.execution.skipRequest()`. Use uma versão atual do Postman Desktop que suporte essa API. A execução em versões antigas, Newman ou Postman CLI não foi validada. Não trate esta coleção como controle de acesso: a autorização real permanece no backend.

## Consultas

Execute **02 - Consultas** após autenticar.

- `deviceId`: ID numérico de um dispositivo real cadastrado, como o retornado por `GET /api/devices`. Sem ID, consultas específicas são ignoradas. Não use `wine002` ou `WineSensor002` no lugar do ID numérico.
- `healthUrl`: opcional. Deixe vazio para ignorar a saúde. A rota `/actuator/health` existe no Java, mas não está necessariamente publicada pelo Nginx. Para Java local, um exemplo é `http://127.0.0.1:8080/actuator/health`.
- `dateFrom` e `dateTo`: intervalo ISO 8601. Se ambos estiverem vazios, o histórico usa as últimas 24 horas. Se preencher um, preencha os dois com início anterior ao fim.

Leituras reais exigem um dispositivo provisionado e FIWARE disponível. Um cadastro fictício não se transforma automaticamente em entidade Orion.

HTTP 502 falha os testes de leitura. HTTP 200 com histórico vazio confirma o formato da resposta, não a existência de medições. A rota Java retorna a primeira página de até vinte pontos no intervalo.

`GET configuration` pode atualizar o estado persistido por reconciliação, embora não envie comandos ao ESP. HTTP 404 sinaliza recurso ausente; confira se o dispositivo existe e se já recebeu uma configuração. Estado PENDING/UNCONFIRMED não é apresentado como aplicação física validada.

## Operações opcionais

Deixe todas as flags `false` na execução comum. Para uma operação de manutenção, habilite somente a flag correspondente no ambiente local, execute apenas a pasta ou requisição escolhida e depois volte a flag para `false`. Não rode a coleção inteira com flags habilitadas.

| Pasta | Variável necessária | Alvo |
|---|---|---|
| 03 - Cadastros demonstrativos | `allowCatalogWrites=true` | Novos cadastros fictícios |
| 04 - Provisionamento | `allowProvision=true` | `operationDeviceId` |
| 05 - Alerta físico | `allowAlert=true` | `operationDeviceId` |
| 06 - Configuração do ESP | `allowConfiguration=true` | `deviceId` real |
| 07 - Remoção | `allowRemoval=true` | `operationDeviceId`, repetido em `confirmRemovalDeviceId` |

### Cadastros demonstrativos

Execute a pasta 03 completa, em ordem e com uma única iteração. Ela cria ambiente e dispositivo com nomes únicos e grava um limite apenas no cadastro demonstrativo.

Os IDs retornados são mantidos nas variáveis locais `demoEnvironmentId` e `demoDeviceId` durante o Runner; não sobrescrevem `deviceId`, usado nas consultas reais. Anote os IDs das respostas se precisar deles depois da execução. Evite definir variáveis de ambiente ou de dados do Runner com esses nomes internos.

Essa pasta não provisiona o dispositivo nem aciona o ESP. Reexecutá-la cria outros cadastros. Os ambientes fictícios não são removidos automaticamente; não há rota de exclusão de ambiente neste contrato.

O dispositivo usa `fiwareType=WineSensor`, `mqttDeviceId` próprio e contexto de demonstração `vinheria` / `/`. Revise o contexto antes de provisionar qualquer cadastro.

### Provisionamento

Escolha um cadastro descartável em `operationDeviceId`. A resposta esperada é 201. HTTP 409 pode indicar que ele já está provisionado; não apague e recrie o dispositivo para contornar o conflito.

O provisionamento não cria usuário, senha ou ACL no broker. Os procedimentos de MQTT e histórico para novos dispositivos precisam ser alinhados com a infraestrutura.

### Comandos de alerta

`alertCommand` aceita `normal`, `temperature`, `humidity` ou `luminosity`. O exemplo é `normal`, mas até esse comando modifica o estado remoto do ESP. HTTP 202 significa aceitação do envio, não comprovação de acionamento físico. Alertas locais não são encerrados por `normal`.

### Configuração de limites do ESP

Antes de enviar, revise os cinco valores do corpo. Os padrões fornecidos são de bancada: 15..29 °C, 40..82% e luminosidade máxima 3500 ADC. Não são uma aprovação das faixas finais para conservação de vinhos.

- Temperatura: -20 a 60 °C, no máximo uma casa decimal.
- Umidade: 0 a 100%, no máximo uma casa decimal.
- Intervalos estritamente maiores que 2 °C e 6 pontos percentuais.
- Luminosidade máxima: inteiro entre 200 e 4095 ADC; não lux.

O POST gera uma nova revisão e responde 202/PENDING. Consulte depois a requisição de configuração da pasta 02. APPLIED exige revisão e valores da telemetria compatíveis; confirmedAt registra a observação pelo backend.

Não há polling nesta coleção. Faça consultas GET após o envio; a passagem para UNCONFIRMED é calculada na consulta após 30 segundos. Uma confirmação tardia ainda pode concluir o registro.

Após timeout, erro de rede ou 502, não repita automaticamente o POST. Consulte a configuração: o registro pode ter sido criado e o comando enviado. Enquanto houver PENDING/UNCONFIRMED, novos envios são bloqueados. O backend agora oferece `POST /api/devices/{id}/configuration/{revision}/retry`. A pasta 06 inclui essa requisição como operação individual, desabilitada por padrão. Preservar o registro faz parte da recuperação operacional.

HTTP 503 indica envio desabilitado no ambiente Java. Não altere o flag de execução do backend apenas para fazer o teste passar.

### Remoção

Confira cuidadosamente o ID e utilize um dispositivo descartável. Preencha o mesmo número em `operationDeviceId` e `confirmRemovalDeviceId`.

A resposta 204 tem corpo vazio. A operação remove registros IoT Agent/Orion e o cadastro local com limites/configurações relacionados. Não apaga o histórico STH nem revoga credenciais MQTT.

Não há transação distribuída ou reversão automática entre FIWARE e PostgreSQL. Em caso de falha, preserve as evidências e verifique o estado de cada componente antes de repetir a operação.

## Encerramento e evidências

Execute **08 - Encerrar sessão** ao concluir. Ela faz logout e verifica que `/api/auth/me` passa a retornar 401. Depois, será necessário executar a pasta 01 para continuar.

Não publique credenciais, cookies, tokens ou exportações preenchidas nos relatórios. Prefira um resumo dos resultados e respostas sem dados de sessão.

Validação desta entrega: JSON analisado, sintaxe dos scripts conferida e controles pré-request exercitados em simulação local. A coleção ainda precisa ser importada e executada no Postman contra a API real. Isso não é uma declaração de que os endpoints ou atuadores foram testados por esta coleção.

## Reenvio da mesma revisão

Na pasta 06, selecione somente “Reenviar a mesma revisão sem confirmação”.

Antes, consulte a configuração mais recente e confira dispositivo,
revisão e estado. Não use o POST de nova configuração para recuperar
um envio anterior.

Na cópia local do ambiente:
- Informe deviceId com o ID numérico do dispositivo.
- Informe retryRevision com a revisão existente.
- Habilite allowConfigurationRetry=true somente para essa operação.

A requisição não recebe corpo. O pré-script busca CSRF atual e envia
a sessão pelo cookie jar.

- HTTP 200: revisão já aplicada; nenhum comando foi enviado.
- HTTP 202: mesma revisão reenviada; aplicação ainda não confirmada.
- HTTP 409: conflito, prazo de espera ou tentativa em andamento.
- HTTP 502: resultado não confirmado; consulte antes de repetir.
- HTTP 503: envio desabilitado no backend.

Após 202, use o GET de configuração da pasta 02 para acompanhar.
Não há polling nem reenvio automático nesta coleção.
Ao concluir, volte allowConfigurationRetry para false.

A execução desta nova requisição no Postman contra a API real ainda
está pendente. Não a considere validada fisicamente apenas porque
o JSON foi conferido ou o envio recebeu 202.

## Usuários, localização e mapa compartilhado

A pasta 07B deve ser executada após o login e antes do logout.

### Consultas

- Listar usuários conforme o perfil: consulta a sessão antes do envio.
  Espera 200 para ADMIN ou 403 para USER. Quando autorizado, verifica
  que a resposta não contém senha nem hash.
- Consultar mapa compartilhado: espera 200 com sessão autenticada.
  Aceita lista vazia e verifica os campos de localização, as faixas
  das coordenadas e Cache-Control: no-store. Não aceita campos extras
  de contas ou sensores nos marcadores.

Sem autenticação, /api/admin/users e /api/wineries devem retornar 401.

### Cadastro individual de usuário

A criação grava uma conta real no banco e exige sessão ADMIN e CSRF.
A requisição fica desativada por padrão: allowUserCreation=false.

No ambiente LOCAL do Postman, preencha:

- newUserUsername
- newUserPassword
- newUserWineryName
- newUserCity
- newUserRegion
- newUserCountry
- newUserLatitude
- newUserLongitude

Use ponto como separador decimal nas coordenadas. Latitude aceita
-90..90 e longitude -180..180, com até seis casas decimais.

O nome de usuário deve ser único. A senha exige pelo menos
12 caracteres e até 72 bytes em UTF-8. A API cria somente perfil USER.

Habilite allowUserCreation=true apenas para executar o cadastro
escolhido. Depois, volte para false. Não execute a coleção inteira
com essa opção habilitada e não configure repetições automáticas.

O script valida os campos antes do envio, obtém CSRF e monta o JSON
sem concatenar a senha ao texto do corpo. A resposta esperada é 201;
o ID retornado fica na variável temporária createdUserId, sem alterar
o deviceId, o usuário ou a senha da sessão atual.

Após criar a conta, consulte novamente o mapa. A criação da conta
não autentica automaticamente o novo usuário e não cria sensores.

Após timeout, erro de rede, 409 ou 502, consulte a lista administrativa
antes de tentar criar novamente. Não presuma que houve reversão.

Não versione senhas nem exportações do ambiente local preenchido.
O arquivo de exemplo mantém newUserPassword vazio.

### Verificações complementares

Com uma conta USER nova, a lista de dispositivos deve estar vazia
até que ela cadastre sensores. O ADMIN continua vendo todos.
Dispositivos antigos sem proprietário ficam restritos ao ADMIN.
Acesso direto a dispositivo de outra conta deve retornar 404.

O mapa é compartilhado entre contas autenticadas, mas os dados dos
sensores continuam privados. Contas antigas sem localização não
recebem marcadores fictícios.

As rotas antigas /api/sensors e /api/sensors/* devem retornar 404
em HTTP e HTTPS. Leituras e histórico usam as rotas Java protegidas.

A validação de JSON e sintaxe dos scripts não equivale à execução
das requisições. A execução completa desta versão no Postman deve
ser registrada separadamente como evidência de integração.

## Evidências da execução manual — 29/10/2026

Execução individual no Postman contra a API HTTPS implantada:

| Verificação | Resultado |
|---|---|
| Obter CSRF | 200 |
| Login ADMIN e consulta da sessão | 200, perfil ADMIN |
| Listar contas como ADMIN | 200, testes aprovados |
| Consultar mapa como ADMIN | 200, testes aprovados |
| Login USER e consulta da sessão | 200, perfil USER |
| Listar contas como USER | 403, teste de permissão aprovado |
| Consultar mapa como USER | 200, testes aprovados |
| Listar dispositivos da conta USER sem sensores | 200, lista vazia |
| Consultar leitura do dispositivo 1 como outra conta USER | 404 |
| Logout e confirmação de sessão encerrada | 200 e 401 |

Na verificação negativa de leitura, o teste original da requisição
espera 200. O resultado 404 foi conferido manualmente como bloqueio
correto de acesso ao dispositivo de outra conta.

O cadastro com localização foi validado pelo painel, mas a nova
requisição de cadastro no Postman ainda não foi executada.

Não foi executada a coleção inteira nem todas as operações de escrita.
Estas evidências não substituem testes físicos do ESP, testes de
concorrência ou verificação de autorização em cada rota individual.
Nenhuma senha, cookie ou token faz parte deste registro.
