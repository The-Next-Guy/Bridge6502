package org.xemplarsoft.bridge.emu;

public class XNORGate extends Chip{
    public XNORGate(){
        attach(0, new Connection(true, false));
        attach(1, new Connection(true, false));
        attach(2, new Connection(false, true));
    }

    public void update() {
        super.update();
        boolean a = getState(0);
        boolean b = getState(1);

        setState(2, a == b);
    }
}
