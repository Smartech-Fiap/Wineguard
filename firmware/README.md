# Firmware ESP32 — WineGuard

Código importado de [arthurgermano69/WineGuard_Firmware](https://github.com/arthurgermano69/WineGuard_Firmware), commit [`f47aa9b`](https://github.com/arthurgermano69/WineGuard_Firmware/commit/f47aa9b36c41afa1e62cf45776e24330c830e153), com histórico preservado. A organização no repositório final adiciona modelos locais e instruções; não altera a lógica de controle importada.

O ESP32 lê temperatura/umidade do BME680 e o sinal analógico do LDR, mostra o estado no Nextion, mantém alertas locais e recebe alertas/configurações via MQTT/TLS. Medição de fumaça não faz parte do projeto. O aquecedor de gás do BME680 está desativado no código.

## Arquivos

Abra [WineGuard_firmware/WineGuard_firmware.ino](WineGuard_firmware/WineGuard_firmware.ino) no Arduino IDE. A pasta tem o mesmo nome do arquivo principal, conforme a [especificação de sketches do Arduino](https://docs.arduino.cc/arduino-cli/sketch-specification/).

- `WineGuard_firmware.ino`: firmware.
- `secrets.h.example`: modelo de Wi-Fi e autenticação MQTT.
- `ca_cert.h.example`: modelo do certificado público da CA.
- `secrets.h` e `ca_cert.h`: arquivos criados localmente e ignorados pelo Git.

Os arquivos `.HMI` e `.TFT` do display não estavam no repositório de origem e ainda precisam ser fornecidos. Eles não foram gerados ou substituídos por arquivos fictícios nesta integração.

## Placa e dependências

O código foi escrito para ESP32 com `Serial2`, ADC de 12 bits e Preferences/NVS. O nome exato da placa selecionada, a versão do pacote ESP32 e as versões de bibliotecas usadas pelo responsável **ainda não foram informados**. Não há FQBN ou perfil de compilação fixado e validado nesta entrega.

No Arduino IDE, instale o suporte ESP32 da Espressif e as bibliotecas necessárias aos includes:

| Include | Fornecimento |
|---|---|
| `Wire.h`, `WiFi.h`, `WiFiClientSecure.h`, `Preferences.h`, `time.h` | Core/bibliotecas do ambiente ESP32 |
| `Adafruit_Sensor.h` | Adafruit Unified Sensor |
| `Adafruit_BME680.h` | Adafruit BME680 Library e suas dependências declaradas |
| `PubSubClient.h` | PubSubClient |

Use o gerenciador de bibliotecas e confira as dependências da versão escolhida. Referências: [Adafruit BME680](https://github.com/adafruit/Adafruit_BME680) e [PubSubClient](https://github.com/knolleary/pubsubclient). As versões atuais desses projetos não comprovam quais versões foram utilizadas no ESP testado; a confirmação com o responsável continua pendente.

Não é usada uma biblioteca Nextion no sketch: as instruções são enviadas pela UART como texto, terminadas por três bytes `0xFF`.

## Sinais definidos no código

| Componente/sinal | Pino ESP32 ou configuração |
|---|---|
| LED verde | GPIO 26 |
| LED vermelho | GPIO 25 |
| Buzzer | GPIO 27 |
| LDR, entrada ADC | GPIO 34 |
| BME680 SDA | GPIO 21 |
| BME680 SCL | GPIO 22 |
| Endereço I2C do BME680 | `0x76` |
| RX do ESP para receber TX do Nextion | GPIO 16 |
| TX do ESP para enviar ao RX do Nextion | GPIO 17 |
| UART Nextion | 9600 baud, 8N1 |
| Monitor Serial | 115200 baud |

Essa tabela descreve os sinais do software, não um esquema elétrico completo. Modelo exato dos módulos, alimentação, compatibilidade dos níveis lógicos, resistores do LDR/LEDs e circuito de acionamento do buzzer precisam ser confirmados no esquema da equipe. Não foram inferidos valores de componentes ausentes.

O display precisa dos objetos `tTemp`, `tEstado`, `tUmid`, `tLuz` e `tRede`. As flags `TEM_TUMID`, `TEM_TLUZ` e `TEM_TREDE` estão em `true`; ajuste somente se o projeto real do display não tiver esses campos. O indicador de luminosidade do display converte ADC para uma porcentagem de escala, não uma medição física calibrada.

## Configurar antes de compilar

1. Copie os dois arquivos `.example` para os respectivos nomes `.h`, dentro da pasta `WineGuard_firmware/`.
2. Preencha `WIFI_SSID`, `WIFI_SENHA`, `MQTT_USER` e `MQTT_SENHA` somente no `secrets.h` local.
3. Substitua o PEM do `ca_cert.h` pela CA pública que assinou o certificado do broker. O marcador do exemplo não é um certificado válido.
4. Confira o bloco Rede e MQTT no `.ino`: `MQTT_HOST`, `MQTT_PORTA`, `DEVICE_ID`, `CLIENT_ID` e os três tópicos. As constantes importadas apontam para o ambiente testado; adapte-as conjuntamente para outra instalação.
5. Confirme placa, porta, core e bibliotecas com o responsável antes de compilar/gravar. Registre essas versões na entrega quando estiverem disponíveis.

O certificado do servidor deve corresponder ao IP/DNS usado em `MQTT_HOST`. O ESP aguarda horário NTP válido antes de conectar, usa `setCACert(CA_CERT)` e não desativa a validação TLS. O exemplo de CA não contém chave privada. Não coloque `server.key` ou `ca.key` no ESP ou no Git.

Cada ESP precisa de um Client ID único. A chave e o device ID presentes nos tópicos devem corresponder ao provisionamento no IoT Agent. Essa chave de grupo não substitui usuário/senha MQTT. Os arquivos reais de senha, ACL e TLS ficam sob administração da infraestrutura.

Em outra instalação, mantenha consistentes firmware, [modelos FIWARE/MQTT](../infra/fiware/README.md) e variáveis do backend. O provisionamento do IoT Agent não cria automaticamente a conta ou ACL do broker.

## Telemetria e comandos

O firmware publica a cada 10 segundos, sem retenção, em `/<chave>/<deviceId>/attrs`:

```text
t|26.8|h|81|l|99|cfg|v1,2,15.0,29.0,40.0,82.0,3500
```

Temperatura usa °C, umidade usa %, luminosidade é ADC bruto de 0 a 4095. Se o BME680 estiver inválido, a telemetria omite temperatura e umidade e continua enviando LDR/configuração. A revisão `0` indica os padrões locais iniciais; `cfg` informa a configuração ativa, não apenas uma solicitação recebida.

O tópico de comandos é `/<chave>/<deviceId>/cmd`. O parser aceita o prefixo do IoT Agent, por exemplo:

```text
wine002@alert|temperature
wine002@alert|normal
wine002@configure|v1,2,15.0,29.0,40.0,82.0,3500
```

Alertas aceitos: `temperature`, `humidity`, `luminosity` e `normal`. Comandos desconhecidos são ignorados. Alertas remotos expiram em 10 minutos; `normal` limpa somente os remotos. Alertas locais persistem enquanto sua condição estiver ativa e usam histerese.

A resposta de configuração é publicada sem retenção em **`/ul/<chave>/<deviceId>/cmdexe`**. Exemplo:

```text
wine002@configure|v1,2,applied
```

O backend interpreta também `already_applied` e rejeições com motivo. O contrato completo e as regras de reenvio estão em [backend/docs/configuracao-do-esp.md](../backend/docs/configuracao-do-esp.md). O backend reserva e envia revisões; não crie manualmente uma revisão concorrente à API durante os testes.

## Persistência e limites

Preferences usa namespace `wineguard`, chave `cfg` e um registro com CRC32. O código só muda a configuração ativa depois de verificar a gravação. Mesmo número de revisão com valores iguais responde `already_applied`, sem nova gravação; valores diferentes na mesma revisão são rejeitados. Na leitura de um registro inválido, usa os padrões do perfil.

O perfil padrão do arquivo é `PERFIL_ADEGA=0` (bancada): 15–29 °C, 40–82% e máximo de 3500 ADC. As histereses são fixas no código. Trocar o perfil não substitui automaticamente uma configuração válida já persistida. Não apague NVS apenas para repetir um teste sem planejar a reconciliação com as revisões do backend.

`SIMULAR_FALHA_NVS` e `SIMULAR_FALHA_CONFIRMACAO` devem ficar `false` na operação normal. Uma falha de publicação da confirmação não desfaz a configuração já salva: a telemetria periódica permite reconciliar o estado.

Os padrões do perfil não representam aprovação das faixas para conservação de vinhos. A API pode impor validação mais restritiva que a do perfil de bancada do firmware.

## Evidências e pendências

Na integração física de 29/10/2026, a equipe confirmou telemetria atual no Orion, alerta remoto de temperatura e encerramento com `normal`. O reenvio da revisão 2 foi reconciliado como aplicado; após o reinício solicitado, o Serial publicou revisão 2 e o Orion informou `v1,2,applied`. O painel manteve o estado após recarga. Registro completo: [testes Vercel/ESP](../backend/docs/testes-vercel-esp.md).

Essas evidências são do equipamento testado; não constituem comparação do binário gravado com este commit nem uma recompilação desta cópia. A revisão do repositório confirmou o contrato e a ausência de `secrets.h`/chaves privadas no histórico importado; não executou compilação ESP32 nem novos testes físicos. As mudanças posteriores à importação são organização, exemplos, documentação e atualização de comentário, preservando a lógica do `.ino`.

Ainda é necessário obter:

- Placa/FQBN, versão do core ESP32, bibliotecas e configuração de compilação realmente utilizadas.
- Modelo e esquema elétrico completo dos módulos.
- Projeto `.HMI` e arquivo `.TFT` do Nextion, com sua versão/modelo.
- Compilação reproduzível a partir do repositório e registro da gravação/teste correspondente.

Não há suíte automatizada do firmware incluída no repositório de origem. Os testes Java e de infraestrutura verificam seus próprios contratos; não devem ser apresentados como testes unitários ou compilação deste sketch.
