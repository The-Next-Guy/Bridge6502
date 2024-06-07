package org.xemplarsoft.bridge.emu;

public class VROM extends VRAM{
    public VROM(int[] addressPins, int[] dataPins, int oe, int cs0, int cs1) {
        super(addressPins, dataPins, -1, oe, cs0, cs1);
    }
}
