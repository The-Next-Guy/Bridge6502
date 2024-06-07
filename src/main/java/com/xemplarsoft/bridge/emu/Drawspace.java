package com.xemplarsoft.bridge.emu;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;

public class Drawspace extends JPanel implements MouseListener {
    public ArrayList<Symbol> symbols = new ArrayList<>();

    public Drawspace(){
        addMouseListener(this);

    }

    protected void paintComponent(Graphics g) {

    }

    public void mouseClicked(MouseEvent mouseEvent) {

    }

    public void mousePressed(MouseEvent mouseEvent) {

    }

    public void mouseReleased(MouseEvent mouseEvent) {

    }

    public void mouseEntered(MouseEvent mouseEvent) {

    }

    public void mouseExited(MouseEvent mouseEvent) {

    }
}
