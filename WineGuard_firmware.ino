// WineGuard - Firmware (ESP32) com limites configuraveis e persistentes
// Sensores (BME680 + LDR), display Nextion, LEDs, buzzer, Wi-Fi, NTP, MQTT sobre TLS
// Novo: comando "configure|v1,revisao,tMin,tMax,uMin,uMax,luzMax" (Preferences/NVS)
#include <Wire.h>
#include <Adafruit_Sensor.h>
#include "Adafruit_BME680.h"
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>
#include <Preferences.h>   // ja faz parte do pacote ESP32 (nenhuma biblioteca nova)
#include <time.h>
#include "secrets.h"     // Wi-Fi e MQTT (NAO versionar)
#include "ca_cert.h"     // certificado publico da CA

// ===== Pinos =====
const int PINO_LED_VERDE    = 26;
const int PINO_LED_VERMELHO = 25;
const int PINO_BUZZER       = 27;
const int PINO_LDR          = 34;
const int NEX_RX = 16, NEX_TX = 17;
const uint8_t ENDERECO_BME  = 0x76;

// ===== Rede e MQTT =====
const char*    MQTT_HOST    = "100.60.123.122";
const uint16_t MQTT_PORTA   = 8883;
const char*    DEVICE_ID    = "wine002";
const char*    CLIENT_ID    = "wineguard_wine002";
const char*    TOPICO_ATTRS  = "/vinheria2026/wine002/attrs";          // publicar telemetria
const char*    TOPICO_CMD    = "/vinheria2026/wine002/cmd";            // receber comandos
// Resposta dos comandos. CANDIDATO do contrato: depende da versao do IoT Agent e da
// liberacao da ACL pelo responsavel pela infraestrutura (ainda NAO validado).
const char*    TOPICO_CMDEXE = "/ul/vinheria2026/wine002/cmdexe";
const unsigned long INTERVALO_PUBLICACAO = 10000;              // 10 s
const unsigned long VALIDADE_REMOTO      = 600000UL;           // alerta remoto expira em 10 min

// ===== Buffers =====
const unsigned int MAX_PAYLOAD_CMD   = 192;   // maximo aceito no callback (+1 byte para o '\0')
const unsigned int BUFFER_MQTT       = 512;
const size_t       TAM_TELEMETRIA    = 192;

// ===== Testes de falha (deixe false na operacao normal) =====
const bool SIMULAR_FALHA_NVS       = false;   // true: a gravacao na flash "falha"
const bool SIMULAR_FALHA_CONFIRMACAO = false; // true: a publicacao da confirmacao "falha"

// ===== Perfil de limites PADRAO: 1 = adega real, 0 = bancada =====
// O perfil so define os valores da PRIMEIRA inicializacao (revisao 0) e as histereses,
// que sao fixas. Depois, o backend pode mudar os limites com o comando configure.
#define PERFIL_ADEGA 0

// <<<LOGICA_CFG  (codigo puro, sem Arduino: testado no PC)
#include <stdint.h>
#include <stdio.h>
#include <string.h>

// Temperatura e umidade sao guardadas em DECIMOS (15.0 -> 150) para comparar sempre com
// numeros inteiros, sem "quase igual" de ponto flutuante. Luz fica em ADC inteiro.
#if PERFIL_ADEGA
const int32_t PADRAO_TEMP_MIN = 100, PADRAO_TEMP_MAX = 180, HIST_TEMP = 10;   // 10.0..18.0, hist 1.0
const int32_t PADRAO_UMID_MIN = 500, PADRAO_UMID_MAX = 800, HIST_UMID = 30;   // 50..80, hist 3.0
const int32_t PADRAO_LUZ_MAX  = 1500, HIST_LUZ = 200;
#else
const int32_t PADRAO_TEMP_MIN = 150, PADRAO_TEMP_MAX = 290, HIST_TEMP = 5;    // 15.0..29.0, hist 0.5
const int32_t PADRAO_UMID_MIN = 400, PADRAO_UMID_MAX = 820, HIST_UMID = 30;   // 40..82, hist 3.0
const int32_t PADRAO_LUZ_MAX  = 3500, HIST_LUZ = 200;
#endif

struct Config {
  int32_t  tempMin, tempMax;   // decimos de grau C
  int32_t  umidMin, umidMax;   // decimos de %
  int32_t  luzMax;             // ADC 0..4095
  uint32_t revisao;            // 0 = padrao do perfil
};

// ----- Registro persistido (um unico bloco: tudo ou nada) -----
struct RegistroCfg {
  uint32_t magic;       // identifica o registro
  uint32_t formato;     // 1
  uint32_t revisao;
  int32_t  tempMin, tempMax, umidMin, umidMax, luzMax;
  uint32_t crc;         // CRC32 de todos os campos acima
};
const uint32_t REGISTRO_MAGIC = 0x57474347UL;   // "WGCG"

enum ResultadoCfg {
  CFG_APLICAR,            // valida e nova: falta gravar
  CFG_APLICADA,           // gravada e ativa
  CFG_JA_APLICADA,
  CFG_REJ_FORMATO,
  CFG_REJ_VERSAO,
  CFG_REJ_VALORES,
  CFG_REJ_ANTIGA,
  CFG_REJ_CONFLITO,
  CFG_REJ_ARMAZENAMENTO
};

enum ParseNum { NUM_OK, NUM_SINTAXE, NUM_FAIXA };

static bool ehDigito(char c) { return c >= '0' && c <= '9'; }

// Decimal estrito com ponto: [-]digitos[.digitos]. Rejeita "29abc", "NaN", "inf", "1e3",
// "+5", ".5", "5.", "1,5" e qualquer espaco. No maximo 1 casa DECIMAL significativa
// (29.50 vale, 29.55 nao: seria arredondar e comparar "quase igual").
ParseNum parseDecimos(const char* s, int32_t& saida) {
  if (!s) return NUM_SINTAXE;
  bool neg = false;
  if (*s == '-') { neg = true; s++; }
  if (!ehDigito(*s)) return NUM_SINTAXE;
  long v = 0; bool grande = false;
  while (ehDigito(*s)) {
    if (v < 1000000L) v = v * 10 + (*s - '0'); else grande = true;
    s++;
  }
  int dec = 0; bool precisao = false;
  if (*s == '.') {
    s++;
    if (!ehDigito(*s)) return NUM_SINTAXE;
    int casas = 0;
    while (ehDigito(*s)) {
      if (casas == 0) dec = *s - '0';
      else if (*s != '0') precisao = true;
      casas++; s++;
    }
  }
  if (*s != '\0') return NUM_SINTAXE;
  if (grande || precisao) return NUM_FAIXA;
  long total = v * 10 + dec;
  saida = (int32_t)(neg ? -total : total);
  return NUM_OK;
}

// Inteiro sem sinal de 32 bits, so digitos
ParseNum parseUint32(const char* s, uint32_t& saida) {
  if (!s || !ehDigito(*s)) return NUM_SINTAXE;
  uint64_t v = 0; bool grande = false;
  while (ehDigito(*s)) {
    v = v * 10 + (uint64_t)(*s - '0');
    if (v > 0xFFFFFFFFULL) { grande = true; v = 0xFFFFFFFFULL + 1; }
    s++;
  }
  if (*s != '\0') return NUM_SINTAXE;
  if (grande) return NUM_FAIXA;
  saida = (uint32_t)v;
  return NUM_OK;
}

// Valida a configuracao inteira (nao altera nada)
bool validarValores(const Config& c) {
  if (c.tempMin < -200 || c.tempMax > 600) return false;
  if (c.umidMin < 0 || c.umidMax > 1000) return false;
  if (c.luzMax < 0 || c.luzMax > 4095) return false;
  if (c.tempMin >= c.tempMax) return false;
  if (c.umidMin >= c.umidMax) return false;
  if ((c.tempMax - c.tempMin) <= 2 * HIST_TEMP) return false;
  if ((c.umidMax - c.umidMin) <= 2 * HIST_UMID) return false;
  if (c.luzMax < HIST_LUZ) return false;
  return true;
}

bool mesmosValores(const Config& a, const Config& b) {
  return a.tempMin == b.tempMin && a.tempMax == b.tempMax &&
         a.umidMin == b.umidMin && a.umidMax == b.umidMax && a.luzMax == b.luzMax;
}

Config configPadraoDoPerfil() {
  Config c;
  c.tempMin = PADRAO_TEMP_MIN; c.tempMax = PADRAO_TEMP_MAX;
  c.umidMin = PADRAO_UMID_MIN; c.umidMax = PADRAO_UMID_MAX;
  c.luzMax = PADRAO_LUZ_MAX;   c.revisao = 0;
  return c;
}

// Divide "a,b,c" in-place. Devolve o numero de campos (maximo+1 se passar do limite).
int dividirCampos(char* s, char* campos[], int maximo) {
  int n = 0;
  campos[n++] = s;
  for (char* p = s; *p; p++) {
    if (*p == ',') {
      *p = '\0';
      if (n >= maximo) return maximo + 1;
      campos[n++] = p + 1;
    }
  }
  return n;
}

// Interpreta o texto apos "configure|" (ex.: "v1,42,15.0,29.0,40.0,82.0,3500").
// NAO altera a configuracao ativa. 'args' e modificado (virgulas viram '\0').
// Devolve CFG_APLICAR quando valida e mais nova que a ativa; 'revResp' e a revisao
// que deve ir na resposta (0 se nao identificavel).
ResultadoCfg interpretarConfig(char* args, const Config& ativa, Config& nova, uint32_t& revResp) {
  char* campo[9];
  int n = dividirCampos(args, campo, 8);
  revResp = 0;

  uint32_t rev = 0;
  bool revOk = (n >= 2) && parseUint32(campo[1], rev) == NUM_OK && rev > 0;
  if (revOk) revResp = rev;

  // protocolo
  if (campo[0][0] == '\0') return CFG_REJ_FORMATO;
  if (strcmp(campo[0], "v1") != 0) {
    bool pareceVersao = (campo[0][0] == 'v' && ehDigito(campo[0][1]));
    for (const char* p = campo[0] + 1; pareceVersao && *p; p++) if (!ehDigito(*p)) pareceVersao = false;
    return pareceVersao ? CFG_REJ_VERSAO : CFG_REJ_FORMATO;
  }

  // exatamente 7 campos, nenhum vazio
  if (n != 7) return CFG_REJ_FORMATO;
  for (int i = 0; i < n; i++) if (campo[i][0] == '\0') return CFG_REJ_FORMATO;
  if (!revOk) return CFG_REJ_FORMATO;

  // sintaxe dos numeros
  Config c;
  ParseNum p0 = parseDecimos(campo[2], c.tempMin);
  ParseNum p1 = parseDecimos(campo[3], c.tempMax);
  ParseNum p2 = parseDecimos(campo[4], c.umidMin);
  ParseNum p3 = parseDecimos(campo[5], c.umidMax);
  uint32_t luz = 0;
  ParseNum p4 = parseUint32(campo[6], luz);
  if (p0 == NUM_SINTAXE || p1 == NUM_SINTAXE || p2 == NUM_SINTAXE ||
      p3 == NUM_SINTAXE || p4 == NUM_SINTAXE) return CFG_REJ_FORMATO;

  if (rev < ativa.revisao) return CFG_REJ_ANTIGA;

  bool valoresOk = (p0 == NUM_OK && p1 == NUM_OK && p2 == NUM_OK && p3 == NUM_OK && p4 == NUM_OK);
  if (valoresOk) {
    c.luzMax = (luz > 4095U) ? 4096 : (int32_t)luz;   // 4096 e rejeitado pela validacao
    c.revisao = rev;
    valoresOk = validarValores(c);
  }

  if (rev == ativa.revisao) {
    if (!valoresOk) return CFG_REJ_VALORES;
    return mesmosValores(c, ativa) ? CFG_JA_APLICADA : CFG_REJ_CONFLITO;
  }
  if (!valoresOk) return CFG_REJ_VALORES;
  nova = c;
  return CFG_APLICAR;
}

// "15.0" a partir de decimos (aceita negativos: -5 -> "-0.5")
int formatarDecimos(char* buf, size_t n, int32_t d) {
  long a = (d < 0) ? -(long)d : (long)d;
  return snprintf(buf, n, "%s%ld.%ld", (d < 0) ? "-" : "", a / 10, a % 10);
}

// "v1,42,15.0,29.0,40.0,82.0,3500". Devolve -1 se nao couber.
int formatarCfg(char* buf, size_t n, const Config& c) {
  char a[16], b[16], d[16], e[16];
  formatarDecimos(a, sizeof(a), c.tempMin);
  formatarDecimos(b, sizeof(b), c.tempMax);
  formatarDecimos(d, sizeof(d), c.umidMin);
  formatarDecimos(e, sizeof(e), c.umidMax);
  int r = snprintf(buf, n, "v1,%lu,%s,%s,%s,%s,%ld",
                   (unsigned long)c.revisao, a, b, d, e, (long)c.luzMax);
  return (r < 0 || (size_t)r >= n) ? -1 : r;
}

// "wine002@configure|v1,42,applied" etc. Devolve -1 se nao couber.
int formatarResposta(char* buf, size_t n, const char* dispositivo, ResultadoCfg r, uint32_t rev) {
  const char* fim = NULL;
  switch (r) {
    case CFG_APLICADA:          fim = "applied"; break;
    case CFG_JA_APLICADA:       fim = "already_applied"; break;
    case CFG_REJ_VALORES:       fim = "rejected,invalid_values"; break;
    case CFG_REJ_ANTIGA:        fim = "rejected,stale_revision"; break;
    case CFG_REJ_CONFLITO:      fim = "rejected,revision_conflict"; break;
    case CFG_REJ_ARMAZENAMENTO: fim = "rejected,storage_error"; break;
    case CFG_REJ_FORMATO:       fim = "rejected,invalid_format"; break;
    case CFG_REJ_VERSAO:        fim = "rejected,unsupported_version"; break;
    default:                    return -1;
  }
  int k = snprintf(buf, n, "%s@configure|v1,%lu,%s", dispositivo, (unsigned long)rev, fim);
  return (k < 0 || (size_t)k >= n) ? -1 : k;
}

// ----- Registro persistido: struct definida acima (antes das funcoes) -----
uint32_t crc32Bytes(const uint8_t* d, size_t n) {
  uint32_t crc = 0xFFFFFFFFUL;
  for (size_t i = 0; i < n; i++) {
    crc ^= d[i];
    for (int k = 0; k < 8; k++) crc = (crc >> 1) ^ (0xEDB88320UL & (0 - (crc & 1)));
  }
  return ~crc;
}

void montarRegistro(const Config& c, RegistroCfg& r) {
  memset(&r, 0, sizeof(r));
  r.magic = REGISTRO_MAGIC; r.formato = 1; r.revisao = c.revisao;
  r.tempMin = c.tempMin; r.tempMax = c.tempMax;
  r.umidMin = c.umidMin; r.umidMax = c.umidMax; r.luzMax = c.luzMax;
  r.crc = crc32Bytes((const uint8_t*)&r, sizeof(r) - sizeof(r.crc));
}

// Registro lido da flash so vale se: magic, formato, CRC, revisao > 0 e valores validos
bool registroValido(const RegistroCfg& r, Config& saida) {
  if (r.magic != REGISTRO_MAGIC || r.formato != 1) return false;
  if (r.crc != crc32Bytes((const uint8_t*)&r, sizeof(r) - sizeof(r.crc))) return false;
  if (r.revisao == 0) return false;
  Config c;
  c.tempMin = r.tempMin; c.tempMax = r.tempMax;
  c.umidMin = r.umidMin; c.umidMax = r.umidMax;
  c.luzMax = r.luzMax;   c.revisao = r.revisao;
  if (!validarValores(c)) return false;
  saida = c;
  return true;
}
// LOGICA_CFG>>>

// Objetos existentes no Nextion (tRede e opcional)
const bool TEM_TUMID = true;
const bool TEM_TLUZ  = true;
const bool TEM_TREDE = true;

// ===== Tempos (ms) =====
const unsigned long INTERVALO_LEITURA_BME = 2000;
const unsigned long INTERVALO_LEITURA_LDR = 500;
const unsigned long INTERVALO_DISPLAY     = 500;
const unsigned long REFRESH_FORCADO       = 10000;
const unsigned long VALIDADE_BME          = 10000;   // leitura mais velha que isso = invalida
const unsigned long INTERVALO_PADROES     = 2500;    // pausa entre padroes do buzzer

// ===== Configuracao ativa (limites) =====
Preferences prefs;
const char* NVS_NAMESPACE = "wineguard";
const char* NVS_CHAVE     = "cfg";
Config cfgAtiva;                       // preenchida em carregarConfig()
char   respostaPendente[96] = "";      // confirmacao de configure a publicar
bool   temRespostaPendente = false;

// ===== Sensores =====
Adafruit_BME680 bme(&Wire);
bool bmeIniciado = false;
unsigned long ultimaTentativaBme = 0, ultimoInicioLeitura = 0, fimLeitura = 0, ultimaLeituraOk = 0;
bool leituraEmAndamento = false;
uint8_t falhasBme = 0;
bool bmeValido = false;
float temperatura = 0, umidade = 0;
int luzBruta = 0;                       // ADC 0..4095 (NAO e lux)
unsigned long ultimaLeituraLdr = 0;

// ===== Alertas =====
bool localTemp = false, localUmid = false, localLuz = false;      // originados pelos sensores
bool remotoTemp = false, remotoUmid = false, remotoLuz = false;   // vindos do FIWARE
unsigned long expiraTemp = 0, expiraUmid = 0, expiraLuz = 0;
bool ativoTemp = false, ativoUmid = false, ativoLuz = false;      // local OU remoto

// ===== Buzzer (nao bloqueante) =====
const uint16_t PADRAO_TEMP[] = {150, 150, 150};            // 2 bipes curtos
const uint16_t PADRAO_UMID[] = {150, 150, 150, 150, 150};  // 3 bipes curtos
const uint16_t PADRAO_LUZ[]  = {800};                      // 1 bipe longo
const uint16_t* padraoAtual = nullptr;
uint8_t tamanhoPadrao = 0, passo = 0, ultimoTocado = 2;
unsigned long ultimoPasso = 0, fimPadrao = 0;
bool tocando = false;

// ===== Display Nextion =====
HardwareSerial& NEX = Serial2;
String cacheTemp = "", cacheUmid = "", cacheLuz = "", cacheEstado = "", cacheRede = "";
uint16_t cacheCor = 0;
unsigned long ultimoDisplay = 0, ultimoRefresh = 0;

// ===== Rede =====
WiFiClientSecure tlsClient;
PubSubClient mqtt(tlsClient);
unsigned long ultimaTentativaWifi = 0, ultimaTentativaMqtt = 0;
unsigned long esperaMqtt = 5000;        // backoff: 5, 10, 20, 40, 60 s
unsigned long ultimaPublicacao = 0, ultimoLog = 0;
bool ntpConfigurado = false;

void nex(const String& cmd) {
  NEX.print(cmd);
  NEX.write(0xFF); NEX.write(0xFF); NEX.write(0xFF);
}

void imprimirSeguro(const char* s) {
  for (; *s; s++) Serial.print(isprint((unsigned char)*s) ? *s : '?');
}

bool horaValida() { return time(nullptr) > 1700000000; }

// ================= Configuracao persistente (Preferences/NVS) =================
void imprimirConfig(const char* titulo, const Config& c) {
  char txt[80];
  if (formatarCfg(txt, sizeof(txt), c) < 0) strcpy(txt, "?");
  Serial.print(titulo);
  Serial.println(txt);
}

// Le o registro da flash. Qualquer problema -> padroes do perfil, revisao 0.
void carregarConfig() {
  cfgAtiva = configPadraoDoPerfil();
  if (!prefs.begin(NVS_NAMESPACE, true)) {          // somente leitura
    imprimirConfig("Config: nada salvo, usando padrao do perfil: ", cfgAtiva);
    return;
  }
  RegistroCfg reg;
  size_t tam = prefs.getBytesLength(NVS_CHAVE);
  bool leu = (tam == sizeof(reg)) && prefs.getBytes(NVS_CHAVE, &reg, sizeof(reg)) == sizeof(reg);
  prefs.end();

  Config lida;
  if (leu && registroValido(reg, lida)) {
    cfgAtiva = lida;
    imprimirConfig("Config: carregada da flash: ", cfgAtiva);
  } else {
    if (tam != 0) Serial.println("Config: registro salvo invalido, usando padrao do perfil");
    imprimirConfig("Config: padrao do perfil: ", cfgAtiva);
  }
}

// Grava TUDO em um unico registro e confere lendo de volta.
bool gravarConfig(const Config& c) {
  if (SIMULAR_FALHA_NVS) { Serial.println("Config: [TESTE] falha de gravacao simulada"); return false; }
  RegistroCfg reg, anterior, conferido;
  montarRegistro(c, reg);

  bool tinhaAnterior = false;
  if (prefs.begin(NVS_NAMESPACE, true)) {
    tinhaAnterior = prefs.getBytesLength(NVS_CHAVE) == sizeof(anterior) &&
                    prefs.getBytes(NVS_CHAVE, &anterior, sizeof(anterior)) == sizeof(anterior);
    prefs.end();
  }

  if (!prefs.begin(NVS_NAMESPACE, false)) return false;
  size_t escrito = prefs.putBytes(NVS_CHAVE, &reg, sizeof(reg));
  size_t lido = (escrito == sizeof(reg)) ? prefs.getBytes(NVS_CHAVE, &conferido, sizeof(conferido)) : 0;
  bool ok = (escrito == sizeof(reg)) && (lido == sizeof(conferido)) &&
            memcmp(&reg, &conferido, sizeof(reg)) == 0;
  if (!ok && tinhaAnterior) prefs.putBytes(NVS_CHAVE, &anterior, sizeof(anterior));   // tenta manter o antigo
  prefs.end();
  return ok;
}

// ================= BME680 =================
void iniciarBME() {
  ultimaTentativaBme = millis();
  if (!bme.begin(ENDERECO_BME)) {
    Serial.println("BME680 nao encontrado; tentando de novo em 5 s");
    return;
  }
  bme.setTemperatureOversampling(BME680_OS_8X);
  bme.setHumidityOversampling(BME680_OS_2X);
  bme.setPressureOversampling(BME680_OS_4X);
  bme.setIIRFilterSize(BME680_FILTER_SIZE_3);
  bme.setGasHeater(0, 0);               // aquecedor de gas desligado
  bmeIniciado = true;
  falhasBme = 0;
  Serial.println("BME680 iniciado");
}

// Leitura em duas fases (inicia, depois coleta), sem bloquear o loop
void lerBME() {
  unsigned long agora = millis();
  if (!bmeIniciado) {
    if (agora - ultimaTentativaBme >= 5000) iniciarBME();
    return;
  }
  if (!leituraEmAndamento) {
    if (agora - ultimoInicioLeitura >= INTERVALO_LEITURA_BME) {
      ultimoInicioLeitura = agora;
      fimLeitura = bme.beginReading();
      if (fimLeitura == 0) { if (falhasBme < 255) falhasBme++; }
      else leituraEmAndamento = true;
    }
  } else if ((int32_t)(agora - fimLeitura) >= 0) {
    leituraEmAndamento = false;
    if (bme.endReading()) {
      float t = bme.temperature, u = bme.humidity;
      bool plausivel = !isnan(t) && !isnan(u) && !isinf(t) && !isinf(u)
                       && t > -20 && t < 60 && u >= 0 && u <= 100;
      if (plausivel) {
        temperatura = t; umidade = u;
        falhasBme = 0;
        ultimaLeituraOk = agora;
        return;
      }
    }
    if (falhasBme < 255) falhasBme++;
  }
}

// ================= LDR =================
void lerLDR() {
  if (millis() - ultimaLeituraLdr < INTERVALO_LEITURA_LDR) return;
  ultimaLeituraLdr = millis();
  long soma = 0;
  for (int i = 0; i < 8; i++) soma += analogRead(PINO_LDR);
  luzBruta = soma / 8;
}

// ================= Alertas =================
void atualizarAlertas() {
  unsigned long agora = millis();
  bmeValido = bmeIniciado && falhasBme < 3 && ultimaLeituraOk != 0
              && (agora - ultimaLeituraOk) < VALIDADE_BME;

  // Limites ATIVOS (configuraveis) e histereses FIXAS do perfil
  const float tMax = cfgAtiva.tempMax / 10.0f, tMin = cfgAtiva.tempMin / 10.0f, tHist = HIST_TEMP / 10.0f;
  const float uMax = cfgAtiva.umidMax / 10.0f, uMin = cfgAtiva.umidMin / 10.0f, uHist = HIST_UMID / 10.0f;
  const int   lMax = cfgAtiva.luzMax;

  // Alertas LOCAIS com histerese. Com o BME680 invalido, os alertas ja ativos
  // de temperatura/umidade NAO sao apagados.
  if (bmeValido) {
    if (!localTemp) localTemp = (temperatura > tMax || temperatura < tMin);
    else if (temperatura <= tMax - tHist && temperatura >= tMin + tHist) localTemp = false;

    if (!localUmid) localUmid = (umidade > uMax || umidade < uMin);
    else if (umidade <= uMax - uHist && umidade >= uMin + uHist) localUmid = false;
  }
  if (!localLuz) localLuz = (luzBruta > lMax);
  else if (luzBruta <= lMax - HIST_LUZ) localLuz = false;

  // Alertas REMOTOS expiram sozinhos
  if (remotoTemp && (long)(agora - expiraTemp) >= 0) { remotoTemp = false; Serial.println("Alerta remoto de temperatura expirou"); }
  if (remotoUmid && (long)(agora - expiraUmid) >= 0) { remotoUmid = false; Serial.println("Alerta remoto de umidade expirou"); }
  if (remotoLuz  && (long)(agora - expiraLuz)  >= 0) { remotoLuz  = false; Serial.println("Alerta remoto de luminosidade expirou"); }

  // Alerta final = local OU remoto
  ativoTemp = localTemp || remotoTemp;
  ativoUmid = localUmid || remotoUmid;
  ativoLuz  = localLuz  || remotoLuz;
}

// ================= Comando configure =================
// 'args' = texto depois de "configure|" (modificavel). Prepara a confirmacao para ser
// publicada no loop (nao publicamos dentro do callback do MQTT).
void tratarConfigure(char* args) {
  Config nova;
  uint32_t revResp = 0;
  ResultadoCfg r = interpretarConfig(args, cfgAtiva, nova, revResp);

  if (r == CFG_APLICAR) {
    if (!gravarConfig(nova)) {
      r = CFG_REJ_ARMAZENAMENTO;          // nada muda na configuracao ativa
    } else {
      cfgAtiva = nova;                    // so apos gravar com sucesso
      r = CFG_APLICADA;
      atualizarAlertas();                 // reavalia os alertas LOCAIS com os novos limites
      // (alertas remotos nao sao tocados)
    }
  }

  switch (r) {
    case CFG_APLICADA:          imprimirConfig("Config: APLICADA -> ", cfgAtiva); break;
    case CFG_JA_APLICADA:       Serial.println("Config: ja aplicada (sem nova gravacao)"); break;
    case CFG_REJ_FORMATO:       Serial.println("Config: rejeitada (formato invalido)"); break;
    case CFG_REJ_VERSAO:        Serial.println("Config: rejeitada (versao de protocolo nao suportada)"); break;
    case CFG_REJ_VALORES:       Serial.println("Config: rejeitada (valores invalidos)"); break;
    case CFG_REJ_ANTIGA:        Serial.println("Config: rejeitada (revisao antiga)"); break;
    case CFG_REJ_CONFLITO:      Serial.println("Config: rejeitada (mesma revisao, valores diferentes)"); break;
    case CFG_REJ_ARMAZENAMENTO: Serial.println("Config: rejeitada (erro ao gravar na flash)"); break;
    default: break;
  }

  if (formatarResposta(respostaPendente, sizeof(respostaPendente), DEVICE_ID, r, revResp) > 0)
    temRespostaPendente = true;
}

// Publica a confirmacao pendente. Se falhar, a configuracao aplicada e mantida:
// a telemetria periodica (campo cfg) permite reconciliar o estado.
void publicarConfirmacao() {
  if (!temRespostaPendente) return;
  temRespostaPendente = false;
  bool ok = false;
  if (!SIMULAR_FALHA_CONFIRMACAO && mqtt.connected())
    ok = mqtt.publish(TOPICO_CMDEXE, respostaPendente, false);   // sem retencao
  Serial.print("MQTT: confirmacao -> ");
  Serial.print(respostaPendente);
  Serial.println(ok ? "  (enviada; recebimento depende da ACL/IoT Agent)" : "  (FALHOU; configuracao mantida)");
}

// ================= Comandos remotos =================
// Formato recebido: "wine002@alert|temperature" (o IoT Agent coloca o prefixo).
// Tambem aceitamos o formato sem prefixo.
void receberComando(char* topic, byte* payload, unsigned int length) {
  if (strcmp(topic, TOPICO_CMD) != 0) return;
  if (length == 0 || length > MAX_PAYLOAD_CMD) {
    Serial.println("Comando ignorado: tamanho invalido");
    return;
  }
  char msg[MAX_PAYLOAD_CMD + 1];
  memcpy(msg, payload, length);
  msg[length] = '\0';
  while (length > 0 && isspace((unsigned char)msg[length - 1])) msg[--length] = '\0';

  char* cmd = msg;
  char* arroba = strchr(msg, '@');
  if (arroba) {
    size_t tamPrefixo = arroba - msg;
    if (tamPrefixo == strlen(DEVICE_ID) && strncmp(msg, DEVICE_ID, tamPrefixo) == 0) {
      cmd = arroba + 1;
    } else {
      Serial.print("Comando ignorado (outro dispositivo): ");
      imprimirSeguro(msg);
      Serial.println();
      return;
    }
  }

  unsigned long agora = millis();
  if (strncmp(cmd, "configure|", 10) == 0) {
    tratarConfigure(cmd + 10);
  }
  else if (strcmp(cmd, "alert|temperature") == 0) { remotoTemp = true; expiraTemp = agora + VALIDADE_REMOTO; Serial.println("Comando: alerta remoto de temperatura ATIVADO"); }
  else if (strcmp(cmd, "alert|humidity")    == 0) { remotoUmid = true; expiraUmid = agora + VALIDADE_REMOTO; Serial.println("Comando: alerta remoto de umidade ATIVADO"); }
  else if (strcmp(cmd, "alert|luminosity")  == 0) { remotoLuz  = true; expiraLuz  = agora + VALIDADE_REMOTO; Serial.println("Comando: alerta remoto de luminosidade ATIVADO"); }
  else if (strcmp(cmd, "alert|normal")      == 0) {
    // Encerra so os alertas REMOTOS. Alertas locais com a condicao ativa continuam.
    remotoTemp = remotoUmid = remotoLuz = false;
    Serial.println("Comando: alertas remotos encerrados (os locais continuam se a condicao persiste)");
  } else {
    Serial.print("Comando desconhecido ignorado: ");
    imprimirSeguro(msg);
    Serial.println();
  }
}

// ================= LEDs =================
void atualizarLEDs() {
  bool algum = ativoTemp || ativoUmid || ativoLuz || !bmeValido;
  digitalWrite(PINO_LED_VERMELHO, algum ? HIGH : LOW);
  digitalWrite(PINO_LED_VERDE, algum ? LOW : HIGH);
}

// ================= Buzzer =================
void iniciarPadrao(const uint16_t* p, uint8_t n) {
  padraoAtual = p; tamanhoPadrao = n; passo = 0;
  tocando = true;
  ultimoPasso = millis();
  digitalWrite(PINO_BUZZER, HIGH);
}

void atualizarBuzzer() {
  unsigned long agora = millis();
  if (tocando) {
    if (agora - ultimoPasso >= padraoAtual[passo]) {
      passo++;
      if (passo >= tamanhoPadrao) {
        tocando = false;
        digitalWrite(PINO_BUZZER, LOW);
        fimPadrao = agora;
        return;
      }
      ultimoPasso = agora;
      digitalWrite(PINO_BUZZER, (passo % 2 == 0) ? HIGH : LOW);
    }
    return;
  }
  if (!(ativoTemp || ativoUmid || ativoLuz)) return;
  if (agora - fimPadrao < INTERVALO_PADROES) return;

  bool ativo[3] = {ativoTemp, ativoUmid, ativoLuz};
  for (uint8_t i = 1; i <= 3; i++) {            // revezamento entre alertas ativos
    uint8_t c = (ultimoTocado + i) % 3;
    if (ativo[c]) {
      ultimoTocado = c;
      if (c == 0) iniciarPadrao(PADRAO_TEMP, 3);
      if (c == 1) iniciarPadrao(PADRAO_UMID, 5);
      if (c == 2) iniciarPadrao(PADRAO_LUZ, 1);
      return;
    }
  }
}

// ================= Display =================
void atualizarDisplay() {
  unsigned long agora = millis();
  if (agora - ultimoDisplay < INTERVALO_DISPLAY) return;
  ultimoDisplay = agora;

  if (agora - ultimoRefresh >= REFRESH_FORCADO) {   // reenvia tudo (caso o display reinicie)
    ultimoRefresh = agora;
    cacheTemp = cacheUmid = cacheLuz = cacheEstado = cacheRede = "";
    cacheCor = 0;
  }

  String t = bmeValido ? String(temperatura, 1) + " C" : "ERRO";
  if (t != cacheTemp) { nex("tTemp.txt=\"" + t + "\""); cacheTemp = t; }

  if (TEM_TUMID) {
    String u = bmeValido ? String(umidade, 0) + " %" : "ERRO";
    if (u != cacheUmid) { nex("tUmid.txt=\"" + u + "\""); cacheUmid = u; }
  }
  if (TEM_TLUZ) {
    String l = String(map(luzBruta, 0, 4095, 0, 100)) + " %";
    if (l != cacheLuz) { nex("tLuz.txt=\"" + l + "\""); cacheLuz = l; }
  }
  if (TEM_TREDE) {
    String r = (WiFi.status() == WL_CONNECTED) ? (mqtt.connected() ? "Wi-Fi OK MQTT OK" : "Wi-Fi OK MQTT --") : "Wi-Fi -- MQTT --";
    if (r != cacheRede) { nex("tRede.txt=\"" + r + "\""); cacheRede = r; }
  }

  String estado; uint16_t cor;
  int n = ativoTemp + ativoUmid + ativoLuz;
  if (n == 1) {
    estado = ativoTemp ? "ALERTA TEMP" : (ativoUmid ? "ALERTA UMID" : "ALERTA LUZ");
    cor = 63488;
  } else if (n > 1) {
    estado = "ALERTA ";
    if (ativoTemp) estado += "T";
    if (ativoUmid) estado += (ativoTemp ? "+U" : "U");
    if (ativoLuz)  estado += ((ativoTemp || ativoUmid) ? "+L" : "L");
    cor = 63488;
  } else if (!bmeValido) {
    estado = "FALHA SENSOR"; cor = 63488;
  } else {
    estado = "NORMAL"; cor = 2016;
  }
  if (estado != cacheEstado || cor != cacheCor) {
    nex("tEstado.bco=" + String(cor));
    nex("tEstado.txt=\"" + estado + "\"");
    cacheEstado = estado; cacheCor = cor;
  }
}

// ================= Rede =================
void verificarWiFi() {
  if (WiFi.status() == WL_CONNECTED) return;
  if (millis() - ultimaTentativaWifi >= 15000) {
    Serial.println("Wi-Fi: reconectando...");
    WiFi.disconnect();
    WiFi.begin(WIFI_SSID, WIFI_SENHA);
    ultimaTentativaWifi = millis();
  }
}

void verificarNTP() {
  if (WiFi.status() != WL_CONNECTED || ntpConfigurado) return;
  configTime(-3 * 3600, 0, "pool.ntp.org", "time.google.com");   // UTC-3
  ntpConfigurado = true;
  Serial.println("NTP: pedindo horario...");
}

const char* textoEstadoMqtt(int s) {
  switch (s) {
    case -4: return "timeout do servidor";
    case -3: return "conexao perdida";
    case -2: return "falha de conexao (rede, porta ou TLS)";
    case -1: return "desconectado";
    case 0:  return "conectado";
    case 1:  return "protocolo recusado";
    case 2:  return "client ID recusado";
    case 3:  return "servidor indisponivel";
    case 4:  return "usuario ou senha invalidos";
    case 5:  return "nao autorizado (ACL)";
    default: return "desconhecido";
  }
}

void reconectarMQTT() {
  if (mqtt.connected()) return;
  if (WiFi.status() != WL_CONNECTED) return;
  if (!horaValida()) return;            // sem hora, o certificado nao pode ser validado
  if (millis() - ultimaTentativaMqtt < esperaMqtt) return;
  ultimaTentativaMqtt = millis();

  // A conexao TLS BLOQUEIA o programa por alguns segundos. Para o buzzer nao ficar
  // preso ligado durante esse tempo, ele e desligado antes da tentativa.
  digitalWrite(PINO_BUZZER, LOW);
  tocando = false;
  fimPadrao = millis();

  Serial.println("MQTT: conectando com TLS...");
  if (mqtt.connect(CLIENT_ID, MQTT_USER, MQTT_SENHA)) {
    Serial.println("MQTT: CONECTADO (certificado validado)");
    esperaMqtt = 5000;
    if (mqtt.subscribe(TOPICO_CMD)) Serial.println("MQTT: inscrito em /cmd");
    else Serial.println("MQTT: falha ao se inscrever em /cmd");
  } else {
    int estado = mqtt.state();
    char erroTls[100] = "";
    tlsClient.lastError(erroTls, sizeof(erroTls));
    Serial.print("MQTT: falhou, estado ");
    Serial.print(estado);
    Serial.print(" - ");
    Serial.println(textoEstadoMqtt(estado));
    if (erroTls[0]) { Serial.print("TLS: "); Serial.println(erroTls); }
    esperaMqtt = min(esperaMqtt * 2, 60000UL);
    Serial.print("MQTT: proxima tentativa em ");
    Serial.print(esperaMqtt / 1000);
    Serial.println(" s");
  }
}

// Telemetria Ultralight: t|23.5|h|65|l|350|cfg|v1,42,15.0,29.0,40.0,82.0,3500
// Se o BME680 estiver invalido, NAO publica temperatura nem umidade (so luz e cfg).
// O campo cfg mostra os valores REALMENTE ativos (revisao 0 = padrao do perfil).
void publicarSensores() {
  if (!mqtt.connected()) return;
  if (millis() - ultimaPublicacao < INTERVALO_PUBLICACAO) return;
  ultimaPublicacao = millis();

  char cfgTxt[80];
  if (formatarCfg(cfgTxt, sizeof(cfgTxt), cfgAtiva) < 0) {
    Serial.println("MQTT: telemetria NAO publicada (cfg nao coube no buffer)");
    return;
  }

  char payload[TAM_TELEMETRIA];
  int n;
  if (bmeValido) n = snprintf(payload, sizeof(payload), "t|%.1f|h|%.0f|l|%d|cfg|%s", temperatura, umidade, luzBruta, cfgTxt);
  else           n = snprintf(payload, sizeof(payload), "l|%d|cfg|%s", luzBruta, cfgTxt);
  if (n < 0 || (size_t)n >= sizeof(payload)) {
    Serial.println("MQTT: telemetria NAO publicada (truncada)");
    return;
  }

  bool ok = mqtt.publish(TOPICO_ATTRS, payload, false);   // sem retencao
  Serial.print("MQTT: publicado -> ");
  Serial.print(payload);
  Serial.println(ok ? "  (enviado)" : "  (FALHOU)");
}

void setup() {
  Serial.begin(115200);
  pinMode(PINO_LED_VERDE, OUTPUT);
  pinMode(PINO_LED_VERMELHO, OUTPUT);
  pinMode(PINO_BUZZER, OUTPUT);
  digitalWrite(PINO_BUZZER, LOW);

  analogReadResolution(12);
  analogSetPinAttenuation(PINO_LDR, ADC_11db);

  Serial.println(PERFIL_ADEGA ? "Perfil: ADEGA" : "Perfil: BANCADA");
  carregarConfig();                    // funciona sem Wi-Fi

  NEX.begin(9600, SERIAL_8N1, NEX_RX, NEX_TX);
  Wire.begin(21, 22);
  iniciarBME();
  ultimoInicioLeitura = millis() - INTERVALO_LEITURA_BME;   // 1a leitura imediata

  WiFi.mode(WIFI_STA);
  WiFi.persistent(false);
  WiFi.setAutoReconnect(true);
  WiFi.begin(WIFI_SSID, WIFI_SENHA);                         // a senha nunca e impressa
  ultimaTentativaWifi = millis();

  tlsClient.setCACert(CA_CERT);        // valida o servidor pela CA do projeto (sem setInsecure)
  tlsClient.setHandshakeTimeout(10);
  mqtt.setServer(MQTT_HOST, MQTT_PORTA);
  mqtt.setCallback(receberComando);
  mqtt.setKeepAlive(30);
  mqtt.setSocketTimeout(5);
  if (!mqtt.setBufferSize(BUFFER_MQTT)) {
    Serial.println("ERRO: nao foi possivel alocar o buffer MQTT de 512 bytes (usando o padrao da biblioteca)");
  }
}

void loop() {
  verificarWiFi();
  verificarNTP();
  reconectarMQTT();
  mqtt.loop();
  publicarConfirmacao();               // fora do callback

  lerBME();
  lerLDR();
  atualizarAlertas();
  atualizarLEDs();
  atualizarBuzzer();
  atualizarDisplay();
  publicarSensores();

  if (millis() - ultimoLog >= 5000) {
    ultimoLog = millis();
    Serial.printf("T=%.1f U=%.0f LDR=%d | local T:%d U:%d L:%d | remoto T:%d U:%d L:%d | cfg rev %lu | BME %s | Wi-Fi %s | MQTT %s\n",
                  temperatura, umidade, luzBruta,
                  localTemp, localUmid, localLuz,
                  remotoTemp, remotoUmid, remotoLuz,
                  (unsigned long)cfgAtiva.revisao,
                  bmeValido ? "ok" : "ERRO",
                  WiFi.status() == WL_CONNECTED ? "OK" : "OFF",
                  mqtt.connected() ? "conectado" : "desconectado");
  }
}
