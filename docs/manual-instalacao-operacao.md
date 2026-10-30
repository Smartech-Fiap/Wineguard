# Manual consolidado de instalação e operação — WineGuard

Este manual orienta a execução e aponta os procedimentos detalhados de cada componente. O protótipo físico usa BME680; por orientação da equipe, a simulação também deve preservar esse sensor. Não há medição de fumaça no escopo.

## 1. Identificar os componentes

| Componente | Função | Manual detalhado |
|---|---|---|
| ESP32, sensores, Nextion, LEDs e buzzer | Medição e atuação local | [Firmware](../firmware/README.md) |
| FIWARE e Mosquitto | Telemetria, estado atual, histórico e comandos | [Infraestrutura](../infra/fiware/README.md) |
| Java 17 e PostgreSQL 16 | API, sessão, autorização e cadastros | [Backend](../backend/README.md) |
| Nginx, TLS e systemd | Publicação e operação da API | [Implantação](../backend/deploy/README.md) |
| React e Vite | Dashboard web | [Frontend](../frontend/README.md) e [requisitos](../frontend/requirementes.txt) |
| Wokwi | BME680/LDR com modelo personalizado ainda pendente | Não há componente BME680 nativo; não confundir BME280 com BME680. |

## 2. Preparar a instalação de software

1. Clonar o repositório integrado: `git clone https://github.com/Smartech-Fiap/Wineguard.git`.
2. Preparar a stack FIWARE seguindo o manual da infraestrutura: variáveis locais, MongoDB, broker, certificados, ACL, IoT Agent e STH-Comet. Os modelos não devem substituir a instalação AWS existente sem planejamento de volumes e credenciais.
3. Provisionar o grupo e o dispositivo, incluindo `temperature`, `humidity`, `luminosity`, `configuration` e os comandos `alert`/`configure`. Criar a assinatura que envia as leituras ao STH-Comet.
4. No backend, configurar as URLs privadas FIWARE, PostgreSQL e chave do grupo usando variáveis locais. Spring Boot não carrega `.env` automaticamente; usar o procedimento manual ou `EnvironmentFile` do serviço.
5. Iniciar PostgreSQL, executar `./mvnw clean package` e iniciar o Java pelo procedimento do backend. No Windows, usar `mvnw.cmd`. Flyway aplica as migrações; não apagar o banco para atualizar a aplicação.
6. Preparar o usuário ADMIN pelo procedimento administrativo documentado. Usuários comuns são criados pelo administrador no painel; não há cadastro público.
7. Em `frontend`, executar `npm ci`, `npm run dev` ou `npm run build`. Para desenvolvimento, ajustar o alvo do proxy no arquivo local conforme o README. Não colocar segredos em variáveis públicas `VITE_*`.
8. Para acesso remoto, usar os modelos Nginx/HTTPS e a publicação Vercel descritos nos manuais. API e banco devem permanecer nas interfaces previstas; não abrir PostgreSQL/MongoDB para o navegador.

## 3. Preparar o hardware físico

O [mapa de pinos](../firmware/README.md) e o sketch definem: BME680 no I²C GPIO 21/22, endereço `0x76`; LDR no GPIO 34; LEDs verde/vermelho nos GPIO 26/25; buzzer no GPIO 27; Nextion pela Serial2 GPIO 16/17.

Antes de energizar, o responsável deve conferir a alimentação de cada módulo, GND comum, níveis lógicos, resistores dos LEDs e eventual estágio de acionamento necessário para buzzer/display. A especificação de tensão dos módulos físicos e seu esquema completo ainda precisam ser fornecidos; não inferir que todo módulo pode receber 5 V ou que a saída do display pode ser ligada diretamente a uma entrada de 3,3 V.

Abra o sketch físico no Arduino IDE, instale o core ESP32 e as bibliotecas indicadas no manual, copie os exemplos `secrets.h.example`/`ca_cert.h.example` para os headers locais e preencha rede, usuário MQTT e CA. Selecione a placa e a porta corretas e grave. Não incluir esses headers locais no Git.

O Nextion exige o projeto `.HMI`/`.TFT` correspondente ao modelo e aos nomes de componentes usados pelo firmware. Esses arquivos e as versões efetivamente usadas de placa/core/bibliotecas ainda devem ser reunidos pelo responsável; este manual não afirma reprodução exata do display sem eles.

## 4. Operação diária

1. Iniciar o laboratório AWS e conferir que as duas EC2, containers e API estão disponíveis. Conferir validade do certificado HTTPS.
2. Energizar o ESP e abrir a serial a 115200 baud para diagnóstico. Aguardar Wi-Fi, horário NTP e conexão MQTT/TLS.
3. Acessar o [dashboard](https://wineguard-peach.vercel.app/), entrar com a conta fornecida pelo administrador e selecionar o dispositivo.
4. Conferir horário e valores de temperatura, umidade e luminosidade. O percentual de luminosidade corresponde a `ADC / 4095 * 100`, sem calibração em lux.
5. Em **Histórico**, selecionar a métrica e percorrer os pontos ou consultar a tabela. O gráfico apresenta até 20 registros nas últimas 24 horas na ordem devolvida pelo STH-Comet.
6. Em **Limites e alertas**, preencher os cinco valores e usar **Salvar e enviar ao ESP**. Os valores ficam registrados no PostgreSQL por revisão. Aguardar a confirmação **Aplicada** antes de tratá-los como ativos.
7. Se o resultado ficar não confirmado, verificar a conexão do equipamento e usar **Verificar novamente**. O reenvio é manual e mantém a mesma revisão; não repetir indiscriminadamente a criação de configurações.
8. Em **Avisos**, acompanhar as ocorrências locais ao navegador. São avisos visuais, registrados com o painel aberto para o dispositivo selecionado; não enviam comandos ao ESP e não substituem o histórico STH ou a sinalização física.
9. Encerrar a sessão ao terminar, especialmente em um computador compartilhado.

## 5. Cadastros e acesso

Ambientes organizam os espaços monitorados, como adega principal e estoque. Cada dispositivo é associado a um ambiente e ao usuário que o cadastra. O cadastro no banco e o provisionamento no IoT Agent são ações distintas.

Um USER consulta e gerencia os próprios sensores; um ADMIN pode consultar todos e criar contas comuns com a localização da vinícola. O mapa compartilhado mostra localizações, sem expor os sensores de outros usuários. Remoção é uma operação explícita e afeta os cadastros/provisionamento; não usar o dispositivo real para demonstrar exclusão.

## 6. Diagnóstico rápido

| Sintoma | Conferência |
|---|---|
| Login/sessão expirou | Entrar novamente; o reinício da API encerra sessões existentes. Não desativar CSRF. |
| Sem leitura recente | ESP, Wi-Fi, NTP, MQTT/TLS, ACL e telemetria no Orion. Valor antigo não comprova transmissão atual. |
| Sem histórico | Assinatura Orion → STH, contexto FIWARE e intervalo consultado. |
| Configuração pendente | Conexão e serial do ESP, comando recebido, tópico `cmdexe` e telemetria `cfg`. Não confundir `configure_status=OK` com a revisão confirmada pela API. |
| BME680 inválido | Alimentação, I²C, endereço e conexões; firmware omite temperatura/umidade inválidas e mantém luminosidade/configuração. |
| `normal` não apaga alerta | O comando limpa apenas o remoto; uma condição local ativa continua até normalizar com histerese. |
| Avisos sumiram em outro dispositivo | O histórico de avisos é local ao navegador, não compartilhado nem gravado no PostgreSQL. |

## 7. Encerramento e preservação

Antes de desligar, registrar a revisão aplicada e salvar o trabalho no Git. No laboratório, seguir o procedimento de encerramento da AWS Academy para evitar consumo desnecessário. Não executar `docker compose down -v` como rotina: isso remove volumes de dados. Na próxima inicialização, conferir serviços, certificados e telemetria recente.
