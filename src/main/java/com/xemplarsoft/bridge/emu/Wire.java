package com.xemplarsoft.bridge.emu;

import java.awt.*;

public class Wire extends Symbol{
    public Wire(){
        attach(0, new Connection(true, true));
        attach(1, new Connection(true, true));
    }

    public void update() {
        super.update();
        boolean state  = getState(0, true);
                state |= getState(1, true);

        setState(0, state);
        setState(1, state);
    }

    public void render(Graphics g) {

    }
}
