package org.xemplarsoft.bridge.emu;

public class NOTGate extends Chip{
    public NOTGate(){
        attach(0, new Connection(true, false));
        attach(1, new Connection(false, true));
    }

    public void update() {
        super.update();
        boolean a = getState(0);

        setState(1, !a);
    }
}
