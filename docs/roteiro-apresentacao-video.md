# Roteiro de vídeo de evidências

A apresentação já foi realizada, conforme informação da equipe. O vídeo também já foi concluído e será enviado diretamente pelo Forms. Este roteiro foi preservado apenas como referência, sem exigir nova gravação ou apresentação. Sugestão de duração: 5–7 minutos; ajustar ao enunciado. As falas abaixo descrevem a solução implementada; as demonstrações devem ser gravadas com o equipamento real. Este documento não é evidência de que o vídeo já foi produzido.

## 1. Problema e proposta — Bruno, cerca de 40 segundos

“A conservação do vinho depende das condições do ambiente. O WineGuard permite acompanhar temperatura, umidade e luminosidade, identificar situações fora da faixa e ajustar os limites do equipamento por um painel web. Nossa proposta reúne monitoramento, histórico e atuação local em uma solução integrada.”

Mostrar a tela inicial com uma leitura recente e o horário de medição. Não apresentar dados antigos como transmissão ao vivo.

## 2. Hardware e diferencial — Arthur, cerca de 1 minuto

“O ESP32 lê o BME680 e o LDR e apresenta o estado no Nextion. A troca do sensor físico e as adaptações de sinalização foram aprovadas pelo professor. O ESP avalia os limites localmente, mantém a configuração salva e usa padrões sonoros diferentes: dois bipes para temperatura, três para umidade e um longo para luminosidade.”

Mostrar a montagem, identificar sensores, LEDs, buzzer e display. Filmar também a caixa/encapsulamento se existente. Demonstrar uma alteração ambiental de cada vez, com método adequado ao componente; não molhar ou aquecer diretamente a placa. Explicar que o percentual do LDR é uma escala relativa, não lux calibrados.

## 3. FIWARE e arquitetura — Artur, cerca de 1 minuto

“O ESP publica telemetria no Mosquitto por MQTT com TLS. O IoT Agent traduz as mensagens para o Orion, que mantém o estado atual. O Orion notifica o STH-Comet, que armazena o histórico no MongoDB. Nossa API consulta esses serviços e entrega os dados ao dashboard. Os comandos percorrem o sentido inverso até o ESP.”

Abrir o [diagrama em camadas](arquitetura-em-camadas.md). Distinguir apresentação, API, integração IoT, persistência e dispositivo. Mostrar a leitura atual no painel sem expor credenciais ou o conteúdo do `.env`.

## 4. Backend e dados — João, cerca de 50 segundos

“O PostgreSQL guarda os cadastros, usuários e revisões de configuração. O histórico de medições fica no STH-Comet, sem duplicação no banco da aplicação. A API autentica a sessão e verifica o proprietário do sensor. Um usuário comum só acessa os próprios dispositivos; o administrador pode gerenciar as contas e visualizar todos.”

Mostrar uma conta USER sem sensores de outro usuário e, se houver tempo, a visão ADMIN. Não gravar a digitação de senhas nem promover usuários por uma rota pública.

## 5. Dashboard e alteração de limites — Bruno e Arthur, cerca de 2 minutos

1. Selecionar o dispositivo real e confirmar o horário da leitura.
2. Abrir o histórico, alternar entre as três métricas e selecionar um ponto para mostrar horário e valor exatos. Abrir a tabela.
3. Abrir **Limites e alertas**, preencher os cinco valores e explicar o botão **Salvar e enviar ao ESP**.
4. Combinar antecipadamente faixas de bancada com Arthur. Registrar o estado pendente e esperar **Aplicada**; se não confirmar, mostrar a situação verdadeira e usar **Verificar novamente**, sem criar várias revisões.
5. Mostrar a revisão ativa na serial/telemetria e o comportamento correspondente no hardware. Não confundir resposta HTTP `202` com confirmação física.
6. Demonstrar uma condição fora da faixa: observar LED/buzzer e o aviso no painel. Quando normalizar, respeitar a histerese do firmware. Mostrar o registro em **Avisos**.
7. Explicar: o histórico de avisos é salvo só neste navegador e enquanto o painel está aberto. O histórico de leituras vem do STH-Comet. São funções distintas.

Se for usar o comando remoto manual, demonstrar `temperature` e depois `normal`. `normal` encerra apenas os alertas remotos; uma condição local ainda anormal continuará sinalizada.

## 6. Encerramento — Artur, cerca de 30 segundos

“O WineGuard integra o equipamento físico, a plataforma FIWARE e um painel autenticado. Como diferenciais, temos configuração remota com confirmação, persistência local no ESP e controle de acesso por proprietário. O código, a arquitetura e os manuais estão organizados no repositório da equipe.”

Mostrar o link do GitHub e o nome dos quatro integrantes. Não afirmar que a simulação local substitui os testes MQTT/TLS ou o hands-on.

## Preparação da gravação

- Conferir enquadramento do display, LEDs e sensor; som do buzzer deve ser audível.
- Capturar a tela com zoom legível e sem senhas, tokens, cookies ou chaves visíveis.
- Testar as duas EC2, o acesso HTTPS e a sessão do painel antes de gravar.
- Preparar as contas de demonstração e os ambientes/dispositivos de teste, sem remover o sensor real.
- Gravar somente comportamentos observados; registrar falhas e refazer a demonstração quando necessário.
- Após gravar, abrir o link como avaliador e confirmar imagem, áudio e permissão de acesso.
- Guardar também fotos da montagem e o comprovante do Forms.

## Se a rede falhar no laboratório

Conferir primeiro Wi-Fi, horário NTP, MQTT e laboratório AWS. O ESP deve continuar avaliando os limites locais. A simulação Wokwi pode explicar a lógica isolada, mas não deve ser apresentada como uma conexão AWS funcionando. Se mostrar um vídeo anterior, identificá-lo explicitamente como gravação.
