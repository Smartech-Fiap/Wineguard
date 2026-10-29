# Validação manual — Vercel e ESP32

Data: 29/10/2026.

Painel: https://wineguard-peach.vercel.app/

Testes executados manualmente pela equipe, com API AWS e ESP32 ativos.
Este registro não representa uma suíte automatizada nem um teste de carga.

## Painel publicado

- Login ADMIN e carregamento do WineGuard 002: aprovados.
- Recarga da página preservando a sessão: aprovada.
- Logout seguido de recarga permanecendo na tela de login: aprovado.
- Conta USER visualizando o mapa compartilhado: aprovado.
- Administração e WineGuard 002 ocultos para a conta USER: aprovado.
- Salvamento do limite de temperatura com os valores existentes: aprovado.
- Resposta exibida: "Limite de temperatura salvo no backend."

As verificações HTTP sem autenticação também confirmaram:
página inicial 200; sessão, dispositivos e mapa 401; CSRF 200;
rota antiga de sensores 404; respostas da API sem cache.

## Alerta remoto

Pelo painel publicado, o ADMIN enviou temperature ao WineGuard 002.

A equipe confirmou o alerta no display e o acionamento do LED e buzzer.
Em seguida, o comando normal encerrou o alerta remoto.

Esse teste não avaliou simultaneamente um alerta local persistente.
O comando normal não deve cancelar uma condição local ainda ativa.

## Recuperação da configuração

Estado inicial: revisão 2 UNCONFIRMED no painel, com ESP publicando
a revisão 1.

Valores solicitados, de bancada:

- Temperatura mínima: 15 °C.
- Temperatura máxima: 29 °C.
- Umidade mínima: 40%.
- Umidade máxima: 82%.
- Luminosidade máxima: 3500 ADC bruto.

Foi acionado "Reenviar mesma revisão", sem criar nova revisão.
O painel passou a indicar configuração aplicada.

Foi solicitado o reinício do ESP sem reenvio da configuração.
O Monitor Serial apresentado após essa etapa mostrou revisão 2,
reconexão MQTT com certificado validado e publicação de:

configuration = v1,2,15.0,29.0,40.0,82.0,3500

Consulta posterior ao Orion:

- TimeInstant: 2026-10-29T23:33:52.272Z.
- configuration: v1,2,15.0,29.0,40.0,82.0,3500.
- configure_info: v1,2,applied.
- configure_status: OK.

Após recarregar o painel, a revisão 2 continuou indicada como aplicada.
A evidência combina telemetria, resposta do comando e reconciliação
da API; o status OK isolado não comprova aplicação dos valores.

## Limites da validação

Os parâmetros usados são de bancada, não faixas aprovadas para vinhos.
O teste de reinício não comprova resistência a falhas durante gravação,
desgaste da NVS ou persistência por longos períodos sem alimentação.

Não foram avaliados nesta rodada concorrência real entre transações,
carga, todas as operações Postman ou todos os cenários físicos de falha.
