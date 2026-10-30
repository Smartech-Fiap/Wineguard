# Revisão de aderência ao Check Point

Atualização: 30/10/2026. Base: enunciado fornecido pela equipe, código do repositório integrado e evidências manuais registradas. Esta matriz não atribui nota nem substitui a avaliação do professor.

## Adaptações aprovadas, segundo a equipe

- BME680 no lugar do DHT-11 do protótipo físico.
- LEDs verde/vermelho no lugar do LED azul.
- Alteração da lógica de acionamento: controle local no ESP, com alertas e configuração remotos pelo painel.

A informação de aprovação foi fornecida nesta revisão. O [pacote de entrega](entrega-final.md) descreve essas adaptações e diferenciais conforme as informações da equipe. Não se deve presumir que elas dispensam Wokwi, vídeo ou outros itens do enunciado.

O piscamento merece confirmação separada: o código atual mantém o LED vermelho aceso durante o alerta; não implementa alternância temporizada para piscar. A confirmação das mudanças de cor e acionamento não especificou expressamente a dispensa do piscamento.

## Critérios com peso

| Critério | Peso | Situação observada | Evidência ou ação restante |
|---|---:|---|---|
| Encapsulamento | 10% | Não confirmado | Solicitar evidência da montagem/caixa e confirmar se o critério se refere ao acabamento físico. A separação de classes no código não comprova encapsulamento do protótipo. |
| Diferencial | 10% | Implementações presentes | BME680 e Nextion, autenticação/perfis, propriedade de sensores, mapa, MQTT/TLS e configuração com revisão, NVS e recuperação. Destacar somente o que funciona e o que foi aprovado. |
| Manual de software e hardware | 10% | Parcial | Software e implantação documentados; completar versões de placa/core/bibliotecas, esquema elétrico, componentes e arquivos Nextion. O manual consolidado de operação já foi preparado. Confirmar o manual seguindo os passos em outro ambiente. |
| Frontend: cadastro, gráficos e triggers | 20% | Implementado com adaptações informadas | Cadastro, listagem, provisionamento, remoção, histórico STH, fluxo único de limites/configuração completa, histórico selecionável e avisos locais ao navegador. A avaliação de anomalias é local no ESP; envio manual remoto no frontend. Explicar essa diferença aprovada e confirmar a exigência de piscamento. |
| GitHub: README, arquitetura em camadas e códigos | 10% | Documentado | Componentes, READMEs, [arquitetura em camadas](arquitetura-em-camadas.md) e [nomes/RMs/responsabilidades da equipe](../README.md#equipe-e-responsabilidades) presentes. Conferir os links antes do envio da entrega. |
| Hands-on e pitch | 40% | Apresentação realizada, conforme informação da equipe | A equipe informou que a apresentação já foi realizada. Reunir vídeo, fotos e comprovantes quando disponíveis; esta revisão não informa nota ou resultado da avaliação. |

## Itens de entrega no Forms

| Exigência | Situação no repositório |
|---|---|
| Link GitHub com `.ino` | Presente: [sketch ESP32](../firmware/WineGuard_firmware/WineGuard_firmware.ino). |
| Nome dos integrantes | Nomes completos, RMs e responsabilidades confirmados pela equipe no README: Artur de Novazzi Maia — 572624; Arthur Pinheiro Germano — 574042; Bruno Araújo Castro — 572723; João Pedro de Souza — 571437. |
| Descrição completa da solução | READMEs e [descrição consolidada da solução](entrega-final.md) com adaptações informadas como aprovadas. |
| Arquitetura em camadas | [Documento consolidado](arquitetura-em-camadas.md) com camadas, pacotes Java, persistência, fluxos, acesso, implantação e referências ao código. |
| Manual de instalação/operação de software e hardware | [Manual consolidado](manual-instalacao-operacao.md) pronto, com links aos procedimentos detalhados. Reprodução exata do hardware/display ainda depende das versões, esquema e arquivos Nextion. |
| Dashboard comentado | Comentários adicionados em sessão/CSRF, isolamento de respostas, polling, adaptação Orion/STH, reenvio e confirmação do ESP, limites assíncronos, mapa e tratamento de senhas. Explicam decisões sem alterar a lógica. |
| Arquivo de requisitos solicitado | [frontend/requirementes.txt](../frontend/requirementes.txt) documenta o ambiente e os comandos com o nome do enunciado. As dependências React/Node continuam definidas por `package.json` e `package-lock.json`; não foram inventadas dependências Python. |
| Wokwi com LDR e DHT-22 | A equipe solicitou manter BME680 e LDR também na simulação. Wokwi não oferece BME680 nativo; modelo personalizado e link público permanecem pendentes. A variante DHT foi retirada da entrega. |
| Vídeo de evidências | [Roteiro de vídeo e hands-on](roteiro-apresentacao-video.md) pronto. A equipe informou em 30/10 que ainda precisa preparar vídeo, fotos e arquivos Nextion. |
| Envio do Forms | Não comprovado por esta revisão. Conferir todos os links e campos antes do envio. |

## Comportamento de alerta: requisito original e implementação

O enunciado original exige acionamento remoto pelo frontend, LED azul piscando até a normalização e sons distintos por anomalia. As alterações de sensor, cores e lógica de acionamento foram informadas como aprovadas pela equipe.

No código atual:

- O frontend registra e envia os cinco limites por revisão e permite enviar comandos manuais. Há comparação visual de leituras recentes com limites confirmados e histórico de avisos local ao navegador; esses avisos não emitem comandos automaticamente.
- O ESP avalia localmente temperatura, umidade e LDR com histerese. Isso permite controle local durante indisponibilidade de rede.
- A saída visual usa LED vermelho fixo durante alerta ou falha de sensor e verde no estado normal.
- O buzzer tem padrões distintos: dois bipes para temperatura, três para umidade e um longo para luminosidade.
- Alertas remotos expiram em dez minutos ou são encerrados por `normal`; o encerramento não é enviado automaticamente pelo frontend ao normalizar a leitura.
- `normal` não limpa uma condição local ainda ativa.

Se a aprovação não abranger o LED fixo ou a duração/encerramento do alerta remoto, esses comportamentos ainda precisam ser ajustados e testados. Não marcar o requisito original como literalmente atendido apenas porque um comando manual acionou o ESP.

Referências: [frontend](../frontend/src/App.jsx), [firmware](../firmware/WineGuard_firmware/WineGuard_firmware.ino) e [evidências físicas](../backend/docs/testes-vercel-esp.md).

## Arquitetura documentada para explicar na entrega

O [documento de arquitetura em camadas](arquitetura-em-camadas.md) consolida:

1. Apresentação: React, formulários, mapa, gráficos e interação do operador.
2. Aplicação/API: Spring Security, controllers, DTOs e serviços Java; autenticação, autorização por proprietário e regras de configuração.
3. Integração IoT: clientes FIWARE, Orion, IoT Agent UltraLight, Mosquitto e tópicos de telemetria/comandos.
4. Persistência: PostgreSQL para cadastros/configurações e MongoDB para os dados mantidos pelo FIWARE/STH.
5. Dispositivo e interação física: ESP32, sensores, NVS, display, LEDs e buzzer; controle local e execução dos comandos.

A Vercel e o Nginx encaminham requisições; não substituem as regras de negócio e autorização da API. Explicar também a organização interna Java em controller/service/repository/model, sem confundir esse recorte com a topologia das duas EC2.

## Evidências existentes e seus limites

- [Testes Vercel e ESP](../backend/docs/testes-vercel-esp.md): alerta de temperatura, `normal`, revisão 2 e telemetria após o reinício solicitado.
- [Postman](../backend/docs/postman/README-postman.md): execução parcial de autenticação, perfis, mapa e isolamento de sensores.
- [Infraestrutura](../infra/fiware/README.md): Compose validado estruturalmente e sete testes offline também na VM; sem reprodução completa nova.
- [Firmware](../firmware/README.md): pinos e configuração; versões, esquema, Nextion e recompilação reproduzível ainda pendentes.

As suítes de frontend e backend e a leitura de código não substituem o hands-on ou um vídeo que mostre a montagem real. Os nomes, RMs e responsabilidades dos quatro integrantes foram confirmados pela equipe; a lista completa de identificação foi atualizada em 30/10/2026. A equipe confirmou em 30/10 que vídeo, fotos e arquivos Nextion ainda serão preparados. A apresentação já foi realizada, segundo a equipe. A simulação deve manter BME680/LDR e ainda precisa de modelo personalizado e link.

## Prioridade para fechamento

1. Confirmar o escopo da adaptação visual, especialmente LED fixo versus piscante.
2. Concluir e salvar a simulação Wokwi e registrar o link; os nomes e RMs da equipe já estão completos.
3. Complementar os manuais de hardware e preservar a documentação da arquitetura; os requisitos/comentários do frontend e o documento de camadas já foram acrescentados.
4. Reunir arquivos Nextion, versões e esquema elétrico com o responsável pelo hardware.
5. Preparar o vídeo de evidências conforme o [roteiro](roteiro-apresentacao-video.md). A apresentação já foi realizada e não é tratada como pendência.
6. Conferir os links e os campos do Forms e realizar o envio pela equipe.

Concorrência real do PostgreSQL, cobertura integral de Postman e testes prolongados de NVS são melhorias de validação, mas não foram listados como entregas explícitas no enunciado recebido. Não confundir essas melhorias com a ausência de materiais obrigatórios como vídeo e simulação.
