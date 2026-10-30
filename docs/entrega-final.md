# WineGuard — pacote de entrega

Atualizado em 30/10/2026. Esta página reúne os materiais para o Microsoft Forms. A equipe informou que a apresentação já foi realizada. Itens marcados como pendentes precisam de evidência real; não representam uma entrega já efetuada.

## Identificação

| Integrante | RM | Responsabilidade principal |
|---|---|---|
| Artur de Novazzi Maia | 572624 | FIWARE, integração final e Express inicial |
| Arthur Pinheiro Germano | 574042 | ESP32 e firmware |
| Bruno Araújo Castro | 572723 | Painel e dashboard |
| João Pedro de Souza | 571437 | Banco de dados e backend Java |

## Links para o Forms

| Material | Endereço ou situação |
|---|---|
| Repositório integrado | https://github.com/Smartech-Fiap/Wineguard |
| Dashboard | https://wineguard-peach.vercel.app/ |
| Código ESP32 | [Sketch físico](../firmware/WineGuard_firmware/WineGuard_firmware.ino) |
| Código dashboard e requisitos | [Frontend](../frontend/README.md) e [requirementes.txt](../frontend/requirementes.txt) |
| Arquitetura em camadas | [Documento](arquitetura-em-camadas.md) |
| Manual de instalação e operação | [Manual consolidado](manual-instalacao-operacao.md) |
| Vídeo | Concluído, conforme confirmação da equipe. Encaminhamento direto pelo Forms; link não incluído na documentação por orientação da equipe. |
| Fotos da montagem/encapsulamento | [Vista frontal e interna do protótipo](evidencias/README.md), fornecidas pela equipe. |
| Interface Nextion | Projeto `.HMI`, `.TFT`, modelo do display e versão do editor ainda não fornecidos. |

## Descrição da solução para o Forms

WineGuard é uma plataforma acadêmica de monitoramento ambiental para vinícolas. Um ESP32 mede temperatura e umidade com BME680 e luminosidade com LDR, mostra o estado no display Nextion e sinaliza anomalias por LEDs e padrões sonoros distintos. A comunicação com a infraestrutura usa MQTT sobre TLS. O IoT Agent UltraLight traduz a telemetria para o Orion Context Broker; o STH-Comet mantém o histórico com persistência MongoDB.

O dashboard React consulta as informações por uma API Java autenticada, permite cadastrar ambientes e dispositivos, provisionar e remover sensores no FIWARE, visualizar gráficos e definir os cinco limites em um único fluxo. A configuração é registrada no PostgreSQL com revisão e só é apresentada como aplicada após confirmação do ESP. O firmware conserva a configuração em NVS e continua avaliando as condições locais sem depender de o painel estar aberto.

O sistema inclui perfis ADMIN/USER, controle de acesso por proprietário do dispositivo e mapa compartilhado de vinícolas sem exposição de sensores de outros usuários. A luminosidade é apresentada como percentual relativo, sem calibração em lux. Os avisos visuais e seu histórico recente são locais ao navegador e só registram ocorrências observadas com o painel aberto; não constituem um serviço de auditoria contínua no banco.

A equipe informou aprovação do professor para o BME680 em substituição ao DHT-11 físico, uso dos LEDs verde/vermelho e alteração da lógica de atuação local com controle remoto pelo painel. O escopo da dispensa de piscamento ainda precisa ser confirmado: o firmware físico atual usa LED vermelho fixo. Não afirmar atendimento literal dessa parte sem confirmar a adaptação.

## Conferência antes do envio

- [x] Vídeo concluído, conforme confirmação da equipe. Enviar diretamente pelo Forms e conferir a permissão de acesso.
- [x] Fotos frontal e interna da montagem/caixa recebidas e documentadas. O esquema elétrico continua sendo um material distinto.
- [ ] Acrescentar os arquivos Nextion e versões de placa/core/bibliotecas realmente utilizadas.
- [ ] Confirmar com o professor a adaptação do LED fixo versus piscante.
- [x] Apresentação realizada, conforme confirmação da equipe. O roteiro permanece como referência; o vídeo também já foi concluído.
- [ ] Conferir os links do GitHub e do painel e testar o login com uma conta de demonstração.
- [ ] Manter as duas EC2 e o laboratório ativos durante o hands-on; conferir HTTPS e telemetria recente.
- [ ] Preencher e enviar o Forms pela conta do integrante responsável; guardar o comprovante.

Não colocar senha de demonstração, senha MQTT, tokens ou chaves privadas no Forms público, no vídeo ou no repositório. O acesso ao painel para avaliação deve ser combinado com o professor por canal apropriado.
