# Simulação WineGuard — BME680 e LDR

Por orientação da equipe em 30/10/2026, a simulação deve preservar o BME680 e o LDR do projeto físico. A variante DHT-22 foi retirada da entrega; o [sketch físico](../WineGuard_firmware/WineGuard_firmware.ino) permanece inalterado.

## Situação

Não há simulação BME680 concluída nem link público validado nesta versão. O Wokwi não oferece esse sensor nativamente. Sua [API de componentes personalizados](https://docs.wokwi.com/chips-api/getting-started) permite criar modelos adicionais, mas esse modelo ainda precisa ser desenvolvido e testado.

Não usar BME280 ou DHT com o nome BME680: possuem interfaces e comportamento diferentes. Um modelo adequado deve funcionar com a biblioteca `Adafruit_BME680`, comunicação I²C em `0x76` e pinos SDA/SCL 21/22 usados pelo firmware. A variante deve ser identificada como simulação, sem alegar fidelidade elétrica ou equivalência ao teste do equipamento físico.

O escopo do WineGuard usa temperatura e umidade do BME680; gás/fumaça não faz parte do projeto. O modelo deverá preservar o contrato de leituras e a lógica de limites, comandos, revisão e confirmação. Também será necessário distinguir o que é simulado localmente do que realmente passa por MQTT/TLS, FIWARE e API.

A apresentação já foi realizada, conforme informação da equipe. O item Wokwi e o vídeo são materiais de entrega distintos e continuam no [checklist final](../../docs/entrega-final.md).
