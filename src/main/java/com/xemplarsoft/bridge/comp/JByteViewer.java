package com.xemplarsoft.bridge.comp;

import com.xemplarsoft.bridge.ModifiedListener;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;

public class JByteViewer extends JPanel implements KeyListener, MouseListener, MouseMotionListener {
    private final ArrayList<HighlightRegion> highlights = new ArrayList<>();
    private String text = "";
    private int caretPos, readingPos = -1, writingPos = -1, hoverPos, mouseX = -1, mouseY = -1;
    private int fontSize, columns = 16;
    private int startOffset = 0, maxSize = -1;
    private String title = "";
    private boolean showAscii = false;

    public JByteViewer(){
        addKeyListener(this);
        setFocusable(true);
        addMouseListener(this);
        addMouseMotionListener(this);

        setFont(Font.decode("Monospaced").deriveFont(Font.PLAIN, getFont().getSize()));
        fontSize = getFont().getSize();
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
        modified = false;
    }

    public void showAscii(boolean val){
        this.showAscii = val;
    }

    //Highlights from address, not text pos
    public void addHighlightRegion(Color c, int start, int end, String title, String desc){
        highlights.add(new HighlightRegion(c, start << 1, (end + 1) << 1, title, desc));//makes inclusive
    }

    public void setFixedSize(int size){
        maxSize = size * 2;
        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < maxSize; i++){
            builder.append("0");
        }
        this.text = builder.toString();
    }

    public void setOffset(int off){
        this.startOffset = off;
    }

    public void setColumns(int columns){
        this.columns = columns;
    }

    public void setTitle(String title){
        this.title = title;
    }

    public void setFont(Font font) {
        super.setFont(font);
        fontSize = font.getSize();
    }

    public void setText(String text){
        this.text = text;
    }
    private String first, last;
    public void append(char c){
        append(c + "");
    }
    public void append(String s){
        if(caretPos >= text.length()){
            this.text += s;
            caretPos = text.length();
            if(maxSize > 0){
                this.text = this.text.substring(0, maxSize);
            }
            return;
        }
        if(this.caretPos == 0){
            this.text = s + text.substring(insert_mode ? 0 : 1);
            caretPos = s.length();
            if(maxSize > 0){
                this.text = this.text.substring(0, maxSize);
            }
            return;
        }
        first = text.substring(0, caretPos);
        last = text.substring(caretPos + (insert_mode ? 0 : 1));
        this.text = first + s + last;
        caretPos = caretPos + 1;
        if(maxSize > 0){
            this.text = this.text.substring(0, maxSize);
        }

        int hzCaret = (caretPos >> 1) % (columns);

        if((hzCaret >= (xScroll + visibleColumns)) && xScroll < (columns - visibleColumns)){
            xScroll++;
        }
        if(hzCaret == 0){
            xScroll = 0;
        }
    }

    public static final Color COLOR_BG = new Color(1F, 1F, 1F, 1F);
    public static final Color COLOR_HEADER_BG = new Color(0F, 0F, 0F, 1F);
    public static final Color COLOR_CARET = new Color(0.1F, 0.8F, 0.1F, 0.5F);
    public static final Color COLOR_READ = new Color(0.1F, 0.8F, 0.1F, 0.5F);
    public static final Color COLOR_WRITE = new Color(1.0F, 0.2F, 0.2F, 0.5F);
    public static final Color COLOR_SCROLL_BAR = new Color(0.1F, 0.1F, 0.1F, 1F);

    public static final Color COLOR_TEXT_HEADER = new Color(1F, 1F, 1F, 1F);
    public static final Color COLOR_TEXT_INFO = new Color(0F, 0F, 0F, 1F);
    public static final Color COLOR_TEXT_DATA = new Color(0.3F, 0.3F, 0.3F, 1);

    public static final Color COLOR_HIGH_SELECT_DATA = new Color(0F, 0.5F, 1F, 0.5F);
    public static final Color COLOR_HIGH_MOUSE_DATA = new Color(0.8F, 0.1F, 0.1F, 0.5F);
    public static final Color COLOR_HIGH_MOUSE_HEADER = new Color(0.8F, 0.8F, 0.1F, 0.5F);

    public int visibleLines = 0, totalLines = 0, visibleColumns = 0, asciiColumns;
    public int yScroll, xScroll;

    private int getFontWidth(Graphics g, String text){
        return g.getFontMetrics().stringWidth(text);
    }
    protected void paintComponent(Graphics g) {
        asciiColumns = (columns / 4) + 2;

        visibleLines = getHeight() / fontSize;
        visibleColumns = ((getWidth() - (int)(fontSize * 1.25F)) / (fontSize * 2)) - 2;
        if(showAscii) visibleColumns -= asciiColumns;
        visibleColumns = Math.min(visibleColumns, columns);

        visibleLines -= 4;
        totalLines = (int)Math.ceil((float)(text.length() + 1) / (columns << 1));

        g.setColor(COLOR_BG);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setFont(getFont());


        //Draw ScrollBar
        float scrollBarHeight = (getHeight() - (fontSize * 4.5F));
        int xOff = (fontSize) * ((visibleColumns + 2) << 1) - 2;
        g.setColor(COLOR_SCROLL_BAR);
        g.drawRect(xOff, (int)(fontSize * 2.5F), fontSize, (int)scrollBarHeight - 1);
        if(totalLines > visibleLines){
            g.fillRect(xOff, (int)(fontSize * 2.5F) + ((int)(scrollBarHeight * ((float)yScroll / totalLines))), fontSize, (int)(scrollBarHeight * ((float)visibleLines / totalLines)));
        }

        //Render Info
        int rightOff = 0, rightMax = xOff + 2;
        if(showAscii) rightMax += (asciiColumns) * (fontSize << 1);

        g.setColor(COLOR_TEXT_INFO);
        String drawString = "Cursor: " + pad4HexNum((caretPos >> 1) + startOffset);
        rightOff += getFontWidth(g, drawString) + 4;
        g.drawString(drawString, rightMax - rightOff, getHeight() - (int)(fontSize * 0.75F));

        drawString = "Size: " + getByteSizeString(text.length() >> 1);
        rightOff += getFontWidth(g, drawString) + (fontSize);
        g.drawString(drawString, rightMax - rightOff, getHeight() - (int)(fontSize * 0.75F));

        drawString = "Mode: " + (insert_mode ? "Insert" : "Replace");
        g.drawString(drawString, (fontSize), getHeight() - (int)(fontSize * 0.75F));

        //Render Borders
        g.setColor(COLOR_HEADER_BG);
        g.fillRect(0, 0, (int)(fontSize * (((visibleColumns + asciiColumns) << 1) + 5.5F)), fontSize * 2);
        g.fillRect(0, 0, (int)(fontSize * 3.75F), fontSize * (visibleLines + 2) + 4);

        //Render Titles
        g.setColor(COLOR_TEXT_HEADER);
        if(showAscii){
            drawString = "ASCII";
            rightOff = getFontWidth(g, drawString) + 4;
            g.drawString(drawString, (fontSize) * ((visibleColumns + 5) << 1) + ((asciiColumns * fontSize) - rightOff) / 2, (int)(fontSize * 1.5F));
        }
        if(xScroll < (columns - visibleColumns)) {
            g.fillPolygon(new int[]{
                    fontSize * ((visibleColumns + 2) * 2) + 4,
                    fontSize * ((visibleColumns + 2) * 2) + 10,
                    fontSize * ((visibleColumns + 2) * 2) + 4
            }, new int[]{
                    (int)(fontSize * 1.5F) - 7,
                    (int)(fontSize * 1.5F) - 3,
                    (int)(fontSize * 1.5F) + 1
            }, 3);
        }

        if(xScroll > 0) {
            g.fillPolygon(new int[]{
                    fontSize * ((visibleColumns + 2) * 2) + 2,
                    fontSize * ((visibleColumns + 2) * 2) - 6,
                    fontSize * ((visibleColumns + 2) * 2) + 2
            }, new int[]{
                    (int)(fontSize * 1.5F) - 7,
                    (int)(fontSize * 1.5F) - 3,
                    (int)(fontSize * 1.5F) + 1
            }, 3);
        }
        drawString = title;
        g.drawString(drawString, fontSize, (int)(fontSize * 1.5F));

        //Render Row Index
        for(int i = 0; i < visibleLines; i++) {
            int x = fontSize;
            int y = fontSize * (i + 3);
            g.drawString(pad4HexNum(startOffset + (i + yScroll) * (columns)), x, y);
        }
        //Render Column Index
        for(int i = 0; i < Math.min(columns, visibleColumns); i++) {
            int x = fontSize * ((i + 2) * 2);
            int y = (int)(fontSize * 1.5F);
            g.drawString(pad2HexNum(i + xScroll), x, y);
        }

        //Render Selection Highlight
        if(selectionStartIndex > -1 && selectionEndIndex > -1){
            int st = Math.min(selectionStartIndex, selectionEndIndex);
            int ed = Math.max(selectionStartIndex, selectionEndIndex);

            g.setColor(COLOR_HIGH_SELECT_DATA);
            for(int i = st; i < ed; i++){
                if(i < yScroll * (columns << 1)) continue;
                if(i >= (yScroll + visibleLines) * (columns << 1)) continue;

                if((i >> 1) % columns < xScroll) continue;
                if((i >> 1) % columns > xScroll + visibleLines) continue;

                renderCursorPos(g, i, COLOR_HIGH_SELECT_DATA);
            }
        }

        //Render Highlights
        for(HighlightRegion region : highlights){
            g.setColor(region.color);
            int st = Math.min(region.start, region.end) - (startOffset << 1);
            int ed = Math.max(region.start, region.end) - (startOffset << 1);

            for(int i = st; i < ed; i++){
                renderCursorPos(g, i, region.color);
            }
        }

        //Render Text Data
        int start = yScroll * (columns << 1);
        int end = Math.min(start + visibleLines * (columns << 1), text.length());
        int index = 0;
        g.setColor(COLOR_TEXT_DATA);
        String val;
        for(int i = start; i < end; i+=2) {
            int x = fontSize * (((index) % (columns << 1)) + 4);
            int y = fontSize * (((index / 2) / columns) + 3);

            index += 2;

            x -= (xScroll & 0xFFFFFE) * (fontSize << 1) + (xScroll % 2) * (fontSize << 1);

            val = text.substring(i, i + 2 <= text.length() ? i + 2 : i + 1);
            if(showAscii){
                char b = (char) (Integer.parseInt(val, 16) & 0xFF);
                g.drawString(getPrintChar(b) + "", (int)((fontSize * 0.305F) * ((index - 1) % (columns << 1)) + ((visibleColumns + 2.5F) * (fontSize << 1))), y);
            }

            if(x < fontSize * 3) continue;
            if(x > ((visibleColumns + 1) * (fontSize << 1))) continue;

            g.drawString(val, x, y);
        }

        //Render Mouse Hover Highlight
        renderCursorPos(g, hoverPos, COLOR_HIGH_MOUSE_DATA);
        renderCursorPosASCII(g, hoverPos, COLOR_HIGH_MOUSE_DATA);

        //Render Caret Pos
        renderCursorPos(g, caretPos, COLOR_CARET);
        renderCursorPosASCII(g, caretPos, COLOR_CARET);

        if(readingPos > -1){
            renderCursorPos(g, readingPos, COLOR_READ);
            renderCursorPosASCII(g, readingPos, COLOR_READ);
        }
        if(writingPos > -1){
            renderCursorPos(g, writingPos, COLOR_WRITE);
            renderCursorPosASCII(g, writingPos, COLOR_WRITE);
        }
    }

    private void renderCursorPos(Graphics g, int pos, Color c){
        g.setColor(c);
        pos -= (xScroll << 1);
        int x = (pos % (columns << 1)) >> 1;
        int y = pos / (columns << 1);

        if(x < 0) return;
        if(x >= visibleColumns) return;

        if(y < yScroll) return;
        if(y >= yScroll + visibleLines) return;
        g.fillRect(((((pos % (columns << 1)) >> 1) << 2) + 8) * (fontSize / 2) + (int)((pos % 2) * (fontSize / 1.5F)), (((pos - (yScroll * (columns << 1))) / (columns << 1)) + 2) * fontSize + 1, (fontSize / 2) + 1, fontSize);
    }

    private void renderCursorPosASCII(Graphics g, int pos, Color c){
        if(!showAscii) return;

        g.setColor(c);
        int y = pos / (columns << 1) + 2;
        int x = ((pos % (columns << 1)) >> 1) << 1;
        x += 1;

        if(y < yScroll) return;
        if(y >= yScroll + visibleLines) return;
        y -= yScroll;

        g.fillRect((int)((fontSize * 0.305F) * x + ((visibleColumns + 2.5F) * (fontSize << 1))) + 1, y * fontSize, (int)(0.50F * fontSize), fontSize);
    }

    public char getPrintChar(char c){
        if(c < 32) return '.';
        if(c >= 127) return '.';

        return c;
    }

    public String getByteSizeString(int bytes){
        float size = bytes;
        int sizes = 0;
        while(size > 1000){
            size /= 1024;
            sizes++;
        }
        size = (float)Math.round(size * 1000) / 1000;

        switch (sizes){
            default: return (int)size + " bytes";
            case 1: return size + "KB";
            case 2: return size + "MB";
            case 3: return size + "GB";
            case 4: return size + "TB";
        }
    }

    protected static String padHex = "";
    public static String pad4HexNum(int in){
        padHex = Integer.toHexString(in).toUpperCase();
        return (padHex.length() == 1 ? "000" : (padHex.length() == 2 ? "00" : (padHex.length() == 3) ? "0" : "")) + padHex;
    }
    public static String pad2HexNum(int in){
        padHex = Integer.toHexString(in).toUpperCase();
        return ((padHex.length() == 1) ? "0" : "") + padHex;
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    public int getDataLength(){
        return text.length() >> 1;
    }

    public void clearData(){
        if(maxSize > -1){
            int len = getDataLength();
            for(int i = 0; i < len; i++){
                setDataAt(i + startOffset, 0);
            }
        } else {
            text = "";
        }
        repaint();
    }

    public void setData(byte[] data){
        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < data.length; i++){
            builder.append(HEX_ARRAY[(data[i] & 0xF0) >> 4]);
            builder.append(HEX_ARRAY[data[i] & 0x0F]);
        }
        this.text = builder.substring(0, Math.min(maxSize, builder.length()));

        if(maxSize > 0){
            builder = new StringBuilder();
            builder.append(text);
            while(builder.length() < maxSize){
                builder.append("0");
            }
            this.text = builder.toString();
        }
    }

    public void setReaderToAddress(int address){
        address -= startOffset;
        this.readingPos = (address << 1);
        repaint();
    }

    public void setWriterToAddress(int address){
        address -= startOffset;
        this.writingPos = (address << 1);
        repaint();
    }

    public void setCursorToAddress(int address){
        address -= startOffset;
        this.caretPos = (address << 1);

        while(this.caretPos - (yScroll * (columns << 1)) < 0){
            yScroll--;
        }
        while((caretPos - (yScroll * (columns << 1)) > (visibleLines - 1) * (columns << 1))){
            yScroll++;
        }

        repaint();
    }

    public byte[] getData(){
        byte[] ret = new byte[text.length() / 2];
        char u, d;
        int upper, lower;
        for(int i = 0; i < ret.length; i++){
            u = text.charAt((i << 1));
            d = text.charAt((i << 1) + 1);

            upper = u - '0';
            lower = d - '0';

            if(upper > 15) upper -= 7;
            if(upper > 15) upper -= 32;

            if(lower > 15) lower -= 7;
            if(lower > 15) lower -= 32;

            ret[i] = (byte)(((upper << 4) + lower) & 0xFF);
        }
        return ret;
    }

    public int getDataAt(int address){
        address -= startOffset;
        char u = text.charAt((address << 1));
        char d = text.charAt((address << 1) + 1);

        int upper = u - '0';
        int lower = d - '0';

        if(upper > 15) upper -= 7;
        if(lower > 15) lower -= 7;

        return ((upper << 4) + lower) & 0xFF;
    }

    public byte[] getDataPageAt(int address, int length){
        address -= startOffset;

        byte[] ret = new byte[length];
        char u, d;
        int upper, lower;
        for(int i = 0; i < ret.length; i++){
            u = text.charAt(((i + address) << 1));
            d = text.charAt(((i + address) << 1) + 1);

            upper = u - '0';
            lower = d - '0';

            if(upper > 15) upper -= 7;
            if(upper > 15) upper -= 32;

            if(lower > 15) lower -= 7;
            if(lower > 15) lower -= 32;

            ret[i] = (byte)(((upper << 4) + lower) & 0xFF);
        }
        return ret;
    }

    public void setDataAt(int address, int data){
        address -= startOffset;
        if(address >= text.length() / 2) return;

        String dat = pad2HexNum(data);
        char[] tableData = text.toCharArray();
        tableData[address * 2] = dat.charAt(0);
        tableData[address * 2 + 1] = dat.charAt(1);
        this.text = new String(tableData);

        repaint();
    }

    public void keyTyped(KeyEvent keyEvent) {
        if(keyEvent.isControlDown()) return;
    }

    protected boolean insert_mode = false;
    public void keyPressed(KeyEvent keyEvent) {
        if(keyEvent.isControlDown()) return;

        if(keyEvent.getKeyCode() == KeyEvent.VK_BACK_SPACE && this.text.length() > 0 && caretPos > 0){
            if(selectionEndIndex > -1){
                text = text.substring(0, selectionStartIndex) + text.substring(Math.min(selectionEndIndex, text.length() - 1));
                if(maxSize > 0){
                    StringBuilder builder = new StringBuilder();
                    builder.append(text);
                    while(builder.length() < maxSize){
                        builder.append("0");
                    }
                    this.text = builder.toString();
                }
                selectionStartIndex = -1;
                selectionEndIndex = -1;
                repaint();
                dispatchModified();
                return;
            }
            if(caretPos >= text.length()){
                text = text.substring(0, text.length() - 1);
                if(maxSize > 0){
                    this.text += "0";
                }
                caretPos = text.length();
                repaint();
                dispatchModified();
                return;
            }
            first = text.substring(0, caretPos - 1);
            last = text.substring(caretPos, text.length());
            this.text = first + last;
            caretPos--;
            if(maxSize > 0){
                this.text += "0";
            }
            repaint();
            dispatchModified();
            return;
        }
        if(keyEvent.getKeyCode() == KeyEvent.VK_DELETE && this.text.length() > 0){
            if(selectionEndIndex > -1){
                char[] arr = text.toCharArray();
                for(int i = selectionStartIndex; i < selectionEndIndex; i++){
                    if(i >= arr.length) continue;
                    arr[i] = '0';
                }
                this.text = new String(arr);
                repaint();
                dispatchModified();
                return;
            }
            if(caretPos >= text.length()){
                text = text.substring(0, text.length() - 1);
                if(maxSize > 0){
                    this.text += "0";
                }
                caretPos = text.length() + 1;
                repaint();
                dispatchModified();
                return;
            }
            first = text.substring(0, caretPos);
            last = text.substring(caretPos + 1, text.length());
            this.text = first + last;
            if(maxSize > 0){
                this.text += "0";
            }
            repaint();
            dispatchModified();
            return;
        }
        if(keyEvent.getKeyCode() == KeyEvent.VK_UP){
            if(keyEvent.isShiftDown() && selectionStartIndex == -1) selectionStartIndex = caretPos;
            if(caretPos - (columns << 1) < 0) return;
            caretPos -= (columns << 1);
            if(keyEvent.isShiftDown()){
                selectionEndIndex = caretPos;
            } else {
                selectionStartIndex = -1;
                selectionEndIndex = -1;
            }

            adjustYScroll(keyEvent);

            repaint();
            return;
        }
        if(keyEvent.getKeyCode() == KeyEvent.VK_DOWN){
            if(keyEvent.isShiftDown() && selectionStartIndex == -1) selectionStartIndex = caretPos;
            if(caretPos + (columns << 1) >= text.length()) return;
            caretPos += (columns << 1);
            if(keyEvent.isShiftDown()){
                selectionEndIndex = caretPos;
            } else {
                selectionStartIndex = -1;
                selectionEndIndex = -1;
            }

            adjustYScroll(keyEvent);

            repaint();
            return;
        }
        if(keyEvent.getKeyCode() == KeyEvent.VK_PAGE_DOWN){
            if(keyEvent.isShiftDown() && selectionStartIndex == -1) selectionStartIndex = caretPos;
            if(caretPos + ((columns << 1) * visibleLines) >= text.length()) {
                caretPos = maxSize == -1 ? text.length() : text.length() - 1;
            } else {
                caretPos += (columns << 1) * visibleLines;
            }
            if(keyEvent.isShiftDown()){
                selectionEndIndex = caretPos;
            } else {
                selectionStartIndex = -1;
                selectionEndIndex = -1;
            }

            adjustYPage(keyEvent);
            adjustXScroll(keyEvent);

            repaint();
            return;
        }
        if(keyEvent.getKeyCode() == KeyEvent.VK_PAGE_UP){
            if(keyEvent.isShiftDown() && selectionStartIndex == -1) selectionStartIndex = caretPos;
            if(caretPos - ((columns << 1) * columns) < 0) {
                caretPos = 0;
            } else {
                caretPos -= (columns << 1) * visibleLines;
                caretPos = Math.max(caretPos, 0);
            }
            if(keyEvent.isShiftDown()){
                selectionEndIndex = caretPos;
            } else {
                selectionStartIndex = -1;
                selectionEndIndex = -1;
            }

            adjustYPage(keyEvent);
            adjustXScroll(keyEvent);

            repaint();
            return;
        }
        if(keyEvent.getKeyCode() == KeyEvent.VK_LEFT){
            if(caretPos - 1 < 0) return;
            if(keyEvent.isShiftDown() && selectionStartIndex == -1) selectionStartIndex = caretPos;
            caretPos -= keyEvent.isAltDown() ? 2 : 1;;
            if(keyEvent.isShiftDown()){
                selectionEndIndex = caretPos;
            } else {
                selectionStartIndex = -1;
                selectionEndIndex = -1;
            }

            adjustXScroll(keyEvent);

            repaint();
            return;
        }
        if(keyEvent.getKeyCode() == KeyEvent.VK_RIGHT){
            if(caretPos + 1 > text.length()) return;
            if(keyEvent.isShiftDown() && selectionStartIndex == -1) selectionStartIndex = caretPos;
            caretPos += keyEvent.isAltDown() ? 2 : 1;
            if(keyEvent.isShiftDown()){
                selectionEndIndex = caretPos;
            } else {
                selectionStartIndex = -1;
                selectionEndIndex = -1;
            }

            adjustXScroll(keyEvent);

            repaint();
            return;
        }
        if(keyEvent.getKeyCode() == KeyEvent.VK_INSERT){
            insert_mode = !insert_mode;
            repaint();
            return;
        }

        String text = keyEvent.getKeyChar() + "";
        if(text.length() > 1) return;
        if(keyEvent.isControlDown()) return;
        if(keyEvent.isAltDown()) return;

        if(text.charAt(0) >= '0' && text.charAt(0) <= '9'){
            append(text);

            repaint();
            dispatchModified();
        }
        if(text.charAt(0) >= 'a' && text.charAt(0) <= 'f'){
            char c = (char)(text.charAt(0) + ('A' - 'a'));
            append(c);

            repaint();
            dispatchModified();
        }
        if(text.charAt(0) >= 'A' && text.charAt(0) <= 'F'){
            append(text);

            repaint();
            dispatchModified();
        }

    }

    private void adjustYScroll(KeyEvent keyEvent){
        if((caretPos - (yScroll * (columns << 1)) > (visibleLines - 1) * (columns << 1)) ||
                (keyEvent.isAltDown() && keyEvent.getKeyCode() == KeyEvent.VK_DOWN) && yScroll < (totalLines - visibleLines)){
            yScroll++;
        }
        if((caretPos - (yScroll * (columns << 1)) < 0) ||
                (keyEvent.isAltDown() && keyEvent.getKeyCode() == KeyEvent.VK_UP) && yScroll > 0){
            yScroll--;
        }
    }

    private void adjustYPage(KeyEvent keyEvent){
        if((caretPos - (yScroll * (columns << 1)) < 0) ||
                (keyEvent.isShiftDown() && keyEvent.getKeyCode() == KeyEvent.VK_PAGE_UP) && yScroll < (totalLines - visibleLines)){
            yScroll -= visibleLines;
            yScroll = Math.max(yScroll, 0);
        }
        if((caretPos - (yScroll * (columns << 1)) > (visibleLines - 1) * (columns << 1)) ||
                (keyEvent.isShiftDown() && keyEvent.getKeyCode() == KeyEvent.VK_PAGE_DOWN) && yScroll < (totalLines - visibleLines)){
            yScroll += visibleLines;
            yScroll = Math.min(yScroll, totalLines - visibleLines);
        }
    }

    private void adjustXScroll(KeyEvent keyEvent){
        int hzCaret = (caretPos >> 1) % (columns);

        if((hzCaret < xScroll || (keyEvent.isAltDown() && keyEvent.getKeyCode() == KeyEvent.VK_LEFT)) && xScroll > 0){
            xScroll--;
        } else if((hzCaret >= (xScroll + visibleColumns) || (keyEvent.isAltDown() && keyEvent.getKeyCode() == KeyEvent.VK_RIGHT)) && xScroll < (columns - visibleColumns)){
            xScroll++;
        }

        if(hzCaret == 0){
            xScroll = 0;
        } else if(hzCaret == (columns - 1)){
            xScroll = columns - visibleColumns;
        }
    }

    public void keyReleased(KeyEvent keyEvent) {
        if(keyEvent.isControlDown()) return;
    }


    protected int selectionStartIndex = -1, selectionEndIndex = -1;
    public void mouseClicked(MouseEvent mouseEvent) {
        requestFocus();
        mouseX = mouseEvent.getX() / (fontSize / 2);
        mouseY = mouseEvent.getY() / fontSize;
        mouseX -= 8;
        mouseY -= 2;
        mouseX = (mouseX + 1) / 2;

        int pos = getCursorIndex(mouseX, mouseY);
        if(pos != -1) caretPos = pos;
        repaint();
    }

    public int getCursorIndex(int mouseX, int mouseY){
        if(mouseX < 0 || mouseY < 0) return -1;

        int caretPos = Math.min(mouseX + (xScroll << 1), ((columns << 1) - 1)) + (mouseY + yScroll) * (columns << 1);
        if(caretPos > text.length()) caretPos = text.length();

        return caretPos;
    }

    @Override
    public void mousePressed(MouseEvent mouseEvent) {
        int mouseX = mouseEvent.getX() / (fontSize / 2);
        int mouseY = mouseEvent.getY() / fontSize;
        mouseX -= 8;
        mouseY -= 2;
        mouseX = (mouseX + 1) / 2;


        int pos = getCursorIndex(mouseX, mouseY);
        if(pos == -1) return;

        selectionEndIndex = -1;
        selectionStartIndex = pos;
    }

    @Override
    public void mouseReleased(MouseEvent mouseEvent) {
        if(selectionEndIndex == -1) selectionStartIndex = -1;
    }

    public void mouseEntered(MouseEvent mouseEvent) {
        setFocusable(true);
        requestFocus();
    }

    public void mouseExited(MouseEvent mouseEvent) {
        setFocusable(false);
        repaint();
        mouseX = -1;
        mouseY = -1;
    }


    public void mouseDragged(MouseEvent mouseEvent) {
        int mouseX = mouseEvent.getX() / (fontSize / 2);
        int mouseY = mouseEvent.getY() / fontSize;
        mouseX -= 8;
        mouseY -= 2;
        mouseX = (mouseX + 1) / 2;

        hoverPos = getCursorIndex(mouseX, mouseY);
        selectionEndIndex = hoverPos + 1;
        repaint();
    }

    public void mouseMoved(MouseEvent mouseEvent) {
        mouseX = mouseEvent.getX() / (fontSize / 2);
        mouseY = mouseEvent.getY() / fontSize;
        mouseX -= 8;
        mouseY -= 2;
        mouseX = (mouseX + 1) / 2;

        hoverPos = getCursorIndex(mouseX, mouseY);
        repaint();
    }

    private static class HighlightRegion{
        public Color color;
        public int start, end;
        public String title, desc;
        public HighlightRegion(Color c, int start, int end, String title, String desc){
            this.color = c;
            this.start = start;
            this.end = end;
            this.title = title;
            this.desc = desc;
        }
    }
}
