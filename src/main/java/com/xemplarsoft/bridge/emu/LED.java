package com.xemplarsoft.bridge.emu;

import java.awt.*;

public class LED extends Symbol{
    public LED(){
        attach(0, new Connection(true, false));
    }

    public void render(Graphics g) {
        if(getState(0)){ //LED On

        } else { //LED Off

        }
    }
}
