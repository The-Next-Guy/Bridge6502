/* Written by Rohan Loomis a.k.a The_Next_Guy
 * 
 * Purpose:
 * 
 * The purpose for this tool, is to emulate RAM and rom ROM for a 6502 style microprocessor.
 * This will aid in writing programs, save cycles on real eeprom chips, and also act as a
 * debugging tool. Since this device will be hooked up to the address and data bus, as well
 * as the r/w pin, it could relay that information back to a user program.
 * 
 * The next goals are:
 *   Full programming of laptop ROM into device ROM
 *   Switch between laptop RAM and device RAM
 *   When in device RAM and ROM mode, run 6502 at full speed.
 *
 * The completed goals:
 *   Allow streaming of ROM contents over usb to run on real hardware
 *   Allow use of 4kb of arduino, device, OR laptop ram, for use with the MPU
 *   Relay current address and bits on the databus to user GUI, stop at higher speeds.
 *   Allow GUI to reset cpu, run and stop programs stepwise or at interval.
 *   Add inhibit pin to allow use of device or software (arduino) RAM.
 *   Add inhibit pin to allow use of device or laptop ROM.
 *   Add support programming pins to program device ROM: ROM OE, WE and 6502 BE
 *   Added serial data error checking, tested stable at 2 Mbps.
 */
#include <EEPROM.h>

/* Connections:
 *
 *  For the address bus, A0 is address 0, A1 is address 1 etc. Uses ports K and F, do not change
 *  For the data bus, PA0 (22) is data 0, PA1 (23) is data 1 etc. Uses port A, do not chnage

 *  For the 6502 RW pin,            PIN_6502_RW              8
 *  For the 6502 Clock pin,         PIN_6502_CLOCK           10, must be PWM
 *  For the 6502 Reset pin,         PIN_6502_RESET           9
 *  For the 6502 Bus Enable pin,    PIN_6502_BE              38
 *
 *  For the device EEPROM WE pin,   PIN_6502_ROM_WE          34
 *  For the device EEPROM OE pin,   PIN_6502_ROM_OE          36
 *
 *  For the device RAM inhibit pin, PIN_6502_RAM_INHIBIT     30
 *  For the device ROM inhibit pin, PIN_6502_ROM_INHIBIT     32
 *
 *  To setup the inhbit pins, simply replace the wire connecting the output from
 *  your address decoder to one of your RAM and ROMs' chip enable pins, with a 10K
 *  Ohm resistor. On the chip enable pin that you selected, connect a wire from here
 *  to PIN_6502_RAM_INHIBIT and PIN_6502_ROM_INHIBIT respectively. This allows the
 *  address decoder to still select the chips, but allows the arduino to have the
 *  final say. By setting these pins to inputs it allows for normal operation, setting
 *  these to outputs and the opposite of their enabled state, disableds the chips and
 *  allow for Laptop ROM and arduino RAM.
 *
 *  You can configure any pins except the address and data bus. PIN_6502_CLOCK
 *  must be PWM for full speed running. You can also configure ROM_START and
 *  RAM_START, as well as ROM_SIZE. RAM size is defined in a setting sent over
 *  the bridge. Lowering PROPAGATION_DELAY may result in faster operating speed.
 *  If using an NMOS 6502, its suggested to leave this at 50, but a WDC65c02
 *  could have this lowered.
 */
#define ROM_START 0x8000
#define RAM_START 0x0000

#define ROM_SIZE 0x7FFF


#define PIN_6502_RW 8
#define PIN_6502_CLOCK 10
#define PIN_6502_RESET 9

#define PIN_6502_RAM_INHIBIT 30
#define PIN_6502_ROM_INHIBIT 32

#define PIN_6502_ROM_WE 34
#define PIN_6502_ROM_OE 36
#define PIN_6502_BE 38

#define STATUS_LED 2

#define PROPAGATION_DELAY 50

boolean STATUS_STATE = false;

byte setting_init = 0xFF;
char setting_name[120];
long setting_baudRate = 19200;
const int setting_ramSize = 0x800;
unsigned long setting_tickDelay = 100000;
boolean setting_emulatedRAM = false;
boolean setting_emulatedROM = false;

byte ram[setting_ramSize];

void setup() {
  initSettings();

  Serial.begin(setting_baudRate);
  //Serial.begin(19200);
  for(int i = 0; i < setting_ramSize; i++){
    ram[i] = 0;
  }
  pinMode(STATUS_LED, OUTPUT);
  pinMode(PIN_6502_RW, INPUT);
  pinMode(PIN_6502_CLOCK, OUTPUT);
  pinMode(PIN_6502_RESET, OUTPUT);

  pinMode(PIN_6502_ROM_OE, OUTPUT);
  pinMode(PIN_6502_ROM_WE, OUTPUT);
  pinMode(PIN_6502_BE, OUTPUT);
  digitalWrite(PIN_6502_ROM_OE, 0);
  digitalWrite(PIN_6502_ROM_WE, 1);
  digitalWrite(PIN_6502_BE, 1);

  setEmulatedRAM(false);
  setEmulatedROM(true);

  digitalWrite(PIN_6502_RESET, 1);
  DDRF = 0;
  DDRK = 0;
  DDRA = 0;
}

void setEmulatedRAM(boolean state){
  setting_emulatedRAM = state;
  if(state){
    pinMode(PIN_6502_RAM_INHIBIT, OUTPUT);
    digitalWrite(PIN_6502_RAM_INHIBIT, 1);
  } else {
    pinMode(PIN_6502_RAM_INHIBIT, INPUT);
  }
}

void setEmulatedROM(boolean state){
  setting_emulatedROM = state;
  if(state){
    pinMode(PIN_6502_ROM_INHIBIT, OUTPUT);
    digitalWrite(PIN_6502_ROM_INHIBIT, 1);
  } else {
    pinMode(PIN_6502_ROM_INHIBIT, INPUT);
  }
}

#define ADDR_SETTING_INITIALIZED 0x0000
#define ADDR_SETTING_RAMS 0x0002
#define ADDR_SETTING_BAUD 0x0004
#define ADDR_SETTING_TICK 0x0008
#define ADDR_SETTING_NAME 0x0080
void initSettings(){
  String("Bridge 6502 v0.01").toCharArray(setting_name, 120);
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
}

void resetSettings(){
  EEPROM.update(ADDR_SETTING_INITIALIZED, 0xFF);
}

//Serial Reading Vars
static char readBuffer[512];
int bufferIndex = 0;
int dataLengthStart = -1, dataLengthEnd = -1, dataLen, dataStart = -1;
boolean reading = false, bangSet = false, questSet = false;

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
  handlePorts();
  readSerial();

  if(!handleRomRequest()) return;

  if((clock_running || doTick) && (micros() - lastCounted > setting_tickDelay)){
    lastCounted = micros();
    tick();
    doTick = false;
  }
}

boolean handleRomRequest(){
  if(romAddressRequested && !obtainedRequestedAddress){
    obtainRequestAttempts++;

    if(obtainRequestAttempts >= 50){
      requestCount++;
      requestDataROM(lastRequestedAddress);
    }
    delayMicroseconds(PROPAGATION_DELAY);
    return false;
  }

  if(romAddressRequested && obtainedRequestedAddress){
    romDataObtained(lastRequestedAddress, obtainedData);
    romAddressRequested = false;
    requestCount = 0;
  }

  return true;
}

void handlePorts(){
  unsigned int address = get6502Address();
  boolean rw = get6502RW();

  DDRF = 0;
  DDRK = 0;

  if(!rw){
    DDRA = 0;
    PORTA = 0;
    return;
  }
  if((address >= ROM_START) && setting_emulatedROM){ //Request Rom
    DDRA = 0xFF;
  } else if(address >= RAM_START && address < (setting_ramSize + RAM_START) && setting_emulatedRAM){ //Process Ram
    DDRA = 0xFF;
  } else {
    DDRA = 0;
    PORTA = 0;
  }
}

void tick(){
  if(needsReset){
    if(reset_step == 0) digitalWrite(PIN_6502_RESET, 0);
    if(reset_step < 10){
      pulse6502Clock();
    }
    if(reset_step == 10) digitalWrite(PIN_6502_RESET, 1);
    if(reset_step < 17){
      pulse6502Clock();
    }
    if(reset_step == 17){
      needsReset = false;
    }

    send6502Status();
    reset_step++;
    return;
  }

  if(!romAddressRequested){
    process6502();
  }
}

void process6502(){
  unsigned int address = get6502Address();
  boolean rw = get6502RW();

  handlePorts();

  if(rw && (address >= ROM_START) && setting_emulatedROM){ //Request Rom
    requestDataROM(address);
  } else if(address < setting_ramSize){ //Process Ram
    handlePorts();
    if(rw){ //Read From Rom
      if(setting_emulatedRAM){
        PORTA = requestDataRAM(address);
      }

      pulse6502Clock();

      sendReadRAM(address);
    } else {
      byte data = 0;
      if(setting_emulatedRAM){
        data = get6502Data();
        setDataRAM(address, data);
      }

      pulse6502Clock();

      sendWriteRAM(address, data);
    }
  } else {
    PORTA = 0;
    pulse6502Clock();
  }
  send6502Status();
}

void pulse6502Clock(){
  digitalWrite(PIN_6502_CLOCK, 0);
  digitalWrite(PIN_6502_CLOCK, 1);
  handlePorts();
}

unsigned int get6502Address(){
  return PINF + (PINK << 8);
}

byte get6502Data(){
  return PINA;
}

boolean get6502RW(){
  return digitalRead(PIN_6502_RW);
}

void romDataObtained(unsigned int address, byte data){
    unsigned int romAdd = address - 0x8000;
    //Pass onto 6502
    DDRA = 0xFF;
    PORTA = data;

    send6502Status();

    pulse6502Clock();

    //sendReadROM(address, PINA);
}

byte requestDataRAM(unsigned int address){
  return ram[address];
}

void setDataRAM(unsigned int address, byte data){
  ram[address] = data;
}

void requestDataROM(unsigned int address){
  lastRequestedAddress = address;
  obtainRequestAttempts = 0;
  obtainedRequestedAddress = false;
  romAddressRequested = true;

  Serial.flush();

  Serial.print(F("?{ROM,4}"));
  if(address < 0x1000)Serial.print('0');
  if(address < 0x100)Serial.print('0');
  if(address < 0x10)Serial.print('0');
  Serial.print(address, HEX);
  Serial.println(' ');

  Serial.flush();
}

void sendReadROM(unsigned int address, byte data){
  Serial.flush();
  Serial.print(F("!{ROM,7}"));
  if(address < 0x1000)Serial.print('0');
  if(address < 0x100)Serial.print('0');
  if(address < 0x10)Serial.print('0');
  Serial.print(address, HEX);
  Serial.print(',');
  if(data < 0x10) Serial.print('0');
  Serial.println(data, HEX);
  Serial.flush();
}

void sendWriteRAM(unsigned int address, byte data){
  Serial.flush();
  Serial.print(F("!{RAM,7}"));
  if(address < 0x1000)Serial.print('0');
  if(address < 0x100)Serial.print('0');
  if(address < 0x10)Serial.print('0');
  Serial.print(address, HEX);
  Serial.print(',');
  if(data < 0x10)Serial.print('0');
  Serial.println(data, HEX);
  Serial.flush();
}

void sendReadRAM(unsigned int address){
  Serial.flush();
  Serial.print(F("!{RAMR,4}"));
  if(address < 0x1000)Serial.print('0');
  if(address < 0x100)Serial.print('0');
  if(address < 0x10)Serial.print('0');
  Serial.println(address, HEX);
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
  Serial.print(address, HEX);
  Serial.print(',');
  if(data < 0x10)Serial.print('0');
  Serial.print(data, HEX);
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

void sendError(int code, String message){
  Serial.print(F("!{ERROR,"));
  int len = message.length();
  len++;
  if(code < 0) len++;
  len += digitCount(code, 10);
  Serial.print(String(len, 10));
  Serial.print('}');
  Serial.print(code);
  Serial.print(',');
  Serial.println(message);
  Serial.flush();
}

byte readDataEEPROM(unsigned int address){
  digitalWrite(PIN_6502_BE, 0); //Disable 6502 bus
  boolean mode = setting_emulatedROM;
  digitalWrite(PIN_6502_ROM_WE, 1); //Disable EEPROM writing
  digitalWrite(PIN_6502_ROM_OE, 1); //Disable EEPROM output
  setEmulatedROM(false);
  DDRF = 0xFF;
  DDRK = 0xFF;
  DDRA = 0;
  PORTK = (address >> 8) & 0xFF; //Upper Word
  PORTF = (address) & 0xFF;      //Lower Word
  digitalWrite(PIN_6502_ROM_OE, 0); //Enable EEPROM output
  delayMicroseconds(1);

  byte data = PINA;

  //Reset Ports before Enable
  setEmulatedROM(mode);
  handlePorts();
  digitalWrite(PIN_6502_BE, 1); //Enable 6502 bus
  DDRF = 0;
  DDRK = 0;
  DDRA = 0;

  return data;
}

byte writeDataEEPROM(unsigned int address, byte data){
  digitalWrite(PIN_6502_BE, 0); //Disable 6502 bus
  boolean mode = setting_emulatedROM;
  digitalWrite(PIN_6502_ROM_WE, 1); //Disable EEPROM writing
  digitalWrite(PIN_6502_ROM_OE, 1); //Disable EEPROM output
  setEmulatedROM(false);
  DDRF = 0xFF;
  DDRK = 0xFF;
  DDRA = 0xFF;
  PORTK = (address >> 8) & 0xFF; //Upper Word
  PORTF = (address) & 0xFF;      //Lower Word
  PORTA = data;

  delayMicroseconds(1);
  digitalWrite(PIN_6502_ROM_WE, 0); //Latch addresses
  delayMicroseconds(1);
  digitalWrite(PIN_6502_ROM_WE, 1); //Latch data
  delayMicroseconds(1);

  //Read EEPROM stored data
  DDRA = 0;
  digitalWrite(PIN_6502_ROM_OE, 0); //Disable EEPROM output
  delayMicroseconds(1);
  byte dat = PINA;

  //Reset Ports before Enable
  setEmulatedROM(mode);
  handlePorts();
  digitalWrite(PIN_6502_BE, 1); //Enable 6502 bus
  DDRF = 0;
  DDRK = 0;
  DDRA = 0;

  return dat;
}

int digitCount(int num, int radix){
  if(num < 0) num *= -1;
  int ret = 1;
  while(num > radix){
    num /= radix;
  }
  return ret;
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
  command = String(readBuffer);
  command = command.substring(0, len);
  firstComma = command.indexOf(',');
  action = command.substring(command.indexOf('{') + 1, firstComma);

  if(action == F("ROM")){
    secondComma = command.indexOf(',', firstComma + 1);
    address = command.substring(command.indexOf('}') + 1, secondComma);
    dataRaw = command.substring(secondComma + 1);

    unsigned int addr = hexToInt(address);

    byte data = hexToByte(dataRaw, 0);
    byte comp = hexToByte(dataRaw, 2);
    byte xorc = hexToByte(dataRaw, 4) ^ 'k';
    comp += data;

    if(comp != 255){ //Data recived is bad, requesting again.
      requestDataROM(lastRequestedAddress);
      sendError(-3, dataRaw);
      return;
    }
    if(xorc != data){
      requestDataROM(lastRequestedAddress);
      sendError(-3, dataRaw);
      return;
    }

    if(addr == lastRequestedAddress){
      obtainedData = data;
      obtainedRequestedAddress = true;
    } else {
      requestDataROM(lastRequestedAddress);
      address.concat(F(" != "));
      address.concat(String(lastRequestedAddress, 16));
      sendError(-4, address);
    }
    return;
  }

  sendMessage(F("Unknown Command"));
  Serial.flush();
}


void processRequest(int len){
  command = String(readBuffer);
  command = command.substring(0, len);
  firstComma = command.indexOf(',');
  action = command.substring(command.indexOf('{') + 1, firstComma);
  if(action == F("AWAKE")){
    Serial.println(F("!{AWAKE,0}"));
    Serial.flush();
    sendAllSettings();
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
    doTick = true;
    return;
  }
  if(action == F("CLOCK")){ //
    char start = command.substring(command.indexOf("}") + 1).charAt(0);
    if(start == '0') {
      Serial.println(F("!{CLOCK,1}0"));
      Serial.flush();
      clock_running = false;
      return;
    }
    if(start == '1') {
      Serial.println(F("!{CLOCK,1}1"));
      Serial.flush();
      clock_running = true;
      lastCounted = micros();
      return;
    }
  }
  if(action == F("RESET")){
    needsReset = true;
    reset_step = 0;
    return;
  }
  if(action == F("RAMCL")){
    for(int i = 0; i < setting_ramSize; i++){
      ram[i] = 0;
    }
    Serial.println(F("!{RAMCL,0}"));
    Serial.flush();
    return;
  }
  if(action == F("INHROM")){
    char start = command.substring(command.indexOf("}") + 1).charAt(0);
    if(start == '0'){ //Use Device ROM

    } else if(start == '1'){ //Use Laptop ROM

    }
  }
  if(action == F("INHRAM")){
    char start = command.substring(command.indexOf("}") + 1).charAt(0);
    if(start == '0'){ //Use Device RAM

    } else if(start == '1'){ //Use Arduino RAM

    }
  }
  if(action == F("EER")){
    key = command.substring(command.indexOf('}') + 1);
    unsigned int addr = hexToInt(key);
    byte data = readDataEEPROM(addr);
    Serial.print(F("!{EER,7}"));
    Serial.print(key);
    Serial.print(',');
    if(data < 0x10) Serial.print('0');
    Serial.println(data, HEX);

    Serial.flush();

    return;
  }
  if(action == F("EEW")){
    secondComma = command.indexOf(',', firstComma + 1);
    key = command.substring(command.indexOf('}') + 1, secondComma);
    value = command.substring(secondComma + 1);

    unsigned int addr = hexToInt(key);
    byte data = hexToByte(value, 0);

    data = writeDataEEPROM(addr, data);

    Serial.print(F("!{EEW,7}"));
    Serial.print(key);
    Serial.print(',');
    if(data < 0x10) Serial.print('0');
    Serial.println(data, HEX);

    Serial.flush();

    return;
  }
  if(action == F("EEWP")){
    secondComma = command.indexOf(',', firstComma + 1);
    key = command.substring(command.indexOf('}') + 1, secondComma);
    int dataIndex = secondComma + 1;

    unsigned int addr = hexToInt(key);

    unsigned int realCRC = readBuffer[dataIndex + 256] + ((readBuffer[dataIndex + 257]) << 8);
    unsigned int crc = calculateCRC(readBuffer, dataIndex, 256);

    Serial.print(F("!{EEWP,10}"));
    Serial.print(key);
    Serial.print(',');
    Serial.print((char)(realCRC));
    Serial.println((char)(realCRC >> 8));
    Serial.print(',');
    Serial.print((char)(crc));
    Serial.println((char)(crc >> 8));

    Serial.flush();

    return;
  }

  sendError(-1, "");
  Serial.flush();
}

String len;
void readSerial(){
  boolean cont = true;
  while(Serial.available() && cont){
    char c = (char)Serial.read();
    if(c == '!') {
      reading = false;
      bangSet = true;
      dataLengthStart = -1;
      dataLengthEnd = -1;
      return;
    }
    if(c == '?') {
      reading = false;
      questSet = true;
      dataLengthStart = -1;
      dataLengthEnd = -1;
      return;
    }
    if(c == '{' && bangSet) { //Message data start
      reading = true;
      readBuffer[0] = '!';
      bufferIndex = 1;
    }
    if(c == '{' && questSet) { //Request data start
      reading = true;
      readBuffer[0] = '?';
      bufferIndex = 1;
    }
    bangSet = false;
    questSet = false;

    if(reading && c == ','){
      dataLengthStart = bufferIndex + 1;
    }
    if(dataLengthStart > -1 && reading && c == '}'){
      dataLengthEnd = bufferIndex;
      len = String(readBuffer);
      len = len.substring(dataLengthStart, dataLengthEnd);

      dataLen = len.toInt();
      dataStart = bufferIndex;
    }

    if(reading) {
      readBuffer[bufferIndex] = c;
      bufferIndex++;

      if(bufferIndex >= 512){
        reading = false;
        bufferIndex = 0;
        sendError(-2, "");

        return;
      }
    }

    if(reading && (dataStart + dataLen + 1) - bufferIndex == 0){
      if(readBuffer[0] == '!'){
        processMessage(bufferIndex);
      } else if(readBuffer[0] == '?'){
        processRequest(bufferIndex);
      }

      dataLen = 0;
      dataStart = -1;
      bufferIndex = 0;
      reading = false;
      cont = false;
    }
  }
}

byte hexToByte(String arr, int off){
  byte ret = (cHexToInt(arr.charAt(off + 1)) & 0x0F) ;
  ret += (cHexToInt(arr.charAt(off)) & 0x0F) << 4;
  return ret;
}

unsigned int hexToInt(String arr){
  unsigned int ret = (cHexToInt(arr.charAt(3)) & 0x0F) ;
  ret += (cHexToInt(arr.charAt(2)) & 0x0F) << 4;
  ret += (cHexToInt(arr.charAt(1)) & 0x0F) << 8;
  ret += (cHexToInt(arr.charAt(0)) & 0x0F) << 12;

  return ret;
}

unsigned int hexToInt(char arr[]){
  unsigned int ret = (cHexToInt(arr[3]) & 0x0F) ;
  ret += (cHexToInt(arr[2]) & 0x0F) << 4;
  ret += (cHexToInt(arr[1]) & 0x0F) << 8;
  ret += (cHexToInt(arr[0]) & 0x0F) << 12;

  return ret;
}

unsigned int cHexToInt(char c){
  unsigned int ret = c - '0';
  if(ret > 15) ret -= 7;
  if(ret > 15) ret -= 32;
  return ret;
}

#define POLYNOMIAL 0x1021
#define INITIAL_VALUE 0xFFFF

uint16_t calculateCRC(byte data[], int off, uint16_t len) {
    uint16_t crc = INITIAL_VALUE;
    for (uint16_t i = off; i < len; i++) {
        crc ^= (data[i] << 8);
        for (uint8_t j = 0; j < 8; j++) {
            if (crc & 0x8000) {
                crc = (crc << 1) ^ POLYNOMIAL;
            } else {
                crc <<= 1;
            }
        }
    }
    return crc;
}
