package org.xemplarsoft.bridge.emu;

public class VRAM extends Chip{
    protected int rw, oe, cs0, cs1;
    protected int[] addressPins, dataPins;

    public byte[] memory;
    public VRAM(int[] addressPins, int[] dataPins, int rw, int oe, int cs0, int cs1){
        memory = new byte[1 << (addressPins.length - 1)];
        this.addressPins = addressPins;
        this.dataPins = dataPins;
        this.rw = rw;
        this.oe = oe;
        this.cs0 = cs0;
        this.cs1 = cs1;

        for(int i = 0; i < addressPins.length; i++){
            this.attach(addressPins[i], new Connection(true, false));
        }
        for(int i = 0; i < dataPins.length; i++){
            this.attach(dataPins[i], new Connection(false, true));
        }
        this.attach(rw, new Connection(true, false));
        if(oe > -1) this.attach(oe, new Connection(true, false));
        if(cs0 > -1) this.attach(cs0, new Connection(true, false));
        if(cs1 > -1) this.attach(cs1, new Connection(true, false));
    }

    public void update() {
        super.update();

        boolean rw = this.rw != -1 && getState(this.rw);
        boolean oe = this.oe == -1 || getState(this.oe);
        boolean cs0 = this.cs0 == -1 || getState(this.cs0);
        boolean cs1 = this.cs1 == -1 || getState(this.cs1);

        for(int i = 0; i < 8; i++) { //Clears Data Pin states
            this.setState(dataPins[i], false);
        }

        if(!cs0 || !cs1) {
            return;
        }

        if(rw && (!oe || this.oe == -1)){ //Writing
            int add = readAddressPins();
            if(add >= memory.length) return;
            memory[add] = readDataPins();
        } else { //Reading
            int add = readAddressPins();
            if(add >= memory.length) writeDataPins((byte) 0xFF);
            writeDataPins(memory[add]);
        }
    }

    protected void writeDataPins(byte data){
        for(int i = 0; i < 8; i++){
            this.setState(dataPins[i], ((data >> i) & 1) == 1);
        }
    }

    protected byte readDataPins(){
        byte ret = 0;
        for(int i = 0; i < 8; i++){
            this.setState(dataPins[i], false);
            ret |= (this.getState(dataPins[i], true) ? 1 : 0) << i;
        }

        return ret;
    }

    protected int readAddressPins(){
        int ret = 0;
        for(int i = 0; i < addressPins.length; i++){
            this.setState(addressPins[i], false);
            ret |= (this.getState(addressPins[i], true) ? 1 : 0) << i;
        }

        return ret;
    }

    public void setMemory(byte[] data){
        int lim = Math.min(data.length, this.memory.length);
        System.arraycopy(data, 0, this.memory, 0, lim);
    }

    public void setMemory(int address, byte data){
        this.memory[address] = data;
    }
}
