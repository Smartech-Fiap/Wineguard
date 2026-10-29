# Configuração de limites do ESP

O backend registra uma configuração completa, envia o comando pelo Orion
e acompanha a configuração ativa publicada pelo ESP.

Salvar limites em `/api/devices/{id}/limits` apenas persiste valores por
métrica. Essa operação não envia configurações ao dispositivo.

## Rotas

As rotas exigem autenticação por sessão.
O POST também exige o token CSRF atual.

### Enviar

POST /api/devices/{deviceId}/configuration

`deviceId` é o identificador numérico do banco.

```json
{
  "temperatureMin": 15,
  "temperatureMax": 29,
  "humidityMin": 40,
  "humidityMax": 82,
  "luminosityMax": 3500
}
```

Todos os campos são obrigatórios.

- Temperatura: de -20 a 60 °C, até uma casa decimal.
- Umidade: de 0 a 100%, até uma casa decimal.
- Intervalo de temperatura: maior que 2 °C.
- Intervalo de umidade: maior que 6 pontos percentuais.
- Luminosidade máxima: inteiro de 200 a 4095 ADC, não lux.

O backend gera uma revisão crescente por dispositivo.
A resposta 202 retorna o registro inicialmente em PENDING.
Ela não comprova a aplicação física dos limites.

### Consultar

GET /api/devices/{deviceId}/configuration

Retorna o registro mais recente e consulta o Orion para reconciliar
a confirmação do ESP.

Retorna 404 quando o dispositivo não existe ou ainda não possui
configuração registrada; consulte o detalhe da resposta.

## Estados

- PENDING: aguardando confirmação.
- APPLIED: telemetria do ESP corresponde à revisão e aos cinco valores.
- REJECTED: resposta do ESP rejeitou a mesma revisão.
- UNCONFIRMED: prazo de 30 segundos transcorrido sem confirmação.

A atualização por prazo ocorre durante a consulta à API.
Não existe tarefa periódica de reconciliação nesta implementação.

Uma configuração UNCONFIRMED pode passar para APPLIED ou REJECTED
quando chegar evidência compatível em uma consulta posterior.

Uma resposta positiva de execução, sem telemetria correspondente,
não é suficiente para marcar APPLIED.

`confirmedAt` indica quando o backend registrou a confirmação,
não necessariamente o instante exato da execução no ESP.

## Protocolo do dispositivo

Comando recebido pelo ESP:

```text
wine002@configure|v1,revision,tempMin,tempMax,humidityMin,humidityMax,lightMax
```

Telemetria publicada no atributo `cfg`, mapeado pelo IoT Agent
para `configuration` do tipo Text:

```text
v1,revision,tempMin,tempMax,humidityMin,humidityMax,lightMax
```

Respostas de execução:

```text
wine002@configure|v1,revision,applied
wine002@configure|v1,revision,already_applied
wine002@configure|v1,revision,rejected,motivo
```

O resultado fica em `configure_info` no Orion.

Motivos reconhecidos:

- invalid_values
- stale_revision
- revision_conflict
- storage_error
- invalid_format
- unsupported_version

`configure_status: OK` indica processamento da resposta pelo
IoT Agent, não aprovação da configuração pelo ESP.

## MQTT e disponibilidade

Para o dispositivo wine002:

- Telemetria: /vinheria2026/wine002/attrs
- Comandos: /vinheria2026/wine002/cmd
- Confirmações: /ul/vinheria2026/wine002/cmdexe

O usuário MQTT precisa de permissão para publicar telemetria e
confirmações e assinar o tópico de comandos.

Os comandos são enviados sem retenção.
Nesta configuração, não se deve presumir entrega enquanto o ESP
estiver desconectado.

Não existe reenvio automático. Há uma rota explícita para recuperar a mesma revisão, descrita abaixo.

Após timeout, erro de rede ou 502, consulte o estado antes de qualquer
nova tentativa. O registro pode ter sido criado e o comando enviado.

Novos envios são bloqueados enquanto houver registro PENDING ou
UNCONFIRMED.

A recuperação operacional deve preservar a revisão e os valores
originais. Não apague registros nem crie novas revisões para contornar
um resultado indefinido.

## Habilitação

```dotenv
FIWARE_CONFIGURATION_ENABLED=false
```

O padrão é desabilitado. Habilite somente após preparar o firmware,
o cadastro do IoT Agent e a ACL do broker.

Não versione o arquivo `.env` real.

## Compatibilidade HTTP

O Orion instalado exige Content-Length em requisições com corpo.

O cliente Spring utiliza HTTP/1.1 e buffering para POST, PUT e PATCH.
O teste diagnóstico encontrou HTTP 411 com envio em streaming e
HTTP 204 com tamanho conhecido; após a correção, o cliente Spring
também recebeu 204.

## Validação realizada

- Compilação do backend concluída.
- Rota sem sessão retornou 401.
- Envio desabilitado retornou 503 sem criar registro.
- Oito verificações dos leitores de mensagens passaram em JShell.
- Comando de revisão 0 foi rejeitado pelo ESP sem alterar os limites.
- Confirmação MQTT chegou ao Orion.
- Revisão 1 foi aplicada e reconciliada como APPLIED pela API.
- Repetição da revisão 1 retornou already_applied.
- POST pela API criou a revisão 2 e respondeu 202.
- Com o ESP desconectado, a revisão 2 permaneceu UNCONFIRMED.
- Após reconexão, o reenvio explícito da revisão 2 pelo painel foi confirmado como APPLIED.

Os limites usados nesses testes são de bancada, não uma aprovação
das faixas finais para conservação de vinhos.

## Validações pendentes

- Validar concorrência entre transações reais do PostgreSQL.
- Ampliar a automação dos cenários físicos e de integração conforme necessário.

A recuperação da revisão 2, a manutenção da configuração após o reinício solicitado e os testes do dashboard publicado foram concluídos nesta rodada.

## Recuperação explícita da mesma revisão

POST /api/devices/{deviceId}/configuration/{revision}/retry

Exige sessão autenticada e CSRF. Não recebe novos valores no corpo.

- Somente a revisão mais recente pode ser recuperada.
- Se ela já estiver APPLIED, retorna 200 sem enviar comandos.
- Configurações REJECTED não podem ser reenviadas.
- Antes do reenvio, o backend consulta o Orion.
- Falha nessa consulta interrompe a operação, sem enviar comandos.
- A configuração informada pelo ESP precisa ter formato reconhecido.
- Uma confirmação compatível recebida nessa consulta evita novo envio.
- Revisão igual com valores divergentes, ou revisão superior no ESP,
  bloqueia o reenvio para reconciliação.
- O reenvio exige estado UNCONFIRMED após o prazo de espera.
- Uma tentativa em andamento bloqueia outra reserva.
- Uma reserva interrompida pode ser recuperada após 60 segundos.
- Há intervalo mínimo de 30 segundos entre tentativas.
- A comunicação acontece fora da transação do banco.

Resposta 202 indica reenvio da mesma revisão em estado PENDING.
A data original createdAt e os valores são preservados.
O prazo de confirmação passa a usar a última tentativa registrada.
A resposta 202 não comprova aplicação física.

Conflitos retornam 409. Dispositivo inexistente retorna 404.
Falha de comunicação com FIWARE retorna 502; consulte o estado antes
de qualquer nova tentativa. Não repita automaticamente o POST.

A migração V5 acrescenta attempt_count, last_attempt_at e
dispatch_lease_until. Esses campos são internos ao banco e não foram
acrescentados ao contrato JSON da resposta.
O contador cobre somente tentativas registradas pelo novo mecanismo;
não reconstrói os envios antigos feitos pelo JShell.

Validação realizada nesta etapa: 45 testes unitários aprovados,
migração aplicada, saúde UP e bloqueios HTTP 403, 401, 409 e 404
verificados sem registrar tentativas.
Os testes usam simulações para as regras de serviço; a concorrência
entre transações reais do PostgreSQL ainda não foi validada.
O reenvio real da revisão 2 foi executado após a reconexão do ESP. O Orion recebeu configuration=v1,2,15.0,29.0,40.0,82.0,3500, configure_info=v1,2,applied e configure_status=OK. Após o reinício solicitado, o Serial continuou publicando a revisão 2; o painel também manteve APPLIED após recarga.
