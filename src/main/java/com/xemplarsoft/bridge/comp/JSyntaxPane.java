package com.xemplarsoft.bridge.comp;

import com.xemplarsoft.bridge.ModifiedListener;
import com.xemplarsoft.bridge.assy.AssemblyError;
import com.xemplarsoft.bridge.assy.AssemblySyntaxKit;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.Collection;


public class JSyntaxPane extends JEditorPane implements MouseMotionListener, KeyListener {
    public ArrayList<AssemblyError> errors;

    private final int offsetY = 3;
    private final int lineHeight = 14;
    private int hoverLineNumber;

    public JSyntaxPane(){
        addKeyListener(this);
        setFont(Font.decode("Monospaced").deriveFont(Font.PLAIN, (float)lineHeight));
        errors = new ArrayList<>();
        addMouseMotionListener(this);
    }

    private final ArrayList<ModifiedListener> modifiedListeners = new ArrayList<>();
    private boolean modified = false;
    public void addModifyListener(ModifiedListener l){
        modifiedListeners.add(l);
    }
    public void removeModifyListener(ModifiedListener l){
        modifiedListeners.remove(l);
    }
    private void dispatchModified(){
        if(modified) return;

        modified = true;
        for(int i = 0; i < modifiedListeners.size(); i++){
            modifiedListeners.get(i).modified(this);
        }
    }
    public void resetModified(){
        lastText = getText();
        modified = false;
    }

    public void clearErrors(){
        errors.clear();
        repaint();
    }

    public void addError(AssemblyError error){
        this.errors.add(error);
        repaint();
    }

    public void addErrors(Collection<? extends AssemblyError> errors){
        this.errors.addAll(errors);
        repaint();
    }

    private final Color COLOR_LINE_HIGHLIGHT = new Color(0.5F, 0.5F, 0.9F, 0.25F);
    private final Color COLOR_LINE_ERROR = new Color(0.9F, 0F, 0F, 0.25F);
    private final Color COLOR_LINE_WARNING = new Color(0.9F, 0.7F, 0.0F, 0.25F);
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        AssemblyError error;
        for(int i = 0; i < errors.size(); i++){
            error = errors.get(i);
            g.setColor(error.type == AssemblyError.Type.ERROR ? COLOR_LINE_ERROR : COLOR_LINE_WARNING);
            g.fillRect(0, offsetY + ((error.lineNumber - 1) * lineHeight), getWidth(), lineHeight);
        }

        g.setColor(COLOR_LINE_HIGHLIGHT);
        g.fillRect(0, offsetY + (hoverLineNumber * lineHeight), getWidth(), lineHeight);
    }

    public void init(){
        setEditorKit(new AssemblySyntaxKit());
    }

    public void mouseDragged(MouseEvent mouseEvent) {

    }

    public void mouseMoved(MouseEvent mouseEvent) {
        int x = mouseEvent.getX();
        int y = mouseEvent.getY();

        hoverLineNumber = (y - offsetY) / lineHeight;

        AssemblyError error = null;
        for(int i = 0; i < errors.size(); i++){
            if((errors.get(i).lineNumber - 1) == hoverLineNumber){
                error = errors.get(i);
                break;
            }
        }
        if(error != null){
            setToolTipText(error.message);
        } else {
            setToolTipText(null);
        }

        repaint();
    }

    private String lastText = "";
    public void setText(String t) {
        super.setText(t);
        lastText = t;
    }

    public void keyTyped(KeyEvent keyEvent) {
        if(!modified && !getText().equals(lastText)) dispatchModified();
    }
    public void keyPressed(KeyEvent keyEvent) {
        if(!modified && !getText().equals(lastText)) dispatchModified();
    }
    public void keyReleased(KeyEvent keyEvent) {
        if(!modified && !getText().equals(lastText)) dispatchModified();
    }
}
