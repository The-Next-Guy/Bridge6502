#include "hardware/gpio.h"

#define PIN_RW         1 << 0
#define PIN_CLK        1 << 1
#define PIN_RST        1 << 13

#define DATA_MASK      0xFF << 2
#define DATA_START     2

#define PIN_DATA_OE    1 << 10
#define PIN_ADDR1_OE   1 << 11
#define PIN_ADDR2_OE   1 << 12

#define PINS_ALWAYS_OUTPUT  (PIN_CLK | PIN_RST | PIN_DATA_OE | PIN_ADDR1_OE | PIN_ADDR2_OE)
byte rom[0xFFFF];
byte ram[0x800];

void setup(){
  initSettings();
  Serial.begin(setting_baudRate);
}

void loop(){
  readSerial();
}

#define ADDR_SETTING_INITIALIZED 0x0000
#define ADDR_SETTING_RAMS 0x0002
#define ADDR_SETTING_BAUD 0x0004
#define ADDR_SETTING_TICK 0x0008
#define ADDR_SETTING_NAME 0x0080
void initSettings(){
  EEPROM.begin(4096);
  String("Bridge 6502 v0.01 Pico").toCharArray(setting_name, 120);
  if(EEPROM.read(ADDR_SETTING_INITIALIZED) != 0){
    EEPROM.put(ADDR_SETTING_INITIALIZED, 0);
    saveSettings();
  }
  //EEPROM.get(ADDR_SETTING_RAMS, setting_ramSize);
  EEPROM.get(ADDR_SETTING_BAUD, setting_baudRate);
  EEPROM.get(ADDR_SETTING_TICK, setting_tickDelay);
  EEPROM.get(ADDR_SETTING_NAME, setting_name);
  EEPROM.get(ADDR_SETTING_INITIALIZED, setting_init);

  //ram = (byte*) malloc(setting_ramSize * sizeof(byte));
}
void saveSettings(){
  EEPROM.put(ADDR_SETTING_RAMS, setting_ramSize);
  EEPROM.put(ADDR_SETTING_BAUD, setting_baudRate);
  EEPROM.put(ADDR_SETTING_TICK, setting_tickDelay);
  EEPROM.put(ADDR_SETTING_NAME, setting_name);
  EEPROM.commit();
}
void resetSettings(){
  EEPROM.update(ADDR_SETTING_INITIALIZED, 0xFF);
  initSettings();
}

//Serial Reading Vars
char serialBuffer[512];
int serialBufferIndex = 0;
int bufferLengthStart = -1, bufferLengthEnd = -1, bufferLen, bufferStart = -1;
boolean bufferReading = false, bangSet = false, questSet = false;

//Tick Vars
boolean clock_running = false;
unsigned long lastCounted = 0;

//ROM Vars
unsigned int lastRequestedAddress = 0, obtainRequestAttempts = 0;
boolean obtainedRequestedAddress = true, romAddressRequested = false;
byte obtainedData = 0;

//6502 Vars
boolean needsReset = false, doTick = false;
int reset_step = 0;

unsigned long requestCount = 0;
void loop() {
  readSerial();
}

void pulse6502Clock(){
  delayMicroseconds(1);
  digitalWrite(PIN_CLK, 1);
  delayMicroseconds(1);
  digitalWrite(PIN_CLK, 0);
  delayMicroseconds(1);
}

unsigned int get6502Address(){
  return status_address;
}

byte get6502Data(){
  return rw == 1 ? status_readData : status_writeData;
}

boolean get6502RW(){
  return rw == 1;
}

void setDataRAM(unsigned int address, byte data){
  ram[address] = data;
}

void setDataROM(unsigned int address, byte data){
  rom[address] = data;
}

void sendReadROM(unsigned int address, byte data){
  Serial.flush();
  Serial.print(F("!{ROM,7}"));
  if(address < 0x1000)Serial.print('0');
  if(address < 0x100)Serial.print('0');
  if(address < 0x10)Serial.print('0');
  Serial.print(String(address, 16));
  Serial.print(',');
  if(data < 0x10) Serial.print('0');
  Serial.println(String(data, 16));
  Serial.flush();
}

void sendWriteRAM(unsigned int address, byte data){
  Serial.flush();
  Serial.print(F("!{RAM,7}"));
  if(address < 0x1000)Serial.print('0');
  if(address < 0x100)Serial.print('0');
  if(address < 0x10)Serial.print('0');
  Serial.print(String(address, 16));
  Serial.print(',');
  if(data < 0x10)Serial.print('0');
  Serial.println(String(data, 16));
  Serial.flush();
}

void sendReadRAM(unsigned int address){
  Serial.flush();
  Serial.print(F("!{RAMR,4}"));
  if(address < 0x1000)Serial.print('0');
  if(address < 0x100)Serial.print('0');
  if(address < 0x10)Serial.print('0');
  Serial.println(String(address, 16));
  Serial.flush();
}

void send6502Status(){
  if(setting_tickDelay < 10000) return;

  unsigned int address = get6502Address();
  byte data = get6502Data();
  boolean rw = get6502RW();
  boolean reset = digitalRead(PIN_6502_RESET);

  Serial.print(F("!{6502,10}"));
  if(address < 0x1000)Serial.print('0');
  if(address < 0x100)Serial.print('0');
  if(address < 0x10)Serial.print('0');
  Serial.print(String(address, 16));
  Serial.print(',');
  if(data < 0x10)Serial.print('0');
  Serial.print(String(data, 16));
  Serial.print(',');
  Serial.print(rw ? '1' : '0');
  Serial.println(reset ? '1' : '0');
  Serial.flush();
}

void sendMessage(String message){
  Serial.print(F("!{MESSAGE,"));
  int len = message.length();
  Serial.print(String(len, 10));
  Serial.print('}');
  Serial.println(message);
  Serial.flush();
}

void sendAllSettings(){
  sendSettingValue(F("BAUD"));
  sendSettingValue(F("TICK"));
  sendSettingValue(F("NAME"));
  sendSettingValue(F("RAMS"));
}

void sendSettingValue(String key){
  String val;
  int len;
  if(key == F("BAUD")){
      val = String(setting_baudRate, 10);
      len = 5 + val.length();
  }
  if(key == F("TICK")){
      val = String(setting_tickDelay, 10);
      len = 5 + val.length();
  }
  if(key == F("NAME")){
      val = String(setting_name);
      val.trim();
      len = 5 + val.length();
  }
  if(key == F("RAMS")){
      val = String(setting_ramSize, 10);
      len = 5 + val.length();

  }
  Serial.print(F("!{SETTING,"));
  Serial.print(String(len, 10));
  Serial.print('}');
  Serial.print(key);
  Serial.print(',');
  Serial.println(val);
  Serial.flush();
}

void setSettingValue(String key, String value){
  if(key == F("BAUD")){
      setting_baudRate = (long) atol(value.c_str());
  }
  if(key == F("TICK")){
      setting_tickDelay = (unsigned long) atol(value.c_str());
  }
  if(key == F("NAME")){
      value.toCharArray(setting_name, 120);
  }
  if(key == F("RAMS")){
      //setting_ramSize = (unsigned int) atol(value.c_str());
  }

  sendSettingValue(key);
  if(key == F("BAUD")){
    Serial.end();
    Serial.begin(setting_baudRate);
  }
}

String command, action, address, dataRaw, key, value;
int firstComma, secondComma;

void processMessage(int len){
  command = String(serialBuffer);
  command = command.substring(0, len);
  firstComma = command.indexOf(',');
  action = command.substring(command.indexOf('{') + 1, firstComma);

  if(action == F("ROMLW")){
    secondComma = command.indexOf(',', firstComma + 1);
    address = command.substring(command.indexOf('}') + 1, secondComma);
    dataRaw = command.substring(secondComma + 1);

    unsigned int addr = hex4ToInt(address);
    int len = dataRaw.length();


    boolean status = status_clockOff;
    status_clockOff = true;

    byte d = 0;
    for(int i = 0; i < len; i += 2){
        rom[addr + (i >> 1)] = (cHexToInt(dataRaw.charAt(i)) << 8) | cHexToInt(dataRaw.charAt(i + 1));
    }

    status_clockOff = status;

    Serial.print(F("!{ROMLW,"))
    String msg = F("Wrote ");
    msg.concat(String(len >> 1, 10));
    msg.concat(F(" bytes."));
    Serial.print(msg.length());
    Serial.print('}');
    Serial.print(msg);
  }
  if(action == F("ROM")){
    secondComma = command.indexOf(',', firstComma + 1);
    address = command.substring(command.indexOf('}') + 1, secondComma);
    dataRaw = command.substring(secondComma + 1);

    unsigned int addr = hex4ToInt(address);
    int dataComp = hex2ToInt(dataRaw);

    rom[addr] = (data >> 8) & 0xFF;
    Serial.print(F("!{ROM,7}"));
    Serial.print(address);
    Serial.print(',');
    Serial.println(dataRaw);
    Serial.flush();

    return;
  }
  if(action == F("RAMCLR")){
    boolean status = status_clockOff;
    status_clockOff = true;
    for(unsigned int i = 0; i < 0x800; i++){
      ram[i] = 0;
    }
    status_clockOff = status;
  }

  sendMessage(F("Unknown Command"));
  Serial.flush();
}

void processRequest(int len){
  command = String(serialBuffer);
  command = command.substring(0, len);
  firstComma = command.indexOf(',');
  action = command.substring(command.indexOf('{') + 1, firstComma);
  if(action == F("AWAKE")){
    Serial.println(F("!{AWAKE,0}"));
    Serial.flush();
    return;
  }
  if(action == F("SETT")){ //Settings value set request
    secondComma = command.indexOf(',', firstComma + 1);
    key = command.substring(command.indexOf('}') + 1, secondComma);
    value = command.substring(secondComma + 1);
    setSettingValue(key, value);
    return;
  }
  if(action == F("SAVE")){ //Settings value set request
    saveSettings();
    sendAllSettings();
    return;
  }
  if(action == F("SETTING")){ //Settings value request
    key = command.substring(command.indexOf('}') + 1);
    sendSettingValue(key);
    return;
  }
  if(action == F("CLOCKPULSE")){
    status_singleStep = true;
    status_clockOff = false;
    return;
  }
  if(action == F("CLOCK")){ //
    char start = command.substring(command.indexOf("}") + 1).charAt(0);
    if(start == '0') {
      Serial.println(F("!{CLOCK,1}0"));
      Serial.flush();
      status_clockOff = true;
      return;
    }
    if(start == '1') {
      Serial.println(F("!{CLOCK,1}1"));
      Serial.flush();
      status_singleStep = false;
      status_clockOff = false;
      return;
    }
  }
  if(action == F("RESET")){
    status_clockOff = true;
    //PERFORM reset
    return;
  }
  if(action == F("RAMLR")){
    secondComma = command.indexOf(',', firstComma + 1);
    address = command.substring(command.indexOf('}') + 1);
    dataRaw = command.substring(secondComma + 1);

    unsigned int addr = hex4ToInt(address);
    int len = dataRaw.toInt();

    byte d = 0;
    Serial.print(F("!{RAMLR,"));
    Serial.print(len << 1);
    Serial.print('}');
    for(int i = 0; i < len; i++){
      if(ram[address + i] < 10) Serial.print('0');
      Serial.print(String(rom[address + i], 16));
    }
    Serial.println();
    Serial.flush();

    return;
  }
  if(action == F("ROMLR")){
    secondComma = command.indexOf(',', firstComma + 1);
    address = command.substring(command.indexOf('}') + 1);
    dataRaw = command.substring(secondComma + 1);

    unsigned int addr = hex4ToInt(address);
    int len = dataRaw.toInt();

    byte d = 0;
    Serial.print(F("!{ROMLR,"));
    Serial.print(len << 1);
    Serial.print('}');
    for(int i = 0; i < len; i++){
      if(rom[address + i] < 10) Serial.print('0');
      Serial.print(String(rom[address + i], 16));
    }
    Serial.println();
    Serial.flush();

    return;
  }

  Serial.println(F("!{ERROR,2}-1"));
  Serial.flush();
}

String len;
void readSerial(){
  boolean cont = true;
  while(Serial.available() && cont){
    char c = (char)Serial.read();
    if(c == '!') {
      bufferReading = false;
      bangSet = true;
      bufferLengthStart = -1;
      bufferLengthEnd = -1;
      return;
    }
    if(c == '?') {
      bufferReading = false;
      questSet = true;
      bufferLengthStart = -1;
      bufferLengthEnd = -1;
      return;
    }
    if(c == '{' && bangSet) { //Message data start
      bufferReading = true;
      serialBuffer[0] = '!';
      serialBufferIndex = 1;
    }
    if(c == '{' && questSet) { //Request data start
      bufferReading = true;
      serialBuffer[0] = '?';
      serialBufferIndex = 1;
    }
    bangSet = false;
    questSet = false;

    if(bufferReading && c == ','){
      bufferLengthStart = serialBufferIndex + 1;
    }
    if(bufferLengthStart > -1 && bufferReading && c == '}'){
      bufferLengthEnd = serialBufferIndex;
      len = String(serialBuffer);
      len = len.substring(bufferLengthStart, bufferLengthEnd);

      bufferLen = len.toInt();
      bufferStart = serialBufferIndex;
    }

    if(bufferReading) {
      serialBuffer[serialBufferIndex] = c;
      serialBufferIndex++;

      if(serialBufferIndex >= 512){
        bufferReading = false;
        serialBufferIndex = 0;
        sendMessage(F("!{ERROR,2}-2"));
        return;
      }
    }

    if(bufferReading && (bufferStart + bufferLen + 1) - serialBufferIndex == 0){
      if(serialBuffer[0] == '!'){
        processMessage(serialBufferIndex);
      } else if(serialBuffer[0] == '?'){
        processRequest(serialBufferIndex);
      }

      bufferLen = 0;
      bufferStart = -1;
      serialBufferIndex = 0;
      bufferReading = false;
      cont = false;
    }
  }
}

volatile boolean status_clockOff = true, status_singleStep = false;
volatile long status_loopDelay = 0, loopWaitTime = 0;

unsigned long status_gpio = 0;
unsigned int status_address = 0;
byte status_rw = 0;
byte status_readData = 0;
int status_writeData = 0;

void setup1() {
  gpio_init_mask(PINS_ALWAYS_OUTPUT | DATA_MASK);
  gpio_set_dir_all_bits(PINS_ALWAYS_OUTPUT);

  gpio_put_all(PIN_RST | PIN_ADDR1_OE);

  status_clockOff = false;
}

void loop1() { start:
  if(status_clockOff) goto start;
  gpio_set_dir_all_bits(PINS_ALWAYS_OUTPUT); //Remove pico data from bus
  gpio_put_all(PIN_RST | PIN_ADDR1_OE);
  loopDelay = 0; //Also delay to allow the level converter to fully output

  //Get Address Lower and RW
  status_gpio = gpio_get_all();
  status_rw = status_gpio & PIN_RW; //Also delays to allow the level converter to fully output
  status_address = (status_gpio >> DATA_START) & 0xFF;

  //Get Address Upper
  gpio_put_all(PIN_RST | PIN_ADDR2_OE);
  status_gpio = gpio_get_all();
  status_address |= ((status_gpio >> DATA_START) & 0xFF) << 8;

  //Allow Reading of data bus
  gpio_put_all(PIN_RST | PIN_DATA_OE | PIN_CLK);
  if(1 == 1){
    //Get Data
    status_gpio = gpio_get_all();
    status_readData =  //Read data off of bus
    if(status_address < 0x800) ram[status_address] = (status_gpio >> DATA_START) & 0xFF;
    status_writeData = 0;
  } else {
    if(status_address < 0x800) status_writeData = ram[status_address];
    if(status_address >= 0x8000) status_writeData = rom[status_address];
    status_writeData = status_writeData << DATA_START;

    gpio_set_dir_all_bits(DATA_MASK | PINS_ALWAYS_OUTPUT); //Put pico data on bus
  }
  gpio_put_all(PIN_RST | PIN_DATA_OE | PIN_CLK | status_writeData);
  while(loopDelay < loopWaitTime){ loopDelay++; }

  gpio_clr_mask(PIN_CLK);
  if(status_singleStep) status_clockOff = true;
  goto start;
}

unsigned int hex4ToInt(String arr){
  unsigned int ret = (cHexToInt(arr.charAt(3)) & 0x0F) ;
  ret += (cHexToInt(arr.charAt(2)) & 0x0F) << 4;
  ret += (cHexToInt(arr.charAt(1)) & 0x0F) << 8;
  ret += (cHexToInt(arr.charAt(0)) & 0x0F) << 12;

  return ret;
}

unsigned int hex4ToInt(char arr[]){
  unsigned int ret = (cHexToInt(arr[3]) & 0x0F) ;
  ret += (cHexToInt(arr[2]) & 0x0F) << 4;
  ret += (cHexToInt(arr[1]) & 0x0F) << 8;
  ret += (cHexToInt(arr[0]) & 0x0F) << 12;

  return ret;
}

byte hex2ToByte(String arr){
  unsigned int ret = (cHexToInt(arr.charAt(3)) & 0x0F) ;
  ret += (cHexToInt(arr.charAt(2)) & 0x0F) << 4;

  return ret;
}

byte hex2ToByte(char arr[]){
  unsigned int ret = (cHexToInt(arr[3]) & 0x0F) ;
  ret += (cHexToInt(arr[2]) & 0x0F) << 4;

  return ret;
}

unsigned int cHexToInt(char c){
  unsigned int ret = c - '0';
  if(ret > 15) ret -= 7;
  if(ret > 15) ret -= 32;
  return ret;
}
