package org.xemplarsoft.bridge.emu;

import com.github.weisj.jsvg.SVGDocument;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;

public abstract class Symbol {
    protected float xPos, yPos;

    public HashMap<Integer, Connection> connections = new HashMap<>();

    public int getPinCount(){
        return connections.size();
    }

    public void attach(int index, Connection connection){
        this.connections.get(index).addConnection(connection);
    }

    public void remove(int index, Connection connection){
        this.connections.get(index).removeConnection(connection);
    }

    public void remove(int index, int ID){
        this.connections.get(index).removeConnection(ID);
    }

    public void setState(int index, boolean state){
        if(index < 0) return;
        if(this.connections.get(index) != null) this.connections.get(index).setState(state);
    }

    public boolean getState(int index){
        return getState(index, false);
    }

    public boolean getState(int index, boolean ignoreSelf){
        if(index < 0) return false;
        return this.connections.get(index) != null && this.connections.get(index).getState(ignoreSelf);
    }

    public Point getPos(){
        return new Point((int)xPos, (int)yPos);
    }

    public Point getConnectionPos(int index){
        return new Point((int)(xPos + connections.get(index).xPos), (int)(yPos + connections.get(index).yPos));
    }

    public void update(){}
    public abstract void render(Graphics g);

    public static class Connection{
        private static int ID_COUNTER = 0;
        public ArrayList<Connection> connections = new ArrayList<>();

        private boolean selfState = false, input, output;
        protected float xPos, yPos;
        public final int ID;

        public Connection(){
            this(true, true);
        }

        public Connection(boolean in, boolean out){
            this.input = in;
            this.output = out;
            ID = ID_COUNTER++;
        }

        public Connection setPos(float xPos, float yPos){
            this.xPos = xPos;
            this.yPos = yPos;

            return this;
        }

        public void setState(boolean state){
            if(!output) return;

            this.selfState = state;
        }

        public void addConnection(Connection con){
            this.connections.add(con);
        }

        public void removeConnection(Connection con){
            this.connections.remove(con);
        }

        public void removeConnection(int id){
            int rmIndex = -1;
            for(int i = 0; i < connections.size(); i++) if(connections.get(i).ID == id) { rmIndex = i; break; }
            if(rmIndex > -1) connections.remove(rmIndex);
        }

        public boolean getState(){
            return getState(false);
        }

        public boolean getState(boolean ignoreSelf){
            if(!ignoreSelf && selfState) return true;
            if(!input) return false;

            boolean ret = false;

            for(int i = 0; i < connections.size(); i++){
                ret |= connections.get(i).selfState;
            }

            return ret;
        }
    }

    private static HashMap<String, SVGDocument> chipIcons = new HashMap<>();
    protected static SVGDocument getChipIcon(String id){
        return chipIcons.getOrDefault(id, null);
    }
    public static void loadChipIcons(){

    }
}
