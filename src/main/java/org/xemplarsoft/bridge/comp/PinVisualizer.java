package org.xemplarsoft.bridge.comp;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;

import static org.xemplarsoft.bridge.comp.JByteViewer.pad2HexNum;
import static org.xemplarsoft.bridge.comp.JByteViewer.pad4HexNum;

public class PinVisualizer extends JPanel {
    protected HashMap<String, Pin> pins = new HashMap<>();
    protected String label;
    protected int labelX, labelY;

    public Font pinLabel, labelFont;
    public int drawWidth = -1, drawHeight = -1;

    public PinVisualizer(){
        pinLabel = getFont().deriveFont(9F);
    }

    public void setFor6502(){
        labelFont = Font.decode("Monospaced").deriveFont(Font.BOLD, 20F);
        setLabelPos(245, 43);
        label = "FFFF -> FF";

        pins.clear();
        for(int i = 0; i < 16; i++) {
            Pin p = new Pin("A" + i, Color.green);
            p.posX = ((15 - i) / (float)16);
            p.posY = 0;
            pins.put("A" + i, p);
        }
        for(int i = 0; i < 8; i++) {
            Pin p = new Pin("D" + i, Color.red);
            p.posX = (7 - i) / (float)16;
            p.posY = 0.55F;
            pins.put("D" + i, p);
        }
        Pin rw = new Pin("RW", Color.black);
        Pin rst = new Pin("Rst", Color.black);
        rw.posX = 15F / 16F;
        rw.posY = 0.55F;
        rst.posX = 14F/ 16F;
        rst.posY = 0.55F;

        pins.put("Rst", rst);
        pins.put("RW", rw);
    }

    public void setLabelPos(int labelX, int labelY){
        this.labelX = labelX;
        this.labelY = labelY;
    }

    public void setPinState(String name, boolean state){
        Pin p = pins.get(name);
        if(p == null) return;
        p.state = state;
        pins.put(name, p);

        calcLabelText();

        repaint();
    }

    public void calcLabelText(){
        int address = 0, data = 0, read = pins.get("RW").state ? 1 : 0;
        for(int i = 0; i < 16; i++){
            address += (pins.get("A" + i).state ? 1 : 0) << i;
        }

        for(int i = 0; i < 8; i++){
            data += (pins.get("D" + i).state ? 1 : 0) << i;
        }

        label = pad4HexNum(address) + (read == 1 ? " -> " : " <- ") + pad2HexNum(data);
    }

    public static final int PIN_SIZE = 20;
    private int getFontWidth(Graphics g, String text){
        return g.getFontMetrics().stringWidth(text);
    }
    protected void paintComponent(Graphics g) {
        if(drawWidth == -1) drawWidth = getWidth();
        if(drawHeight == -1) drawHeight = getHeight();

        g.setFont(pinLabel);
        g.setColor(getBackground());
        g.fillRect(0, 0, drawWidth, drawHeight);
        for(Pin p : pins.values()){
            g.setColor(Color.WHITE);
            g.fillOval((int)(p.posX * (drawWidth)), (int)(p.posY * drawHeight), PIN_SIZE, PIN_SIZE);

            g.setColor(p.c);
            if(p.state){
                g.fillOval((int)(p.posX * (drawWidth)), (int)(p.posY * drawHeight), PIN_SIZE, PIN_SIZE);
                g.setColor(Color.WHITE);
            } else {
                g.drawOval((int)(p.posX * (drawWidth)), (int)(p.posY * drawHeight), PIN_SIZE, PIN_SIZE);
                g.setColor(Color.BLACK);
            }
            g.drawString(p.name, (int)(p.posX * (drawWidth)) + (PIN_SIZE - getFontWidth(g, p.name)) / 2, (int)(p.posY * drawHeight) + 14);
        }

        g.setFont(labelFont);
        g.setColor(Color.WHITE);
        g.fillRect(labelX - 2, labelY - 16, getFontWidth(g, label) + 4, 18 + 2);
        g.setColor(Color.BLACK);
        g.drawRect(labelX - 2, labelY - 16, getFontWidth(g, label) + 4, 18 + 2);
        g.drawString(label, labelX, labelY);
    }

    public Color invertColor(Color c){
        return new Color(255 - c.getRed(), 255 - c.getGreen(), 255 - c.getBlue(), c.getAlpha());
    }

    private static class Pin{
        protected boolean state = false;
        protected String name;
        protected Color c;
        protected float posX, posY;

        public Pin(String name, Color c){
            this.name = name;
            this.c = c;
        }
    }
}
