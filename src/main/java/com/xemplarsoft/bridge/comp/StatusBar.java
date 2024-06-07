package com.xemplarsoft.bridge.comp;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class StatusBar extends JPanel {
    protected Map<String, StatusItem> items = new HashMap();
    protected int spacing = 16;
    public StatusBar(){

    }

    public void addItem(String id, String name, boolean right){
        items.put(id, new StatusItem(name, right));
        repaint();
    }
    public void addItem(String id, String name, Object defValue, boolean right){
        items.put(id, new StatusItem(name, defValue, right));
        repaint();
    }

    public void putValue(String id, Object value){
        StatusItem i = items.get(id);
        if(i == null) return;
        i.setValue(value);
        items.put(id, i);

        repaint();
    }

    public void setSpacing(int spacing){
        this.spacing = spacing;
        repaint();
    }

    private int getFontWidth(Graphics g, String text){
        return g.getFontMetrics().stringWidth(text);
    }

    protected void paintComponent(Graphics g) {
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getWidth());

        g.setColor(getForeground());
        g.setFont(getFont());
        int lOffset = 8, rOffset = 8;

        StringBuilder textBuilder;
        String text;
        for(StatusItem i : items.values()){
            textBuilder = new StringBuilder();
            text = textBuilder.append(i.name).append(": ").append(i.value.toString()).toString();
            int width = getFontWidth(g, text + " ");

            g.drawString(text, i.right ? (getWidth() - (rOffset + width)) : lOffset, getFont().getSize());

            if(i.right) rOffset += width + spacing;
            else lOffset += width + spacing;
        }
    }

    protected static class StatusItem{
        protected boolean right;
        protected String name;
        protected Object value;

        public StatusItem(String name, boolean right){
            this.name = name;
            this.right = right;
            value = "";
        }

        public StatusItem(String name, Object defValue, boolean right){
            this.name = name;
            this.right = right;
            value = defValue;
        }


        public void setValue(Object value) {
            this.value = value;
        }
    }
}
