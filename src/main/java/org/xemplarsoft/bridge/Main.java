package org.xemplarsoft.bridge;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import org.xemplarsoft.bridge.assy.AssemblyError;
import org.xemplarsoft.bridge.comp.*;
import org.xemplarsoft.bridge.emu.VRAM;
import org.xemplarsoft.bridge.util.DataCRC;
import org.xemplarsoft.bridge.util.OSValidator;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.xemplarsoft.bridge.comp.JByteViewer.pad2HexNum;
import static org.xemplarsoft.bridge.comp.JByteViewer.pad4HexNum;

public class Main extends JFrame implements ModifiedListener, SerialPortDataListener {
    //UI Vars
    protected ScheduledExecutorService scheduler;
    protected Dimension pin_visualizer_size = new Dimension(450, 50);
    protected Dimension tool_size = new Dimension(32, 32);
    protected Dimension min_size = new Dimension(1080, 600);

    protected JMenuItem comms_comPort, comms_connected, comms_baudRate;
    protected JButton button_connectDev, button_playPause;
    protected JToolBar tools;

    protected JTabbedPane romPane, ramPane, outputPane;
    protected volatile JByteViewer RAM_VIEW, ROM_VIEW;
    protected PinVisualizer pinVisualizer;
    protected JSyntaxPane assyCode;
    protected JTextArea assemblyOutput;
    protected JTextPane serialOutput;
    protected StatusBar statusBar;
    protected Style textStyle;
    protected JPanel content;

    protected JTextField command_input;

    //COM Port Vars
    public static int[] baud_rates = new int[]{9600, 19200, 38400, 57600, 74880, 115200, 150000, 200000, 400000, 500000, 1000000};
    public static String[] comm_port_names;
    public static SerialPort[] comm_ports;

    protected volatile SerialPort SELECTED_COM;
    protected String SELECTED_COM_NAME;
    protected int SELECTED_BAUD = baud_rates[0];
    protected boolean CONNECTED = false, DEVICE_RESPONSIVE = false, CLOCK_RUNNING = false;

    // Assemble and Thread Vars
    protected long ASSEMBLE_COUNT = 0;
    private String TOOLCHAIN_EXECUTABLE;
    private Thread compileThread;
    private boolean runCompile = false, assembled = false;

    //Logging Vars
    protected boolean LOG_DATA_ERROR, LOG_ADDR_ERROR, LOG_MESSAGES, LOG_DECODE_ERROR;
    protected JCheckBoxMenuItem logs_messages, logs_data_error, logs_addr_error, logs_decode_error;
    protected long dataErrorCount, addrErrorCount;

    public Main(){
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                runCompile = false;
                flushSettings();
                super.windowClosing(e);
            }
        });

        scheduler = Executors.newSingleThreadScheduledExecutor();

        initUI();

        obtainComPorts();

        initProperties();
    }

    //Settings Methods
    private Properties settings;
    private File settingsFile;
    private void initProperties(){
        if(OSValidator.isWindows()) TOOLCHAIN_EXECUTABLE = "vasm6502_oldstyle.exe";
        if(OSValidator.isUnix() || OSValidator.isMac()) TOOLCHAIN_EXECUTABLE = "vasm6502_oldstyle";

        try {
            settingsFile = new File(System.getProperty("user.dir") + File.separator + "assets" + File.separator + "config.properties");
            boolean newConfig = false;
            if(!settingsFile.exists()) {
                if(!settingsFile.createNewFile()){
                    JOptionPane.showMessageDialog(this, "Cannot create settings file, settings will no be saved.", "Config File Creation Failed", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                newConfig = true;
            }

            settings = new Properties();
            settings.load(new FileReader(settingsFile));
            if(newConfig){
                flushSettings();
            } else {
                SELECTED_BAUD = Integer.parseInt((String)settings.get("connection.baud"));
                selectBaud(SELECTED_BAUD);

                SerialPort sp = getComPort((String)settings.get("connection.com"));
                if(sp != null) {
                    selectComPort(sp);
                } else {
                    SELECTED_COM_NAME = "";
                }

                String asmPath = (String)settings.get("asm.lastFile");
                if(asmPath.length() > 0) {
                    assemblySaveFile = new File(asmPath);
                    if(assemblySaveFile.exists()){
                        loadFileIntoAssy(assemblySaveFile);
                    } else {
                        assemblySaveFile = null;
                    }
                }

                String romPath = (String)settings.get("rom.lastFile");
                if(romPath.length() > 0) {
                    loadedRomFile = new File(romPath);
                    if(loadedRomFile.exists()){
                        loadFileIntoROM(loadedRomFile);
                    } else {
                        loadedRomFile = null;
                    }
                }

                ASSEMBLE_COUNT = Long.parseLong((String)settings.get("stats.assembleCount"));
            }
        } catch (Exception e){
            JOptionPane.showMessageDialog(this, "An error occurred: " + e.getMessage(), "Config File Read Failed", JOptionPane.WARNING_MESSAGE);
        }
    }
    private void flushSettings(){
        try {
            if (!settingsFile.exists()) {
                if (!settingsFile.createNewFile()) {
                    JOptionPane.showMessageDialog(this, "Cannot create settings file, settings will no be saved.", "Config File Creation Failed", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
            settings.put("connection.baud", Integer.toString(SELECTED_BAUD));
            settings.put("connection.com", SELECTED_COM_NAME == null ? "" : SELECTED_COM_NAME);
            settings.put("rom.lastFile", (loadedRomFile != null && loadedRomFile.exists()) ? loadedRomFile.getAbsolutePath() : "");
            settings.put("asm.lastFile", (assemblySaveFile != null && assemblySaveFile.exists()) ? assemblySaveFile.getAbsolutePath() : "");
            settings.put("stats.assembleCount", Long.toString(ASSEMBLE_COUNT));

            settings.store(new FileWriter(settingsFile), "Bridge6502 v0.01 Settings");
        } catch (Exception e){
            JOptionPane.showMessageDialog(this, "An error occurred: " + e.getMessage(), "Config File Read Failed", JOptionPane.WARNING_MESSAGE);
        }
    }

    //UI Creation Methods
    private void initUI(){
        content = new JPanel();
        content.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "save");
        content.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "saveAs");
        content.getActionMap().put("saveAs", new AbstractAction() {
            public void actionPerformed(ActionEvent actionEvent) {
                int index = romPane.getSelectedIndex();

                if(index == 0) saveRom(true);
                if(index == 1) saveAssembly(true);
            }
        });
        content.getActionMap().put("save", new AbstractAction() {
            public void actionPerformed(ActionEvent actionEvent) {
                int index = romPane.getSelectedIndex();

                if(index == 0) saveRom(false);
                if(index == 1) saveAssembly(false);
            }
        });
        GridBagLayout gbl = new GridBagLayout();
        gbl.columnWeights = new double[]{0, 1, 0};
        gbl.rowWeights = new double[]{0, 1, 0};
        gbl.columnWidths = new int[]{16 * (8 * 3) + 4, 300, -1, -1};
        gbl.rowHeights = new int[]{40, -1, 150, 24, 16};
        content.setLayout(gbl);

        initToolBar();

        GridBagConstraints gbc_vis = new GridBagConstraints();
        gbc_vis.gridx = 2; gbc_vis.gridy = 0; gbc_vis.gridwidth = 1; gbc_vis.gridheight = 1;
        gbc_vis.insets = new Insets(5, 0, 0, 5); gbc_vis.fill = GridBagConstraints.BOTH;
        pinVisualizer = new PinVisualizer();
        pinVisualizer.setFor6502();
        pinVisualizer.setMinimumSize(pin_visualizer_size);
        pinVisualizer.setPreferredSize(pin_visualizer_size);
        pinVisualizer.setMaximumSize(pin_visualizer_size);
        content.add(pinVisualizer, gbc_vis);

        GridBagConstraints gbc_ramTabs = new GridBagConstraints();
        gbc_ramTabs.gridx = 0; gbc_ramTabs.gridy = 1; gbc_ramTabs.gridwidth = 1; gbc_ramTabs.gridheight = 1;
        gbc_ramTabs.insets = new Insets(0, 5, 5, 5); gbc_ramTabs.fill = GridBagConstraints.BOTH;
        ramPane = new JTabbedPane();

        RAM_VIEW = new JByteViewer();
        RAM_VIEW.setTitle("RAM");
        RAM_VIEW.setFixedSize(0x800);
        RAM_VIEW.addHighlightRegion(new Color(1F, 1F, 0F, 0.25F), 0x0100, 0x017F, "Stack", "The 6502 uses these addresses for the stack.");
        RAM_VIEW.addHighlightRegion(new Color(0.9F, 0.6F, 1F, 0.325F), 0x0000, 0x00FF, "Zero Page", "The Zero Page can be accessed quicker than the rest of the address space.");
        ramPane.addTab("RAM View", RAM_VIEW);
        content.add(ramPane, gbc_ramTabs);

        GridBagConstraints gbc_romTabs = new GridBagConstraints();
        gbc_romTabs.gridx = 1; gbc_romTabs.gridy = 1; gbc_romTabs.gridwidth = 2; gbc_romTabs.gridheight = 1;
        gbc_romTabs.insets = new Insets(0, 0, 5, 5); gbc_romTabs.fill = GridBagConstraints.BOTH;
        romPane = new JTabbedPane();

        ROM_VIEW = new JByteViewer();
        ROM_VIEW.setText("");
        ROM_VIEW.setTitle("ROM");
        ROM_VIEW.showAscii(true);
        ROM_VIEW.setColumns(32);
        ROM_VIEW.setFixedSize(0x8000);
        ROM_VIEW.setOffset(0x8000);
        ROM_VIEW.addModifyListener(this);

        ROM_VIEW.addHighlightRegion(new Color(0.0F, 0.0F, 1F, 0.325F), 0xFFFA, 0xFFFB, "NMI Vector", "When the non-maskable interrupt is triggered, the CPU loads the value in these addresses to the program counter.");
        ROM_VIEW.addHighlightRegion(new Color(0.9F, 0.6F, 0.3F, 0.325F), 0xFFFC, 0xFFFD, "Restart Vector", "After Reset, the CPU loads the value in these addresses to the program counter.");
        ROM_VIEW.addHighlightRegion(new Color(0.9F, 0.0F, 0F, 0.325F), 0xFFFE, 0xFFFF, "BRK/IRQ Vector", "When the maskable interrupt is triggered, or the BRK command is executed, the CPU loads the value in these addresses to the program counter.");
        romPane.addTab("ROM View", ROM_VIEW);

        assyCode = new JSyntaxPane();
        assyCode.addModifyListener(this);
        JScrollPane scrollPane = new JScrollPane(assyCode);
        assyCode.init();
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        romPane.addTab("Assembly View", scrollPane);
        content.add(romPane, gbc_romTabs);

        GridBagConstraints gbc_outPane = new GridBagConstraints();
        gbc_outPane.weighty = 0;
        gbc_outPane.gridx = 0; gbc_outPane.gridy = 2; gbc_outPane.gridwidth = 3; gbc_outPane.gridheight = 1;
        gbc_outPane.insets = new Insets(0, 5, 5, 5); gbc_outPane.fill = GridBagConstraints.BOTH;
        outputPane = new JTabbedPane();

        serialOutput = new JTextPane();
        serialOutput.setEditable(false);
        textStyle = serialOutput.addStyle("Name", null);
        JScrollPane serScroll = new JScrollPane(serialOutput);

        assemblyOutput = new JTextArea();
        assemblyOutput.setEditable(false);
        JScrollPane assyScroll = new JScrollPane(assemblyOutput);

        outputPane.setMinimumSize(new Dimension(-1, 150));
        outputPane.setPreferredSize(new Dimension(-1, 150));
        outputPane.setMaximumSize(new Dimension(-1, 150));
        outputPane.addTab("Device Communications", serScroll);
        outputPane.addTab("Assembler Output", assyScroll);

        content.add(outputPane, gbc_outPane);

        GridBagConstraints gbc_command = new GridBagConstraints();
        gbc_command.weighty = 0;
        gbc_command.gridx = 0; gbc_command.gridy = 3; gbc_command.gridwidth = 3; gbc_command.gridheight = 1;
        gbc_command.insets = new Insets(0, 5, 5, 5); gbc_command.fill = GridBagConstraints.BOTH;
        JPanel command_panel = new JPanel();
        command_panel.setLayout(new BorderLayout());
        command_input = new JTextField();
        command_input.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                if(e.getKeyCode() == KeyEvent.VK_ENTER){
                    sendCommandRaw(command_input.getText().getBytes(StandardCharsets.US_ASCII));
                    command_input.setText("");
                }
            }
        });
        command_panel.add(command_input, BorderLayout.CENTER);
        JButton command_send = new JButton("Send");
        command_send.addActionListener((e) -> {
            sendCommandRaw(command_input.getText().getBytes(StandardCharsets.US_ASCII));
            command_input.setText("");
        });
        command_panel.add(command_send, BorderLayout.EAST);
        content.add(command_panel, gbc_command);

        GridBagConstraints gbc_status = new GridBagConstraints();
        gbc_status.weighty = 0;
        gbc_status.gridx = 0; gbc_status.gridy = 4; gbc_status.gridwidth = 3; gbc_status.gridheight = 1;
        gbc_status.insets = new Insets(0, 5, 5, 5); gbc_status.fill = GridBagConstraints.BOTH;
        statusBar = new StatusBar();
        statusBar.setMinimumSize(new Dimension(-1, 16));
        statusBar.setPreferredSize(new Dimension(-1, 16));
        statusBar.addItem("data_errors", "Data Errors", 0, false);
        statusBar.addItem("addr_errors", "Address Errors", 0, false);
        content.add(statusBar, gbc_status);

        content.setMinimumSize(min_size);
        content.setPreferredSize(min_size);
        this.setContentPane(content);

        initMenuBar();

        this.setIconImage(loadImageAsset("chip.png"));
        this.pack();
        this.setLocationRelativeTo(null);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setVisible(true);
    }
    private void initToolBar(){
        GridBagConstraints gbc_toolbar = new GridBagConstraints();
        gbc_toolbar.gridx = 0; gbc_toolbar.gridy = 0; gbc_toolbar.gridwidth = 2; gbc_toolbar.gridheight = 1;
        gbc_toolbar.insets = new Insets(5, 5, 5, 5); gbc_toolbar.fill = GridBagConstraints.BOTH;
        tools = new JToolBar();

        tools.add(createToolButton("load_rom.png", "Load ROM Image", (e) -> loadRom()));
        tools.add(createToolButton("save_rom.png", "Save ROM Image", (e) -> saveRom(false)));
        tools.add(createToolButton("clear_ram.png", "Clear RAM", (e) -> sendRequest("RAMCL")));
        tools.addSeparator();
        button_connectDev = createToolButton("disconnected.png", "Connect to Device", (e) -> connectToDevice());
        tools.add(button_connectDev);
        tools.add(createToolButton("reset.png", "Reset Device", (e) -> sendRequest("RESET")));
        button_playPause = createToolButton("play.png", "Start Clock", (e) -> toggleClock());
        tools.add(button_playPause);
        tools.add(createToolButton("pulse.png", "Pulse Clock", (e) -> sendRequest("CLOCKPULSE")));
        tools.add(createToolButton("interval.png", "Set Clock Interval", (e) -> adjustInterval()));
        tools.addSeparator();

        tools.add(createToolButton("load_file.png", "Load Assembly", (e) -> loadAssembly()));
        tools.add(createToolButton("save_file.png", "Save Assembly", (e) -> saveAssembly(false)));
        tools.add(createToolButton("assemble.png", "Assemble Code", (e) -> assembleCode()));
        tools.add(createToolButton("send_to_rom.png", "Send to ROM", (e) -> sendBinToROM()));
        tools.add(createToolButton("run_on_device.png", "Reset Device and run ROM", (e) -> runCodeOnROM()));
        tools.add(createToolButton("assemble_and_run.png", "Assemble Code, Send to ROM, Reset device and run ROM", (e) -> assembleRunOnROM()));

        tools.add(createToolButton("pulse.png", "Test Button", (e) -> {
            sendPageWrite(0x8000);
        }));


        content.add(tools, gbc_toolbar);
    }
    private void initMenuBar() {
        JMenuBar menu = new JMenuBar();
        JMenu menu_file = new JMenu("File");

        JMenuItem file_rom_clear = new JMenuItem("Clear ROM");
        file_rom_clear.addActionListener((e) -> clearRom());
        menu_file.add(file_rom_clear);

        JMenuItem file_rom_load = new JMenuItem("Load ROM");
        file_rom_load.addActionListener((e) -> loadRom());
        menu_file.add(file_rom_load);

        JMenuItem file_rom_save = new JMenuItem("Save ROM");
        file_rom_save.addActionListener((e) -> saveRom(false));
        menu_file.add(file_rom_save);

        JMenuItem file_rom_saveAs = new JMenuItem("Save ROM As");
        file_rom_saveAs.addActionListener((e) -> saveRom(true));
        menu_file.add(file_rom_saveAs);

        menu_file.add(new JSeparator(JSeparator.HORIZONTAL));

        JMenuItem file_ram_clear = new JMenuItem("Clear RAM");
        file_ram_clear.addActionListener((e) -> clearRam());
        menu_file.add(file_ram_clear);

        JMenuItem file_ram_load = new JMenuItem("Load RAM");
        file_ram_load.addActionListener((e) -> loadRam());
        menu_file.add(file_ram_load);

        JMenuItem file_ram_save = new JMenuItem("Save RAM");
        file_ram_save.addActionListener((e) -> saveRam());
        menu_file.add(file_ram_save);

        JMenu menu_comms = new JMenu("Device");
        comms_connected = new JMenuItem("Connected: false");
        comms_connected.addActionListener((e) -> connectToDevice());
        menu_comms.add(comms_connected);
        comms_comPort = new JMenu("COM Port: none");
        menu_comms.add(comms_comPort);
        comms_baudRate = new JMenu("Baud Rate: " + baud_rates[0]);
        for (int i = 0; i < baud_rates.length; i++) {
            JMenuItem select = new JMenuItem("" + baud_rates[i]);
            int finalI = i;
            select.addActionListener((e) -> selectBaud(baud_rates[finalI]));
            comms_baudRate.add(select);
        }

        menu_comms.add(comms_baudRate);
        menu_comms.add(new JSeparator(JSeparator.HORIZONTAL));
        JMenuItem refresh = new JMenuItem("Refresh COM Ports");
        refresh.addActionListener((e) -> obtainComPorts());
        menu_comms.add(refresh);


        JMenu menu_commands = new JMenu("Commands");

        JMenuItem command_awake = new JMenuItem("Awake");
        command_awake.addActionListener((e) -> sendRequest("AWAKE"));
        menu_commands.add(command_awake);

        JMenuItem request_number = new JMenuItem("Save Settings");
        request_number.addActionListener((e) -> sendRequest("SAVE"));
        menu_commands.add(request_number);


        JMenu menu_assemble = new JMenu("Assemble");

        JMenuItem assemble_assy = new JMenuItem("Assemble Only");
        assemble_assy.addActionListener((e) -> assembleCode());
        menu_assemble.add(assemble_assy);

        JMenuItem assemble_send_rom = new JMenuItem("Send to ROM");
        assemble_send_rom.addActionListener((e) -> sendBinToROM());
        menu_assemble.add(assemble_send_rom);

        JMenuItem assemble_run_rom = new JMenuItem("Run on Laptop ROM");
        assemble_run_rom.addActionListener((e) -> runCodeOnROM());
        menu_assemble.add(assemble_run_rom);

        JMenuItem assemble_run_dev = new JMenuItem("Run on Device ROM");
        assemble_run_dev.addActionListener((e) -> runCodeOnDev());
        menu_assemble.add(assemble_run_dev);


        JMenu menu_logs = new JMenu("Log Settings");

        logs_messages = new JCheckBoxMenuItem("Messages");
        logs_messages.setActionCommand(2 + "");
        logs_messages.addActionListener((e) -> handleLogSetting(logs_messages));
        menu_logs.add(logs_messages);

        logs_data_error = new JCheckBoxMenuItem("Data Errors");
        logs_data_error.setActionCommand(0 + "");
        logs_data_error.addActionListener((e) -> handleLogSetting(logs_data_error));
        menu_logs.add(logs_data_error);

        logs_addr_error = new JCheckBoxMenuItem("Address Errors");
        logs_addr_error.setActionCommand(1 + "");
        logs_addr_error.addActionListener((e) -> handleLogSetting(logs_addr_error));
        menu_logs.add(logs_addr_error);

        logs_decode_error = new JCheckBoxMenuItem("Decode Errors");
        logs_decode_error.setActionCommand(3 + "");
        logs_decode_error.addActionListener((e) -> handleLogSetting(logs_decode_error));
        menu_logs.add(logs_decode_error);

        menu.add(menu_file);
        menu.add(menu_comms);
        menu.add(menu_commands);
        menu.add(menu_assemble);
        menu.add(menu_logs);
        this.setJMenuBar(menu);
    }

    public void handleLogSetting(JCheckBoxMenuItem item){
        boolean state = item.getState();
        int logState = Integer.parseInt(item.getActionCommand());

        switch (logState){
            case 0:
                LOG_DATA_ERROR = state;
                break;
            case 1:
                LOG_ADDR_ERROR = state;
                break;
            case 2:
                LOG_MESSAGES = state;
                break;
            case 3:
                LOG_DECODE_ERROR = state;
                break;
        }
    }
    public void modified(JComponent component) {
        if(component == ROM_VIEW) setTabModified(0, true);
        if(component == assyCode) setTabModified(1, true);
    }
    private void setTabModified(int index, boolean modified){
        romPane.setTitleAt(index, setModifiedString(romPane.getTitleAt(index), modified));
    }

    public static String setModifiedString(String in, boolean modified){
        if(in.indexOf("*") == in.length() - 1) in = in.substring(0, in.length() - 1);
        if(modified) in = in + "*";

        return in;
    }
    public BufferedImage loadImageAsset(String name){
        String path = System.getProperty("user.dir") + File.separator + "assets" + File.separator + "images" + File.separator + name;
        try{
            return ImageIO.read(new File(path));
        } catch (Exception e){
            System.out.println(path);
            e.printStackTrace();
        }
        return null;
    }
    public JImageButton createToolButton(String icon, String toolTip, ActionListener listener){
        JImageButton b = new JImageButton(loadImageAsset(icon));
        b.setMinimumSize(tool_size);
        b.setPreferredSize(tool_size);
        b.setMaximumSize(tool_size);
        b.addActionListener(listener);
        b.setToolTipText(toolTip);

        return b;
    }

    //Data IO Methods
    private File assemblySaveFile, loadedRomFile;

    private int saveRom(boolean saveAs){
        if(!saveAs && loadedRomFile != null){
            if(saveFileFromROM(loadedRomFile)){
                return JFileChooser.APPROVE_OPTION;
            }
        }

        JFileChooser jfc = new JFileChooser();
        if(loadedRomFile != null) jfc.setCurrentDirectory(loadedRomFile.getParentFile());
        jfc.setMultiSelectionEnabled(false);
        jfc.setDialogTitle("ROM Export");
        jfc.setApproveButtonText("Export");
        jfc.setCurrentDirectory(new File(System.getProperty("user.dir")));
        jfc.addChoosableFileFilter(new FileFilter() {
            public boolean accept(File file) {
                int periodIndex = file.getName().lastIndexOf(".");
                if(periodIndex == -1) return file.isDirectory();

                String ext = file.getName().substring(periodIndex + 1);
                return ext.equals("bin");
            }

            public String getDescription() {
                return "Binaries (*.bin)";
            }
        });


        int status = jfc.showSaveDialog(this);
        if(status == JFileChooser.APPROVE_OPTION){
            loadedRomFile = jfc.getSelectedFile();
            if(saveFileFromROM(loadedRomFile)){
                JOptionPane.showMessageDialog(this, "ROM Saved", "Your ROM has been successfully saved.", JOptionPane.INFORMATION_MESSAGE);
                return JFileChooser.APPROVE_OPTION;
            } else {
                return JFileChooser.CANCEL_OPTION;
            }
        }
        return JFileChooser.CANCEL_OPTION;
    }
    private int saveRam(){
        JFileChooser jfc = new JFileChooser();
        if(loadedRomFile != null) jfc.setCurrentDirectory(loadedRomFile.getParentFile());
        jfc.setMultiSelectionEnabled(false);
        jfc.setDialogTitle("ROM Export");
        jfc.setApproveButtonText("Export");
        jfc.setCurrentDirectory(new File(System.getProperty("user.dir")));
        jfc.addChoosableFileFilter(new FileFilter() {
            public boolean accept(File file) {
                int periodIndex = file.getName().lastIndexOf(".");
                if(periodIndex == -1) return file.isDirectory();

                String ext = file.getName().substring(periodIndex + 1);
                return ext.equals("bin");
            }

            public String getDescription() {
                return "Binaries (*.bin)";
            }
        });


        int status = jfc.showSaveDialog(this);
        if(status == JFileChooser.APPROVE_OPTION){
            File loadedRamFile = jfc.getSelectedFile();
            if(saveFileFromRAM(loadedRamFile)){
                JOptionPane.showMessageDialog(this, "RAM Saved", "RAM state has been successfully saved.", JOptionPane.INFORMATION_MESSAGE);
                return JFileChooser.APPROVE_OPTION;
            } else {
                return JFileChooser.CANCEL_OPTION;
            }
        }
        return JFileChooser.CANCEL_OPTION;
    }
    private void clearRam(){
        for(int i = 0; i < RAM_VIEW.getDataLength(); i++){
            RAM_VIEW.setDataAt(i, 0);
        }
    }
    private void loadRom(){
        JFileChooser jfc = new JFileChooser();
        jfc.setMultiSelectionEnabled(false);
        jfc.setDialogTitle("ROM Import");
        jfc.setApproveButtonText("Load");
        jfc.setCurrentDirectory(new File(System.getProperty("user.dir")));
        jfc.removeChoosableFileFilter(jfc.getChoosableFileFilters()[0]);
        jfc.addChoosableFileFilter(new FileFilter() {
            public boolean accept(File file) {
                int periodIndex = file.getName().lastIndexOf(".");
                if(periodIndex == -1) return file.isDirectory();

                String ext = file.getName().substring(periodIndex + 1);
                return ext.equals("bin") || ext.equals("hex");
            }

            public String getDescription() {
                return "Binaries (*.bin, *.hex)";
            }
        });


        int status = jfc.showOpenDialog(this);
        if(status == JFileChooser.APPROVE_OPTION){
            loadedRomFile = jfc.getSelectedFile();
            loadFileIntoROM(loadedRomFile);
        }
    }
    private void loadRam(){
        JFileChooser jfc = new JFileChooser();
        jfc.setMultiSelectionEnabled(false);
        jfc.setDialogTitle("RAM State Import");
        jfc.setApproveButtonText("Load");
        jfc.setCurrentDirectory(new File(System.getProperty("user.dir")));
        jfc.removeChoosableFileFilter(jfc.getChoosableFileFilters()[0]);
        jfc.addChoosableFileFilter(new FileFilter() {
            public boolean accept(File file) {
                int periodIndex = file.getName().lastIndexOf(".");
                if(periodIndex == -1) return file.isDirectory();

                String ext = file.getName().substring(periodIndex + 1);
                return ext.equals("bin") || ext.equals("hex");
            }

            public String getDescription() {
                return "Binaries (*.bin, *.hex)";
            }
        });


        int status = jfc.showOpenDialog(this);
        if(status == JFileChooser.APPROVE_OPTION){
            loadFileIntoRAM(jfc.getSelectedFile());
        }
    }
    private void clearRom(){
        for(int i = 0; i < ROM_VIEW.getDataLength(); i++){
            ROM_VIEW.setDataAt(i, 0);
        }
    }
    private int saveAssembly(boolean saveAs){
        if(!saveAs && assemblySaveFile != null){
            return saveFileFromAssy(assemblySaveFile) ? JFileChooser.APPROVE_OPTION : JFileChooser.CANCEL_OPTION;
        }

        JFileChooser jfc = new JFileChooser();
        if(assemblySaveFile != null) jfc.setCurrentDirectory(assemblySaveFile.getParentFile());
        jfc.setMultiSelectionEnabled(false);
        jfc.setDialogTitle("Assembly File Save");
        jfc.setApproveButtonText("Save");
        jfc.setCurrentDirectory(new File(System.getProperty("user.dir")));
        jfc.addChoosableFileFilter(new FileFilter() {
            public boolean accept(File file) {
                int periodIndex = file.getName().lastIndexOf(".");
                if(periodIndex == -1) return file.isDirectory();

                String ext = file.getName().substring(periodIndex + 1);
                return ext.equals("s") || ext.equals("S") || ext.equals("asm");
            }

            public String getDescription() {
                return "Assembly Files (*.asm, *.s, *.S)";
            }
        });


        int status = jfc.showSaveDialog(this);
        if(status == JFileChooser.APPROVE_OPTION){
            assemblySaveFile = jfc.getSelectedFile();
            String path = assemblySaveFile.getAbsolutePath();

            if(path.lastIndexOf(".") < path.lastIndexOf(File.separator)){
                path += ".s";
            }

            assemblySaveFile = new File(path);
            return saveFileFromAssy(assemblySaveFile) ? JFileChooser.APPROVE_OPTION : JFileChooser.CANCEL_OPTION;
        }

        return JFileChooser.CANCEL_OPTION;
    }
    private void loadAssembly(){
        JFileChooser jfc = new JFileChooser();
        jfc.setMultiSelectionEnabled(false);
        jfc.setDialogTitle("Assembly File Load");
        jfc.setApproveButtonText("Load");
        jfc.setCurrentDirectory(new File(System.getProperty("user.dir")));
        jfc.removeChoosableFileFilter(jfc.getChoosableFileFilters()[0]);
        jfc.addChoosableFileFilter(new FileFilter() {
            public boolean accept(File file) {
                int periodIndex = file.getName().lastIndexOf(".");
                if(periodIndex == -1) return file.isDirectory();

                String ext = file.getName().substring(periodIndex + 1);
                return ext.equals("s") || ext.equals("S") || ext.equals("asm");
            }

            public String getDescription() {
                return "Assembly Files (*.asm, *.s, *.S)";
            }
        });


        int status = jfc.showOpenDialog(this);
        if(status == JFileChooser.APPROVE_OPTION){
            assemblySaveFile = jfc.getSelectedFile();
            loadFileIntoAssy(assemblySaveFile);
        }
    }
    private boolean saveFileFromROM(File f){
        try {
            FileOutputStream writer = new FileOutputStream(f);
            byte[] data = ROM_VIEW.getData();

            writer.write(data);
            writer.flush();
            writer.close();
            setTabModified(0, false);
            ROM_VIEW.resetModified();
            return true;
        } catch (Exception e){
            JOptionPane.showMessageDialog(this, "Error saving file: " + e.getMessage(), "Error saving ROM image!", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            return false;
        }
    }
    private boolean saveFileFromRAM(File f){
        try {
            FileOutputStream writer = new FileOutputStream(f);
            byte[] data = RAM_VIEW.getData();

            writer.write(data);
            writer.flush();
            writer.close();
            RAM_VIEW.resetModified();
            return true;
        } catch (Exception e){
            JOptionPane.showMessageDialog(this, "Error saving file: " + e.getMessage(), "Error saving RAM state!", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            return false;
        }
    }
    private boolean saveFileFromAssy(File f){
        try{
            FileWriter writer = new FileWriter(f);
            String data = assyCode.getText();
            writer.write(data);
            writer.flush();
            writer.close();
            setTitle(f.getAbsolutePath());
            setTabModified(1, false);
            assyCode.resetModified();
            return true;
        } catch (Exception e){
            JOptionPane.showMessageDialog(this, "Error saving file: " + e.getMessage(), "Error saving assembly code!", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }
    private void loadFileIntoROM(File f){
        try {
            FileInputStream reader = new FileInputStream(f);
            ArrayList<Byte> data = new ArrayList<>();
            int read;
            while((read = reader.read()) != -1){
                data.add((byte) (read & 0xFF));
            }
            byte[] dat = new byte[data.size()];
            for(int i = 0; i < dat.length; i++){
                dat[i] = data.get(i);
            }
            ROM_VIEW.setData(dat);
        } catch (Exception e){
            JOptionPane.showMessageDialog(this, "Error loading file: " + e.getMessage(), "Error loading ROM image!", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }
    private void loadFileIntoRAM(File f){
        try {
            FileInputStream reader = new FileInputStream(f);
            ArrayList<Byte> data = new ArrayList<>();
            int read;
            while((read = reader.read()) != -1){
                data.add((byte) (read & 0xFF));
            }
            byte[] dat = new byte[data.size()];
            for(int i = 0; i < dat.length; i++){
                dat[i] = data.get(i);
            }
            if(dat.length > RAM_VIEW.getDataLength()){
                JOptionPane.showMessageDialog(this, "RAM file is larger than simulated RAM!", "Error loading RAM state!", JOptionPane.ERROR_MESSAGE);
                return;
            }
            RAM_VIEW.setData(dat);
        } catch (Exception e){
            JOptionPane.showMessageDialog(this, "Error loading file: " + e.getMessage(), "Error loading RAM state!", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }
    private void loadFileIntoAssy(File f){
        try {
            assyCode.setText("");
            FileReader reader = new FileReader(f);
            StringBuilder builder = new StringBuilder();
            int c;
            while((c = reader.read()) != -1){
                builder.append((char)c);
            }
            assyCode.setText(builder.toString());
            setTitle(f.getAbsolutePath());

            romPane.setSelectedIndex(1);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private void loadFileIntoVRAM(VRAM vram, File f){
        try {
            FileInputStream reader = new FileInputStream(f);
            ArrayList<Byte> data = new ArrayList<>();
            int read;
            while((read = reader.read()) != -1){
                data.add((byte) (read & 0xFF));
            }
            byte[] dat = new byte[data.size()];
            for(int i = 0; i < dat.length; i++){
                dat[i] = data.get(i);
            }
            vram.setMemory(dat);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    //Assembly Methods
    private int ROR_COUNTER = -1;
    private Runnable compileCode = () -> {
        String toolchain_path = System.getProperty("user.dir") + File.separator + "assets" +
                File.separator + "toolchain" + File.separator + TOOLCHAIN_EXECUTABLE;
        String inputFilePath = assemblySaveFile.getAbsolutePath();
        String outputFilePath = inputFilePath.substring(0, Math.min(inputFilePath.lastIndexOf("."), inputFilePath.length()));
        outputFilePath += ".bin";

        try {
            ASSEMBLE_COUNT++;

            Process process = new ProcessBuilder(toolchain_path, "-Fbin", "-dotdir", "-o", outputFilePath, inputFilePath).start();
            InputStream nis = process.getInputStream();
            InputStream eis = process.getErrorStream();
            InputStreamReader nisr = new InputStreamReader(nis);
            InputStreamReader eisr = new InputStreamReader(eis);
            int c;
            StringBuilder errorStream = new StringBuilder();

            while(runCompile && (process.isAlive())){
                if(((c = nisr.read()) != -1)){
                    assemblyOutput.append((char)c + "");
                }
                if(((c = eisr.read()) != -1)){
                    assemblyOutput.append((char)c + "");
                    errorStream.append((char)c);
                }
                assemblyOutput.setCaretPosition(assemblyOutput.getText().length());
            }
            while(runCompile && (((c = nisr.read()) != -1))){
                assemblyOutput.append((char)c + "");
                assemblyOutput.setCaretPosition(assemblyOutput.getText().length());
            }
            while(runCompile && (((c = eisr.read()) != -1))){
                assemblyOutput.append((char)c + "");
                errorStream.append((char)c);
                assemblyOutput.setCaretPosition(assemblyOutput.getText().length());
            }
            nisr.close();
            eisr.close();

            String errors = errorStream.toString();
            int lastIndex = -1;
            final ArrayList<AssemblyError> errorList = new ArrayList<>();

            while((lastIndex = errors.indexOf("warning", lastIndex + 1)) != -1){
                String errorLine = errors.substring(lastIndex, errors.indexOf("\n", lastIndex));
                int lineIndex = errorLine.indexOf("line ") + 5;
                int lineNumber = Integer.parseInt(errorLine.substring(lineIndex, errorLine.indexOf(" of ", lineIndex)));
                lineIndex = errorLine.indexOf("\": ", lineIndex);
                String fileName = errorLine.substring(errorLine.indexOf("of \"") + 4, lineIndex);
                String errorMessage = errorLine.substring(lineIndex + 3);
                errorList.add(new AssemblyError(lineNumber, fileName, errorMessage, AssemblyError.Type.WARNING));
            }

            while((lastIndex = errors.indexOf("error", lastIndex + 1)) != -1){
                String errorLine = errors.substring(lastIndex, errors.indexOf("\n", lastIndex));
                int lineIndex = errorLine.indexOf("line ") + 5;
                int lineNumber = Integer.parseInt(errorLine.substring(lineIndex, errorLine.indexOf(" of ", lineIndex)));
                lineIndex = errorLine.indexOf("\": ", lineIndex);
                String fileName = errorLine.substring(errorLine.indexOf("of \"") + 4, lineIndex);
                String errorMessage = errorLine.substring(lineIndex + 3);
                errorList.add(new AssemblyError(lineNumber, fileName, errorMessage, AssemblyError.Type.ERROR));
            }
            SwingUtilities.invokeLater(() -> {
                postAssemble(errorList);
            });

            runCompile = false;
            process.destroyForcibly();
        } catch (Exception e){
            e.printStackTrace();
        }
    };

    private void postAssemble(ArrayList<AssemblyError> errorList){
        assyCode.clearErrors();
        assyCode.addErrors(errorList);

        if(errorList.size() == 0 && ROR_COUNTER == 0){
            sendBinToROM();
            runCodeOnROM();
        }

        if(errorList.size() == 0) return;
        if(ROR_COUNTER == 0){
            ROR_COUNTER = -1;
            JOptionPane.showMessageDialog(this, "Could not run, assembled program has errors. ", "Assembly Error", JOptionPane.ERROR_MESSAGE);
            outputPane.setSelectedIndex(1);
        }

        for(AssemblyError error : errorList){
            error.printToErr();
        }
    }
    private void assembleCode(){
        if(assemblySaveFile == null || !assemblySaveFile.exists()){
            JOptionPane.showMessageDialog(this,
                    "You must save your assembly file to compile it.",
                    "Assembly Not Saved", JOptionPane.WARNING_MESSAGE);
            int status = saveAssembly(true);
            if(status != JFileChooser.APPROVE_OPTION || !assemblySaveFile.exists()) {
                ROR_COUNTER = -1;
                return;
            }
        } else {
            saveAssembly(false);
        }

        if(compileThread != null && compileThread.isAlive()){
            runCompile = false;
            try {
                compileThread.join();
            } catch (Exception e){
                e.printStackTrace();
            }
        }

        outputPane.setSelectedIndex(1);
        assemblyOutput.setText(""); //Clear prev output

        runCompile = true;
        compileThread = new Thread(compileCode);
        compileThread.start();
    }
    private void sendBinToROM(){
        if(assemblySaveFile == null || !assemblySaveFile.exists()){
            JOptionPane.showMessageDialog(this,
                    "You must save your assembly file, and assemble it before sending it to the ROM.",
                    "Assembly Not Saved", JOptionPane.WARNING_MESSAGE);
            ROR_COUNTER = -1;
            return;
        }

        String inputFilePath = assemblySaveFile.getAbsolutePath();
        String outputFilePath = inputFilePath.substring(0, Math.min(inputFilePath.lastIndexOf("."), inputFilePath.length()));
        outputFilePath += ".bin";

        loadedRomFile = new File(outputFilePath);

        if(!loadedRomFile.exists()){
            JOptionPane.showMessageDialog(this,
                    "You must assemble your program before it can be sent to the ROM.",
                    "Program not Assembled", JOptionPane.WARNING_MESSAGE);
            ROR_COUNTER = -1;
            return;
        }

        loadFileIntoROM(loadedRomFile);
        romPane.setSelectedIndex(0);
    }
    private void runCodeOnROM(){
        if(!CONNECTED){
            ROR_COUNTER = -1;
            JOptionPane.showMessageDialog(this, "You must be connected to your device before running.", "Device Not Connected", JOptionPane.ERROR_MESSAGE);
            return;
        }

        ROR_COUNTER = 0;
        sendRequest("INHROM", "1");
        sendRequest("CLOCK", "1");
    }
    private void runCodeOnDev(){
        if(!CONNECTED){
            ROR_COUNTER = -1;
            JOptionPane.showMessageDialog(this, "You must be connected to your device before running.", "Device Not Connected", JOptionPane.ERROR_MESSAGE);
            return;
        }

        ROR_COUNTER = 0;
        sendRequest("INHROM", "0");
        sendRequest("CLOCK", "1");
    }
    private void assembleRunOnROM(){
        if(!CONNECTED){
            ROR_COUNTER = -1;
            JOptionPane.showMessageDialog(this, "You must be connected to your device before running.", "Device Not Connected", JOptionPane.ERROR_MESSAGE);
            return;
        }

        ROR_COUNTER = 0;
        assembleCode();
    }

    //Device Methods
    public void adjustInterval(){
        String time = JOptionPane.showInputDialog(this, "Set Clock Interval in us", 200);
        try{
            int us = Integer.parseInt(time);
            sendRequest("SETT", "TICK", (us + ""));
        } catch (Exception e){
            JOptionPane.showMessageDialog(this, "Clock Interval Invalid", "Invalid Input", JOptionPane.ERROR_MESSAGE);
        }
    }
    public void toggleClock(){
        boolean toggle = !CLOCK_RUNNING;
        sendRequest("CLOCK", toggle ? "1" : "0");
    }
    public void connectToDevice(){
        if(CONNECTED){
            disconnectFromDevice();
            return;
        }
        if(SELECTED_COM == null){
            JOptionPane.showMessageDialog(this, "Please select a COM port with the option below this one.", "No COM Selected", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if(SELECTED_COM.isOpen()){
            if(!SELECTED_COM.closePort()) {
                JOptionPane.showMessageDialog(this, "Please select a different COM port, this one is busy.", "COM Busy", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        if(SELECTED_COM.openPort()){
            Runnable r = new Runnable() {
                public void run() {
                    sendRequest("AWAKE");
                }
            };
            scheduler.schedule(r, 1, TimeUnit.SECONDS);
        }
    }
    public void disconnectFromDevice(){
        CONNECTED = false;
        DEVICE_RESPONSIVE = false;
        SELECTED_COM.closePort();
        ((JImageButton)button_connectDev).setImage(loadImageAsset("disconnected.png"));
    }
    public void selectBaud(int baud){
        if(SELECTED_COM != null) {
            if(CONNECTED){
                sendRequest("SETT", "BAUD", Integer.toString(baud));
            } else {
                setBaud(baud);
                SELECTED_COM.setBaudRate(SELECTED_BAUD);
            }
        } else {
            setBaud(baud);
        }
    }
    public void setBaud(int baud){
        SELECTED_BAUD = baud;
        comms_baudRate.setText("Baud Rate: " + SELECTED_BAUD);
        log("DEVICE BAUD SET TO: " + SELECTED_BAUD, LOG_REQUEST_COMMAND);
    }
    public void processDeviceSetting(String key, String value){
        if(key.equals("BAUD")){
            int newBaud = Integer.parseInt(value);
            setBaud(newBaud);
            if(CONNECTED) SELECTED_COM.setBaudRate(newBaud);
        }
    }

    public void obtainComPorts(){
        comm_ports = SerialPort.getCommPorts();
        comm_port_names = new String[comm_ports.length];
        comms_comPort.removeAll();
        for(int i = 0; i < comm_ports.length; i++){
            comm_port_names[i] = comm_ports[i].getSystemPortName();
            JMenuItem comPort = new JMenuItem(comm_port_names[i]);
            final int fi = i;
            comPort.addActionListener((e) -> selectComPort(fi));
            comms_comPort.add(comPort);
        }
    }
    public void selectComPort(int index){
        selectComPort(comm_ports[index]);
    }
    public void selectComPort(SerialPort sp){
        if(CONNECTED){
            disconnectFromDevice();
        }

        if(sp == null) return;
        SELECTED_COM = sp;
        SELECTED_COM.addDataListener(this);
        SELECTED_COM.setBaudRate(SELECTED_BAUD);
        SELECTED_COM_NAME = SELECTED_COM.getSystemPortName();
        comms_comPort.setText("COM Port: " + SELECTED_COM_NAME);
        log("DEVICE COM PORT SET TO: " + SELECTED_COM_NAME, LOG_REQUEST_COMMAND);
    }
    public SerialPort getComPort(String name){
        try {
            return SerialPort.getCommPort(name);
        } catch (Exception e){
            return null;
        }
    }

    //Serial Comm Methods
    protected int dataLengthStart = -1, dataLengthEnd = -1, dataLen, dataStart = -1, bufferIndex = 0;
    protected boolean bangSet = false, questSet = false, reading = false;
    protected char[] readBuffer = new char[4096];

    private static byte[] pageData = new byte[256];
    public void sendPageWrite(int addrStart){
        String dat = "?{EEWP,263}";
        if(addrStart < 0x1000) dat += '0';
        if(addrStart < 0x100) dat += '0';
        if(addrStart < 0x10) dat += '0';
        dat += Integer.toHexString(addrStart);
        dat += ',';

        byte[] header = dat.getBytes(StandardCharsets.US_ASCII);
        byte[] data = ROM_VIEW.getDataPageAt(addrStart, 256);
        int crcI = DataCRC.calculateCRC(data, 0, 256);
        byte[] crc = DataCRC.toBytes(crcI);

        System.arraycopy(data, 0, pageData, 0, data.length);

        byte[] total = new byte[header.length + pageData.length + crc.length];
        System.arraycopy(header, 0, total, 0, header.length);
        System.arraycopy(pageData, 0, total, header.length, pageData.length);
        System.arraycopy(crc, 0, total, header.length + pageData.length, crc.length);

        sendCommandRaw(total);

        System.out.println("Send CRC: ");
        System.out.println(Integer.toHexString(crcI));
    }

    public void sendCommandRaw(byte[] bytes){
        if(CONNECTED){
            SELECTED_COM.writeBytes(bytes, bytes.length);
            SELECTED_COM.flushIOBuffers();
        }
    }
    public void sendCommand(String method, String... params){
        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < params.length; i++){
            builder.append(params[i]).append(",");
        }
        String p = params.length == 0 ? "" : builder.substring(0, builder.length() - 1);

        builder = new StringBuilder();
        builder.append("!{").append(method).append(",").append(p.length()).append("}").append(p);
        String command = builder.toString();

        if(!method.equals("ROM")) log("SENDING COMMAND: " + command, LOG_REQUEST_COMMAND);
        sendCommandRaw(command.getBytes(StandardCharsets.US_ASCII));
    }
    public void sendRequest(String method, String... params){
        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < params.length; i++){
            builder.append(params[i]).append(",");
        }
        String p = params.length == 0 ? "" : builder.substring(0, builder.length() - 1);

        builder = new StringBuilder();
        builder.append("?{").append(method).append(",").append(p.length()).append("}").append(p);
        String command = builder.toString();

        log("SENDING REQUEST: " + command, LOG_REQUEST_COMMAND);
        if(CONNECTED || method.equals("AWAKE")){
            byte[] data = command.getBytes(StandardCharsets.US_ASCII);
            SELECTED_COM.writeBytes(data, data.length);
            SELECTED_COM.flushIOBuffers();
        }
    }
    public void processMessage(String message){
        int firstComma, secondComma, thirdComma;
        firstComma = message.indexOf(",");
        String command = message.substring(message.indexOf("{") + 1, firstComma).trim();
        int len = Integer.parseInt(message.substring(firstComma + 1, message.indexOf("}"))) + message.indexOf("}") + 1;
        if(command.equals("RESET")){
            if(ROR_COUNTER == 1){
                ROR_COUNTER = -1;
                romPane.setSelectedIndex(0);
                CLOCK_RUNNING = true;
                dataErrorCount = 0;
                addrErrorCount = 0;
            }
            log("DEVICE RESET", LOG_DEVICE_RESPONSE);
            return;
        }
        if(command.equals("AWAKE")){
            if(!DEVICE_RESPONSIVE){
                DEVICE_RESPONSIVE = true;
                CONNECTED = true;
                comms_connected.setText("Connected: TRUE");
                ((JImageButton)button_connectDev).setImage(loadImageAsset("connected.png"));
            }
            log("DEVICE RETURNED AWAKE", LOG_DEVICE_RESPONSE);
            return;
        }
        if(command.equals("MESSAGE")){
            String mess = message.substring(message.indexOf("}") + 1);
            if(LOG_MESSAGES) log("DEVICE MESSAGE: " + mess, LOG_DEVICE_RESPONSE);
            return;
        }
        if(command.equals("SETTING")){
            secondComma = message.indexOf(",", firstComma + 1);
            String key = message.substring(message.indexOf("}") + 1, secondComma);
            String value;
            if(len == secondComma){
                value = "";
            } else {
                value = message.substring(secondComma + 1, len);
            }

            processDeviceSetting(key, value);

            log("DEVICE SETTING: " + key + " -> " + value, LOG_DEVICE_RESPONSE);
            return;
        }
        if(command.equals("CLOCK")){
            if(ROR_COUNTER == 0){
                ROR_COUNTER = 1;
                sendRequest("RESET");
            }
            CLOCK_RUNNING = message.charAt(message.indexOf("}") + 1) == '1';
            ((JImageButton)button_playPause).setImage(loadImageAsset(CLOCK_RUNNING ? "pause.png" : "play.png"));
            log("DEVICE CLOCK " + (CLOCK_RUNNING ? "STARTED" : "STOPPED"), LOG_DEVICE_RESPONSE);
            return;
        }
        if(command.equals("ROM")){
            secondComma = message.indexOf(",", firstComma + 1);
            if(secondComma == -1){
                if(LOG_DECODE_ERROR) log("ERROR DECODING: " + message, LOG_DEVICE_ERROR);
                return;
            }
            String address = message.substring(message.indexOf("}") + 1, secondComma);
            String data = message.substring(secondComma + 1);

            int a = Integer.parseInt(address, 16) & 0xFFFF;
            int d = Integer.parseInt(data, 16) & 0xFF;

            String req = "";

            if(a < 0x1000) req += "0";
            if(a < 0x100) req += "0";
            if(a < 0x10) req += "0";
            req += Integer.toHexString(a);
            req += " -> ";
            if(d < 0x10) req += "0";
            req += Integer.toHexString(d);
            log(req, Color.GRAY);
            return;
        }
        if(command.equals("RAM")){
            secondComma = message.indexOf(",", firstComma + 1);
            String address = message.substring(message.indexOf("}") + 1, secondComma);
            String data = message.substring(secondComma + 1);

            int a = Integer.parseInt(address, 16) & 0xFFFF;
            int d = Integer.parseInt(data, 16) & 0xFF;

            RAM_VIEW.setWriterToAddress(a);
            RAM_VIEW.setDataAt(a, d);
            return;
        }
        if(command.equals("RAMCL")){
            RAM_VIEW.clearData();
            log("DEVICE RAM CLEARED", LOG_DEVICE_RESPONSE);
            return;
        }
        if(command.equals("RAMR")){
            String address = message.substring(message.indexOf("}") + 1);
            int a = Integer.parseInt(address, 16) & 0xFFFF;

            RAM_VIEW.setReaderToAddress(a);
            return;
        }
        if(command.equals("EEWP")){
            secondComma = message.indexOf(",", firstComma + 1);
            String address = message.substring(message.indexOf("}") + 1, secondComma);
            System.out.println("Address: " + address);

            return;
        }
        if(command.equals("6502")){
            secondComma = message.indexOf(",", firstComma + 1);
            thirdComma = message.indexOf(",", secondComma + 1);

            if(secondComma == -1 || thirdComma == -1){
                if(LOG_DECODE_ERROR) log("ERROR DECODING: " + message, LOG_DEVICE_ERROR);
                return;
            }

            String address = message.substring(message.indexOf("}") + 1, secondComma);
            String data = message.substring(secondComma + 1, thirdComma);
            String rw_s = message.substring(thirdComma + 1, thirdComma + 2);
            String rst_s = message.substring(thirdComma + 2);

            int a = Integer.parseInt(address, 16) & 0xFFFF;
            int d = Integer.parseInt(data, 16) & 0xFF;
            boolean rw = rw_s.equals("1");
            boolean rst = rst_s.equals("1");

            for(int i = 0; i < 16; i++){
                int addressBit = (a >> i) & 1;
                pinVisualizer.setPinState("A" + i, addressBit == 1);
            }

            for(int i = 0; i < 8; i++){
                int dataBit = (d >> i) & 1;
                pinVisualizer.setPinState("D" + i, dataBit == 1);
            }
            pinVisualizer.setPinState("RW", rw);
            pinVisualizer.setPinState("Rst", rst);

            return;
        }
        if(command.equals("ERROR")){
            secondComma = message.indexOf(",", firstComma + 1);
            if(secondComma == -1) {
                //System.out.println("Error decoding error: " + message);
                return;
            }

            int errorMessage = Integer.parseInt(message.substring(message.indexOf("}") + 1, secondComma));
            String msg = message.length() > secondComma + 1 ? message.substring(secondComma + 1) : "";
            switch (errorMessage){
                case -1: log("DEVICE ERROR: Unknown Command", LOG_DEVICE_ERROR); break;
                case -2: log("DEVICE ERROR: Read Buffer Overflow", LOG_DEVICE_ERROR); break;
                case -3: {
                    dataErrorCount++;
                    statusBar.putValue("data_errors", dataErrorCount);
                    if(LOG_DATA_ERROR) log("DEVICE ERROR: Invalid Data Received " + msg, LOG_DEVICE_ERROR);
                    break;
                }
                case -4: {
                    addrErrorCount++;
                    statusBar.putValue("addr_errors", dataErrorCount);
                    if(LOG_ADDR_ERROR) log("DEVICE ERROR: Invalid Address Received " + msg, LOG_DEVICE_ERROR);
                    break;
                }
                default: log("DEVICE ERROR: Unknown Error", LOG_DEVICE_ERROR); break;
            }
            return;
        }

        log("UNKNOWN COMMAND: " + message, LOG_DEVICE_ERROR);
        return;
    }
    public void processRequest(String message){
        String command = message.substring(message.indexOf("{") + 1, message.indexOf(",")).trim();
        if(command.equals("ROM")) {
            String address = message.substring(message.indexOf("}") + 1);
            int a;
            try {
                a = Integer.parseInt(address, 16);
            } catch (Exception e){
                return;
            }
            address = pad4HexNum(a);
            int data = ROM_VIEW.getDataAt(a);
            int comp = 255 - data;
            int xor = data ^ 'k';
            String dcx = pad2HexNum(data) + pad2HexNum(comp) + pad2HexNum(xor);
            ROM_VIEW.setReaderToAddress(a);

            sendCommand("ROM", address, dcx);
            return;
        }
    }

    public void serialEvent(SerialPortEvent event) {
        try {
            int count = SELECTED_COM.bytesAvailable();
            byte[] dat = new byte[count];
            SELECTED_COM.readBytes(dat, count);
            char c;

            for(int i = 0; i < count; i++) {
                c = (char)dat[i];
                System.out.print(c);
                if(c == '\n') {
                    reading = false;
                }
                if(c == '!') {
                    bangSet = true;
                    questSet = false;
                    reading = false;
                }
                else if(c == '?'){
                    bangSet = false;
                    questSet = true;
                    reading = false;
                }
                else if(c == '{' && bangSet) { //Message data start
                    reading = true;
                    readBuffer[0] = '!';
                    bangSet = false;
                    bufferIndex = 1;
                    dataLengthStart = -1;
                    dataLengthEnd = -1;
                }
                else if(c == '{' && questSet) { //Request data start
                    reading = true;
                    readBuffer[0] = '?';
                    questSet = false;
                    bufferIndex = 1;
                    dataLengthStart = -1;
                    dataLengthEnd = -1;
                }
                else if(reading && c == ','){
                    dataLengthStart = bufferIndex + 1;
                }
                else if(dataLengthStart > -1 && reading && c == '}'){
                    dataLengthEnd = bufferIndex;
                    String length = new String(readBuffer, dataLengthStart, dataLengthEnd - dataLengthStart);
                    try {
                        dataLen = Integer.parseInt(length);
                    } catch (Exception e){
                        reading = false;
                    }
                    //System.out.println("Command Len: " + dataLen);
                    dataStart = bufferIndex;
                }

                if(reading) {
                    bangSet = false;
                    questSet = false;
                    readBuffer[bufferIndex] = c;
                    bufferIndex++;
                }

                if(reading && (dataStart + dataLen + 1) - bufferIndex == 0){
                    String command = new String(readBuffer, 0, bufferIndex);
                    if(command.charAt(0) == '!'){
                        processMessage(command);
                    } else if(command.charAt(0) == '?'){
                        processRequest(command);
                    }

                    dataLen = 0;
                    dataStart = -1;
                    bufferIndex = 0;
                    reading = false;
                }
            }
        } catch (Exception e){
            e.printStackTrace();
        }}
    public int getListeningEvents() {
        return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
    }

    public static final Color LOG_DEVICE_RESPONSE = new Color(0.2F, 0.8F, 0.2F, 1F);
    public static final Color LOG_DEVICE_ERROR = new Color(1.0F, 0.2F, 0.2F, 1F);
    public static final Color LOG_REQUEST_COMMAND = new Color(0.2F, 0.2F, 1.0F, 1F);
    public void log(String message, Color color){
        outputPane.setSelectedIndex(0);

        StyleConstants.setForeground(textStyle, color);
        StyledDocument doc = serialOutput.getStyledDocument();
        try {
            doc.insertString(doc.getLength(), message + "\n", textStyle);
        } catch (Exception e){
            e.printStackTrace();
        }
        serialOutput.setCaretPosition(doc.getLength());
    }

    public static void main(String[] args) {
        System.out.println(System.getProperty("java.version"));
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e){
            e.printStackTrace();
        }
        new Main();
    }
}