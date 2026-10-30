# Infraestrutura FIWARE e MQTT — WineGuard

Modelos preparados a partir dos arquivos e consultas da VM FIWARE em 29/10/2026. **A stack existente funciona, mas este Compose revisado ainda precisa de validação em instalação nova. Não executar `up` ou substituir os arquivos sobre a VM atual.**

## Origem e licença

Base: [fabiocabrini/fiware](https://github.com/fabiocabrini/fiware), de Fábio Henrique Cabrini. A licença MIT original está preservada em [LICENSE](LICENSE). A equipe WineGuard adaptou a configuração MQTT/TLS, ACL, provisionamento, histórico, variáveis e documentação. Essa atribuição se refere à base da infraestrutura; não atribui os demais componentes do projeto ao autor original.

## Serviços e versões

O Compose fixa as imagens por digest informados na consulta às imagens locais da VM. Mosquitto foi identificado em execução como **2.1.2**. A configuração do plugin ACL foi preservada conforme o arquivo em uso nessa versão, sem substituir sua sintaxe por exemplos de outras versões.

| Serviço | Imagem identificada | Função |
|---|---|---|
| `mongo-db-historical` | MongoDB 4.4 | Mongo autenticado usado pelo Orion e pelo STH |
| `mongo-db-internal` | MongoDB 4.4 | Mongo sem autenticação usado pelo IoT Agent, somente na rede Docker |
| `orion` | Orion 3.11.0 | Estado NGSIv2, comandos e notificações |
| `fiware-sth-comet` | `telefonicaiot/fiware-sth-comet` | Histórico das três métricas |
| `mosquitto` | Mosquitto 2.1.2 | MQTT interno e MQTT/TLS do ESP |
| `iot-agent` | `fiware/iotagent-ul` | Tradução UltraLight e integração ao Orion |

O Mongo histórico contém o estado do Orion e as coleções históricas STH; o nome não significa uso exclusivo pelo STH. O PostgreSQL da aplicação Java é outro banco, definido em `backend/`.

## Diferenças em relação à VM observada

- Removido o campo Compose `version` obsoleto; imagens fixadas por digest.
- Senhas Mongo vêm de `.env`; não há senha no comando versionado do Orion.
- MongoDB não publica porta no host. MQTT anônimo 1883 fica somente na rede Docker; não é publicado no host. A porta 9001 não é publicada, pois a configuração observada não define listener WebSocket.
- Orion 1026, IoT Agent 4041 e STH 8666 usam `FIWARE_BIND_ADDRESS`, por padrão `127.0.0.1`. Na EC2 FIWARE, configure o IP privado acessível pela EC2 da API e restrinja o Security Group a essa origem.
- Somente MQTT/TLS 8883 tem publicação externa por padrão. O acesso SSH é administração da VM, não um serviço deste Compose.
- Política `restart: unless-stopped`, logs INFO nos serviços configuráveis e volumes persistentes para dados/logs Mosquitto.
- Arquivos locais privados em `.local/`, com montagem somente leitura; bind mounts não criam diretórios silenciosamente quando faltam arquivos.
- Nome de projeto `wineguard-fiware`: volumes deste modelo não são uma migração automática dos volumes existentes.

Os nomes dos containers e várias portas são iguais aos da VM atual. Uma segunda stack no mesmo host causará conflitos. Qualquer migração real exige planejamento de volumes, credenciais, certificados e interrupção, fora deste procedimento de reprodução.

## Preparação em instalação nova

Procedimento para Linux/Bash com Docker Compose, Python 3, OpenSSL, curl e acesso ao registro das imagens. Requer host compatível com as imagens; a implantação observada usa EC2 x86_64. Não foi validada reprodução em ARM.

Na pasta `infra/fiware/`:

```bash
cp .env.example .env
chmod 600 .env
```

Edite `.env` localmente e preencha `MONGO_ROOT_PASSWORD`, `IOTA_API_KEY` e `MQTT_TLS_HOST`. Use valores próprios. A chave do grupo IoT Agent precisa coincidir com a chave dos tópicos no firmware e com `FIWARE_IOTA_API_KEY` no backend. Usuário/senha MQTT são credenciais diferentes dessa chave.

Use um arquivo confiável compatível com Bash e Compose; valores com caracteres especiais precisam de aspas adequadas. Não reutilize a senha de exemplo de tutoriais. Alterar `.env` não altera a senha de um Mongo já inicializado.

```bash
umask 077
set -a
. ./.env
set +a
python3 scripts/prepare.py
```

O script gera `.local/group.json`, `device.json`, `history-subscription.json`, `mosquitto.acl` e `server.cnf`. Não chama APIs, inicia containers ou altera a stack. Não mostra credenciais. Recusa sobrescrever arquivos sem `--replace`; gere antes de atribuir arquivos ao usuário do broker. JSONs são renderizados estruturalmente, sem concatenar valores secretos em texto JSON.

Para este modelo, chave de grupo e device ID usam letras ASCII, números, hífen ou sublinhado; o usuário MQTT também aceita ponto. O script rejeita caracteres de curinga e novas linhas nos tópicos. Exemplos de IDs: `wine002`, `WineSensor002` e `WineSensor`.

### Senha MQTT e identidade do processo

Defina a imagem fixada e consulte o UID/GID do broker na imagem:

```bash
MQTT_IMAGE=eclipse-mosquitto@sha256:6f8d8a947c506f8a2290ec65cd4bd2bc7cb4d43fb5f6271f861cb013e2ef9797
BROKER_UID=$(docker run --rm --entrypoint id "$MQTT_IMAGE" -u mosquitto)
BROKER_GID=$(docker run --rm --entrypoint id "$MQTT_IMAGE" -g mosquitto)
```

Na instalação nova, crie o arquivo de senhas interativamente. `-c` cria/substitui o arquivo: **não usar sobre um arquivo de contas existente**. A senha é solicitada pelo utilitário, sem `-b` na linha de comando:

```bash
test ! -e .local/mosquitto.passwd
docker run --rm -it --user "$(id -u):$(id -g)" \
  --entrypoint mosquitto_passwd \
  -v "$PWD/.local:/work" "$MQTT_IMAGE" \
  -c /work/mosquitto.passwd "$MQTT_USERNAME"
```

Confirme o resultado do `test` antes do comando seguinte. Para mais dispositivos, crie contas e regras ACL específicas; não conceda `#` indiscriminadamente. O cadastro/provisionamento Java não cria usuários MQTT.

### TLS para um ambiente novo

O firmware deve confiar na CA correspondente ao broker. O certificado do servidor deve conter no SAN o IP ou DNS de `MQTT_TLS_HOST`. O helper gera o tipo IP/DNS correto. Não desative a verificação TLS para contornar incompatibilidade de hostname.

Os comandos abaixo criam uma CA e certificado novos; não substituem os certificados da implantação existente. Execute somente sem material anterior em `.local/pki/` ou `.local/tls/`:

```bash
mkdir -m 700 .local/pki .local/tls
openssl req -x509 -newkey rsa:3072 -sha256 -nodes -days 3650 \
  -subj '/CN=WineGuard Local CA' \
  -addext 'basicConstraints=critical,CA:TRUE' \
  -addext 'keyUsage=critical,keyCertSign,cRLSign' \
  -keyout .local/pki/ca.key -out .local/tls/ca.crt
openssl req -new -newkey rsa:2048 -nodes \
  -keyout .local/tls/server.key -out .local/pki/server.csr \
  -config .local/server.cnf
openssl x509 -req -sha256 -days 365 \
  -in .local/pki/server.csr -CA .local/tls/ca.crt \
  -CAkey .local/pki/ca.key -CAcreateserial -CAserial .local/pki/ca.srl \
  -extfile .local/server.cnf -extensions server_ext \
  -out .local/tls/server.crt
openssl verify -CAfile .local/tls/ca.crt .local/tls/server.crt
```

Execute cada comando somente se o anterior tiver concluído com sucesso. Copie ao firmware apenas a CA pública `ca.crt`; chave privada da CA e do servidor nunca vão para o ESP nem para o Git. A CA privada fica fora da pasta TLS montada no broker. O ESP também precisa de horário válido para validar certificados.

Permita leitura pelo UID/GID consultado; o diretório `.local` permanece privado no host:

```bash
sudo chown "$BROKER_UID:$BROKER_GID" \
  .local/mosquitto.passwd .local/mosquitto.acl \
  .local/tls .local/tls/ca.crt .local/tls/server.crt .local/tls/server.key
sudo chmod 750 .local/tls
sudo chmod 640 .local/mosquitto.passwd .local/mosquitto.acl .local/tls/server.key
sudo chmod 644 .local/tls/ca.crt .local/tls/server.crt
```

Essas permissões são para o host Linux de reprodução. Scripts geradores não têm garantia de ACL de arquivos equivalente no Windows. Backups de `.local` e `.env` precisam de armazenamento privado e controle de acesso.

## Subir e provisionar a instalação nova

Primeiro valide sem imprimir os valores interpolados; depois suba somente no host novo:

```bash
docker compose config --quiet
docker compose up -d
docker compose ps
```

Não use `docker compose down -v` para atualizar. Aguarde os serviços ficarem disponíveis; dependência de inicialização não garante prontidão imediata.

Prepare endereços e cabeçalhos na mesma sessão Bash em que exportou `.env`:

```bash
IOTA_URL="http://${FIWARE_BIND_ADDRESS}:4041"
ORION_URL="http://${FIWARE_BIND_ADDRESS}:1026"
HEADERS=(-H "fiware-service: $FIWARE_SERVICE" -H "fiware-servicepath: $FIWARE_SERVICE_PATH")
curl -fsS --max-time 15 "$ORION_URL/version"
```

Antes de criar qualquer recurso, consulte o estado. Os arquivos de resposta podem conter a chave do grupo e ficam somente em `.local/`, sob o `umask 077` configurado:

```bash
curl -sS --max-time 15 "${HEADERS[@]}" \
  -o .local/services-before.json -w 'Servicos: %{http_code}\n' "$IOTA_URL/iot/services"
curl -sS --max-time 15 "${HEADERS[@]}" \
  -o .local/device-before.json -w 'Dispositivo: %{http_code}\n' "$IOTA_URL/iot/devices/$DEVICE_ID"
curl -sS --max-time 15 "${HEADERS[@]}" \
  -o .local/subscriptions-before.json -w 'Assinaturas: %{http_code}\n' "$ORION_URL/v2/subscriptions?limit=1000"
```

Revise localmente essas respostas. Em instalação nova, a lista de grupos e assinaturas deve estar vazia e o dispositivo deve retornar 404. Se houver recurso equivalente, não execute o POST correspondente: confira o contrato e preserve o recurso. Os comandos seguintes não são um script idempotente nem devem ser rodados automaticamente em cada deploy.

Somente após confirmar a ausência, execute cada criação individualmente:

```bash
curl -sS --max-time 15 "${HEADERS[@]}" -H 'Content-Type: application/json' \
  --data-binary @.local/group.json -o .local/group-response.json \
  -w 'Grupo: %{http_code}\n' "$IOTA_URL/iot/services"
curl -sS --max-time 15 "${HEADERS[@]}" -H 'Content-Type: application/json' \
  --data-binary @.local/device.json -o .local/device-response.json \
  -w 'Dispositivo: %{http_code}\n' "$IOTA_URL/iot/devices"
curl -sS --max-time 15 "${HEADERS[@]}" -H 'Content-Type: application/json' \
  --data-binary @.local/history-subscription.json -o .local/subscription-response.json \
  -w 'Assinatura: %{http_code}\n' "$ORION_URL/v2/subscriptions"
```

O esperado para criação é 201. Pare se houver falha ou resposta inesperada. Após timeout ou erro de rede, consulte o estado antes de repetir: a criação pode ter ocorrido. Não apague recursos ou recrie assinaturas automaticamente para resolver um 409. POSTs não são uma transação distribuída.

## Contratos observados

O dispositivo usa `t → temperature`, `h → humidity`, `l → luminosity`, `cfg → configuration` e comandos `alert`/`configure`. O MQTT de telemetria e comandos usa `/<chave>/<deviceId>/attrs` e `/<chave>/<deviceId>/cmd`. A confirmação do firmware usa **`/ul/<chave>/<deviceId>/cmdexe`**. O helper gera essas três permissões para a conta do ESP.

A assinatura de histórico observada estava ativa, com entidades `idPattern: .*`, tipo `WineSensor`, condição nas três métricas e `notifyOnMetadataChange: true`. Notifica `http://sth-comet:8666/notify` com `attrsFormat: legacy`. Os templates preservam esse contrato; o ID da assinatura antiga não é reutilizado. A assinatura coleta todos os sensores desse tipo no contexto informado; privacidade de usuários é aplicada pela API Java.

Depois de ligar o ESP com CA, usuário, senha, chave e IDs corretos, confirme que TimeInstant avança no Orion e que o STH registra as métricas. Provisionar não produz leituras por si só. As instruções do backend, o contrato de revisão e as evidências físicas estão em [../../backend/README.md](../../backend/README.md) e [../../backend/docs/testes-vercel-esp.md](../../backend/docs/testes-vercel-esp.md).

No backend, configure Orion 1026, STH 8666 e IoT Agent 4041 com os endereços privados acessíveis pela EC2 da API. Banco, Orion, STH e IoT Agent não são APIs públicas de navegação. Para SSH e regras AWS, mantenha apenas as origens necessárias; a publicação Docker em `0.0.0.0` por si só não informa se o Security Group permite acesso externo.

## Verificação e limites desta entrega

Testes offline, sem rede, credenciais reais ou Docker:

```bash
python3 -m unittest discover -s scripts -p 'test_*.py' -v
```

Os sete testes verificam os contratos gerados, tópicos de confirmação, bloqueio de injeção/curingas, chave ausente, SAN IP/DNS e renderização JSON. Não comprovam o carregamento do plugin, as permissões dos arquivos no container, a inicialização das imagens, TLS ou provisionamento real do Compose revisado. A reprodução completa em ambiente separado continua pendente.

### Evidências de 29/10/2026

- Arquivos da infraestrutura consultados no commit `d94121f` deste repositório.
- YAML, três templates JSON, digests, dependências, portas e links locais conferidos no PC; sete testes offline aprovados.
- Na VM FIWARE, o Compose foi baixado para uma pasta temporária e submetido a `docker compose config --quiet`, com valor fictício de senha apenas para interpolação. O comando terminou sem mensagens de erro. Nenhum `up` foi executado.
- Os dois scripts Python e os três templates do mesmo commit foram baixados para outra pasta temporária na VM. Os sete testes passaram: `Ran 7 tests in 0.003s`, `OK`.
- Os testes utilizaram valores fictícios e não iniciaram containers, alteraram volumes, trocaram credenciais, provisionaram recursos ou modificaram os serviços da implantação existente.

Esses resultados validam a estrutura do Compose e os contratos do gerador. Não são evidência de inicialização ou integração da stack revisada. Ainda é necessário reproduzir a instalação nova, incluindo acesso aos arquivos privados pelo broker, TLS e registro de medições no histórico.

A licença, o Compose e a configuração MQTT têm origem identificada. Não foram importados arquivos de senha, certificados da VM, bancos, backups, coleções antigas com possíveis credenciais ou exemplos de firmware que não são a versão final do grupo. Senhas observadas em comandos da implantação original foram substituídas por variáveis; a inclusão dos modelos não altera nem rotaciona as credenciais em uso.
