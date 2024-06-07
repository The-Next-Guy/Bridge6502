package com.xemplarsoft.bridge.comp;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class JImageButton extends JButton {
    private BufferedImage image;
    private int padding = 0;
    public JImageButton(BufferedImage image){
        this.image = image;
        this.padding = 4;
    }

    public void setImage(BufferedImage image){
        this.image = image;
        repaint();
    }

    public void setImagePadding(int padding){
        this.padding = padding;
    }

    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(image, padding, padding, getWidth() - (padding << 1), getHeight() - (padding << 1), null);
    }
}
