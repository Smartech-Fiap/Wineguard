# Display Nextion — WineGuard

Arquivos originais fornecidos pela equipe em 30/10/2026, preservados sem alterações.

| Arquivo | Uso |
|---|---|
| [WINEGUARD.HMI](WINEGUARD.HMI) | Projeto editável da interface no Nextion Editor. |
| [WINEGUARD.tft](WINEGUARD.tft) | Interface compilada para transferência ao display compatível. Não é o firmware do ESP32. |
| [manifest.json](manifest.json) | Tamanhos, SHA-256, resolução e contrato de comunicação observado. |

## Contrato com o firmware

O projeto contém a página `page0` com resolução de **480 × 320** e os cinco componentes usados pelo [sketch](../WineGuard_firmware/WineGuard_firmware.ino):

| Componente | Conteúdo atualizado pelo ESP |
|---|---|
| `tTemp` | Temperatura em C ou ERRO. |
| `tUmid` | Umidade em %. |
| `tLuz` | Luminosidade relativa em % da escala ADC; não é lux. |
| `tRede` | Estado Wi-Fi e MQTT. |
| `tEstado` | NORMAL, alertas ou FALHA SENSOR; fundo verde/vermelho. |

A comunicação do sketch é **9600 baud, 8N1**, pela `Serial2`: RX do ESP no GPIO 16 e TX no GPIO 17. As instruções são ASCII terminadas por três bytes `0xFF`; não é necessária biblioteca Nextion no ESP. Ligar TX de um lado ao RX do outro, com GND comum e níveis elétricos compatíveis com cada módulo. A resolução não identifica sozinha o modelo físico do display.

## Editar e conferir

1. Instalar o Nextion Editor a partir do fabricante e abrir uma cópia de `WINEGUARD.HMI`.
2. Conferir o modelo selecionado e a orientação nas configurações do projeto contra o display físico. O modelo exato e a versão do editor usada pela equipe ainda não foram confirmados.
3. Conferir os nomes dos cinco componentes. Renomeá-los exige atualizar também o sketch.
4. Usar Compile e Debug para verificar o projeto e a visualização. Caso o editor converta o formato do HMI, preservar os arquivos originais desta pasta.
5. Para gerar um novo TFT, usar a saída de TFT disponível na versão do editor instalada. Registrar modelo e versão utilizados; não substituir o arquivo original sem conferir o resultado no equipamento.

Referência do fabricante: [introdução ao Nextion Editor](https://itead.cc/nextion/nextion-editor%EF%BC%9Aa-basic-introduction/).

## Carregar o TFT por microSD

O procedimento abaixo é para o display Nextion compatível, não para o ESP32:

1. Conferir modelo, resolução e orientação do display antes de gravar. Não usar o arquivo em outro modelo apenas porque tem a mesma resolução.
2. Preparar um microSD compatível, formatado em FAT32, com somente `WINEGUARD.tft` na raiz. Preservar dados existentes antes de formatar.
3. Desligar o display e inserir o cartão.
4. Alimentar o display conforme sua especificação e aguardar a conclusão da atualização, sem interromper a energia.
5. Desligar, remover o cartão e ligar novamente.
6. Conectar ao ESP configurado e verificar atualização dos campos e estado de rede.

O fabricante descreve o carregamento por microSD em [seu exemplo de integração](https://itead.cc/nextion/how-to-use-nextion-to-work-with-raspberry-pi/). Confirmar também os requisitos específicos do cartão e da alimentação no manual do modelo utilizado. Outra opção é Upload pelo Nextion Editor com interface serial compatível; o processo e o cabeamento são distintos da gravação do ESP.

## O que foi verificado

- Leitura local do HMI: uma página 480 × 320 e os cinco nomes de componentes esperados pelo firmware.
- Verificação de integridade interna do TFT com `nextion-tools` 0.1.1: aprovada.
- Hashes SHA-256 das cópias iguais aos arquivos recebidos.

Essas verificações não substituem compilação no editor oficial nem gravação e teste no display físico. Não foi afirmada equivalência entre o TFT fornecido e uma nova compilação do HMI. Modelo exato, versão do editor e teste de reprodução no hardware continuam a confirmar com o responsável.
