package org.jdyna.frontend.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.jdyna.Board;
import org.jdyna.BoardInfo;
import org.jdyna.Boards;
import org.jdyna.Constants;
import org.jdyna.Game;
import org.jdyna.GameConfiguration;
import org.jdyna.GameResult;
import org.jdyna.IGameEventListener;
import org.jdyna.IGameEventListenerHolder;
import org.jdyna.IPlayerController;
import org.jdyna.IPlayerFactory;
import org.jdyna.IPlayerSprite;
import org.jdyna.IViewListener;
import org.jdyna.Player;
import org.jdyna.PlayerTeamName;
import org.jdyna.audio.jxsound.JavaSoundSFX;
import org.jdyna.audio.openal.OpenALSFX;
import org.jdyna.frontend.swing.Configuration.ViewType;
import org.jdyna.network.packetio.UDPPacketEmitter;
import org.jdyna.network.sockets.AsyncPlayerController;
import org.jdyna.network.sockets.ControllerStateDispatch;
import org.jdyna.network.sockets.GameClient;
import org.jdyna.network.sockets.GameHandle;
import org.jdyna.network.sockets.GameServer;
import org.jdyna.network.sockets.GameServerClient;
import org.jdyna.network.sockets.PlayerHandle;
import org.jdyna.network.sockets.packets.ServerInfo;
import org.jdyna.players.CustomControllerPlayerFactory;
import org.jdyna.players.n00b.NoobFactory;
import org.jdyna.players.rabbit.RabbitFactory;
import org.jdyna.players.stalker.StalkerFactory;
import org.jdyna.players.tyson.TysonFactory;
import org.jdyna.view.resources.ImageUtilities;
import org.jdyna.view.swing.AWTKeyboardController;
import org.jdyna.view.swing.BoardFrame;
import org.jdyna.view.swing.SwingUtils;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jgoodies.forms.builder.ButtonBarBuilder;
import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.factories.DefaultComponentFactory;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.looks.Options;

/**
 * Simple Swing GUI to JDyna.
 */
public final class JDyna
{
    /**
     * Default GUI elements spacing.
     */
    private final static int SPACING = 4;

    /**
     * Main launcher frame.
     */
    private JFrame frame;

    /**
     * All available boards.
     */
    private Boards boards;

    /**
     * Configuration settings.
     */
    private Configuration config = new Configuration();

    /**
     * Board used for the game.
     */
    private Board board;

    /**
     * Game configuration used for the game.
     */
    private GameConfiguration gameConfig;

    /**
     * Bots.
     */
    private static final HashMap<String, IPlayerFactory> bots;
    static
    {
        bots = Maps.newHashMap();

        bots.put("rabbit (cannon fodder)", new RabbitFactory());  
        bots.put("stalker (fair)", new StalkerFactory());
        bots.put("n00b (good)", new NoobFactory());
        bots.put("tyson (very good)", new TysonFactory());
    }

    /*
     * 
     */
    private void start()
    {
        /*
         * Load board configurations.
         */
        try
        {
            final ClassLoader cl = Thread.currentThread().getContextClassLoader();
            this.boards = Boards.read(
                new InputStreamReader(cl.getResourceAsStream("boards.conf"), "UTF-8"));
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        /*
         * Load previously saved configuration.
         */
        final File configSettings = new File(SystemUtils.getUserHome(), ".jdyna.xml");
        try
        {
            config = Configuration.load(
                new FileInputStream(configSettings));
        }
        catch (IOException e)
        {
            // Ignore and stay with defaults.
        }

        /*
         * Initialize the main GUI.
         */
        frame = new JFrame("JDyna.com");
        frame.getContentPane().add(createMainPanelGUI());
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setResizable(false);
        try
        {
            frame.setIconImage(ImageUtilities.loadResourceImage("icons/window-icon.png"));
        }
        catch (IOException e)
        {
            // Ignore if icon not found.
        }

        /*
         * Save settings on shutdown.
         */
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosed(WindowEvent e)
            {
                try
                {
                    FileUtils.writeStringToFile(configSettings, 
                        config.save(), "UTF-8");
                }
                catch (IOException x)
                {
                    // Ignore if could not save.
                }
            }
        });

        /*
         * From the dispatcher thread, center the frame and show it.
         */
        SwingUtilities.invokeLater(new Runnable() {
            public void run()
            {
                frame.pack();
                SwingUtils.centerFrameOnScreen(frame);
                frame.setVisible(true);
            }
        });
    }

    /*
     * 
     */
    private JComponent createMainPanelGUI()
    {
        final JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(SPACING, SPACING, SPACING, SPACING));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        panel.add(createSectionTitleGUI("Local play modes"));
        panel.add(Box.createVerticalStrut(2));
        panel.add(createLocalModeGUI());
        panel.add(Box.createVerticalStrut(5));

        panel.add(createSectionTitleGUI("Network (multiplayer) modes"));
        panel.add(Box.createVerticalStrut(2));
        panel.add(createNetworkModeGUI());

        panel.add(Box.createVerticalStrut(10));
        panel.add(Box.createGlue());
        panel.add(new JSeparator());
        panel.add(Box.createVerticalStrut(4));
        panel.add(createBottomButtonsGUI());
        return panel;
    }

    /*
     * 
     */
    private JComponent createSectionTitleGUI(String text)
    {
        DefaultComponentFactory f = new DefaultComponentFactory();
        return f.createSeparator(text);
    }
    
    /*
     * 
     */
    private JComponent createBottomButtonsGUI()
    {
        final ButtonBarBuilder builder = new ButtonBarBuilder();
        
        final JButton aboutButton = new JButton("About");
        aboutButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                displayAbout();
            }
        });

        final JButton configureButton = new JButton("Configure");
        configureButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                displayConfiguration();
            }
        });

        final JButton quitButton = new JButton("Quit");
        quitButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                frame.dispose();
            }
        });

        builder.addFixed(aboutButton);
        builder.addRelatedGap();
        builder.addFixed(configureButton);
        builder.addUnrelatedGap();
        builder.addGlue();
        builder.addGridded(quitButton);
        return builder.getPanel();
    }

    /*
     * 
     */
    private void displayConfiguration()
    {
        assert SwingUtilities.isEventDispatchThread();

        final ConfigurationDialog configDialog = new ConfigurationDialog(config);
        config = configDialog.prompt(frame);
    }

    /*
     * 
     */
    protected void displayAbout()
    {
        try
        {
            final JPanel panel = new JPanel(new BorderLayout(5, 5));
            panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 20));
    
            final JTextArea textArea = new JTextArea(20, 81);
            textArea.setFont(
                new Font("Monospaced", Font.PLAIN, textArea.getFont().getSize()));
            textArea.setWrapStyleWord(false);
            textArea.setLineWrap(false);
            textArea.setText(IOUtils.toString(getClass().getResourceAsStream("/about.txt"), "UTF-8"));
            textArea.setEditable(false);
            textArea.setCaretPosition(0);

            final JScrollPane scroller = new JScrollPane(textArea);
            scroller.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

            panel.add(scroller, BorderLayout.CENTER);
            scroller.setBorder(BorderFactory.createEmptyBorder());
    
            JOptionPane.showMessageDialog(
                frame, panel, "About JDyna", JOptionPane.INFORMATION_MESSAGE);
        }
        catch (IOException e)
        {
            // Ignore.
        }
    }

    /*
     * 
     */
    private Component createLocalModeGUI()
    {
        final JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

        final JButton twoPlayersGameButton = new JButton(getIcon("buttons/human-human.png"));
        twoPlayersGameButton.setToolTipText("Two human players.");
        twoPlayersGameButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                runTwoPlayersGame();
            }
        });
        panel.add(twoPlayersGameButton);

        panel.add(Box.createHorizontalStrut(SPACING));

        final JButton onePlayerGameButton = new JButton(getIcon("buttons/human-cpu.png"));
        onePlayerGameButton.setToolTipText("Play against computer.");
        onePlayerGameButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                runOnePlayerGame();
            }
        });        
        panel.add(onePlayerGameButton);

        return panel;
    }

    /*
     * 
     */
    private Component createNetworkModeGUI()
    {
        final JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

        final JButton startGame = new JButton(getIcon("buttons/server-start.png"));
        startGame.setToolTipText("Start a network game.");
        startGame.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                runNetworkedGame();
            }
        });
        panel.add(startGame);

        panel.add(Box.createHorizontalStrut(SPACING));

        final JButton joinGame = new JButton(getIcon("buttons/server-join.png"));
        joinGame.setToolTipText("Join a network game.");
        joinGame.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                joinNetworkGame();
            }
        });
        panel.add(joinGame);

        return panel;
    }    
    /**
     * Run a local game between two players. 
     */
    private void runTwoPlayersGame()
    {
        assert SwingUtilities.isEventDispatchThread();

        if (!selectGameConfig(
            GameConfiguration.CLASSIC,
            GameConfiguration.WITH_BONUSES,
            GameConfiguration.MORE_BONUSES))
            return;

        hideMainGUI();
        runLocalGame(
            board,
            gameConfig,
            null,
            getKeyboardController(0, config.viewType, "Player 1"),
            getKeyboardController(1, config.viewType, "Player 2"));
    }

    /**
     * Return the keyboard controller adequate to the attached view. 
     */
    private IPlayerFactory getKeyboardController(int playerNum, 
        ViewType viewType, String playerName)
    {
        switch (viewType)
        {
            case SWING_VIEW:
                return new CustomControllerPlayerFactory(
                    AWTKeyboardController.getKeyboardController(playerNum, config), playerName);
            default:
                throw new RuntimeException("Unreachable code.");
        }
    }

    /**
     * Run one player game (vs. bot(s)). 
     */
    private void runOnePlayerGame()
    {
        assert SwingUtilities.isEventDispatchThread();

        // TODO: restrict to CLASSIC only because bots were not designed to run with
        // bonuses on board.
        if (!selectGameConfig(GameConfiguration.CLASSIC))
            return;

        final String bot = Dialogs.selectOneFromList(frame,
            "Select opponent", "Select opponent",
            null, getBotNames());
        if (bot == null) return;

        hideMainGUI();

        final String playerName = "Player";
        runLocalGame(
            board,
            GameConfiguration.CLASSIC,
            playerName,
            getKeyboardController(0, config.viewType, playerName),
            getBot(bot));        
    }

    /**
     * Pick the board and game configuration if more than one is allowed.
     */
    private boolean selectGameConfig(GameConfiguration... configs)
    {
        final DefaultFormBuilder builder =
            new DefaultFormBuilder(new FormLayout("right:pref, 3dlu, fill:pref:grow"));
        builder.setDefaultDialogBorder();
        
        // Add board selection.
        final Set<String> boardNames = boards.getBoardNames();
        final JComboBox boardCombo = new JComboBox(
            (String []) boardNames.toArray(new String [boardNames.size()]));
        setComboDefault(boardCombo, config.mostRecentBoard);

        // Add game config selection.
        final String [] configNames = new String [configs.length];
        for (int i = 0; i < configs.length; i++)
            configNames[i] = configs[i].configName;

        final JComboBox configCombo = new JComboBox(configNames);
        setComboDefault(configCombo, config.mostRecentConfig);

        builder.append("Board:", boardCombo);
        builder.append("Bonuses:", configCombo);

        final JComponent panel = builder.getPanel();
        if (JOptionPane.OK_OPTION == JOptionPane.showOptionDialog(frame, panel,
            "Game configuration", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, 
            null, null, null))
        {
            this.board = boards.get((String) boardCombo.getSelectedItem());
            this.gameConfig = configs[configCombo.getSelectedIndex()];

            this.config.mostRecentBoard = board.name;
            this.config.mostRecentConfig = gameConfig.configName;
            
            return true;
        }
        return false;
    }

    /**
     * Set the default for a given combo or the first available option.
     */
    private void setComboDefault(JComboBox combo, String selected)
    {
        combo.setSelectedItem(selected);
        if (combo.getSelectedIndex() < 0)
            combo.setSelectedIndex(0);
    }

    /**
     * Return the names of all bots.
     */
    private String [] getBotNames()
    {
        return (String []) bots.keySet().toArray(new String [bots.size()]);
    }

    /**
     * Join an existing game on the local network. 
     */
    private void joinNetworkGame()
    {
        assert SwingUtilities.isEventDispatchThread();

        final String name = promptForName();
        if (name == null) return;

        final PlayerTeamName fullName = new PlayerTeamName(name);

        /*
         * Display a temporary progress window while looking for network servers
         * and games.
         */
        final JOptionPane information =
            new JOptionPane("Looking for games on the local network...", 
            JOptionPane.INFORMATION_MESSAGE);
        information.setOptions(new Object [] {"Cancel"});

        final JDialog dialog = information.createDialog(frame, "Network scanning...");

        SwingWorker<?, ?> sw = new SwingWorker<List<GameListEntry>, String>()
        {
            @Override
            protected List<GameListEntry> doInBackground() throws Exception
            {
                List<ServerInfo> servers = GameServerClient.lookup(
                    config.UDPBroadcastPort, 
                    /* Look for all servers. */ 0,
                    2 * GameServer.AUTO_DISCOVERY_INTERVAL);

                if (servers.size() == 0)
                {
                    return Collections.emptyList();
                }

                final List<GameListEntry> games = Lists.newArrayList();
                for (ServerInfo si : servers)
                {
                    final GameServerClient gsc = new GameServerClient(si);
                    gsc.connect();
                    for (GameHandle gh : gsc.listGames())
                    {
                        games.add(new GameListEntry(si, gh));
                    }
                    gsc.disconnect();
                }

                return games;
            }
            
            @Override
            protected void done()
            {
                try
                {
                    dialog.dispose();
                    if (isCancelled()) return;
                    joinNetworkGame(fullName, get());
                }
                catch (ExecutionException e)   
                {
                    // Ignore.
                }
                catch (InterruptedException e)
                {
                    // Ignore.
                }
            }
        };
        sw.execute();

        // Display a modal dialog box for the duration of server lookup.
        dialog.setVisible(true);
        if (information.getValue() != JOptionPane.UNINITIALIZED_VALUE)
        {
            sw.cancel(true);
        }
    }

    protected void joinNetworkGame(PlayerTeamName fullName, List<GameListEntry> games)
    {
        /*
         * Look for existing game servers on the local network.
         */
        try
        {
            GameListEntry gameEntry;
            if (games.size() == 0) throw new IOException("No active servers or games.");
            else if (games.size() == 1) gameEntry = games.get(0);
            else
            {
                final Object [] options = games.toArray(new GameListEntry [games.size()]);
                gameEntry = (GameListEntry) Dialogs.selectOneFromList(frame, 
                    "Game selection.", "Select the game to join.", null, options);
                if (gameEntry == null) return;
            }

            /*
             * Join the given game.
             */
            final IPlayerFactory playerFactory = 
                getKeyboardController(0, config.viewType, fullName.playerName);

            final GameServerClient client = new GameServerClient(gameEntry.server);
            client.connect();
            final PlayerHandle playerHandle = client.joinGame(
                gameEntry.handle, fullName.toString());
            client.disconnect();

            /*
             * Prepare asynchronous player using a loopback connection.
             */
            final IPlayerController localController = playerFactory.getController(
                fullName.playerName);

            final UDPPacketEmitter serverUpdater = new UDPPacketEmitter(new DatagramSocket());
            serverUpdater.setDefaultTarget(
                InetAddress.getByName(gameEntry.server.serverAddress), 
                gameEntry.server.UDPFeedbackPort);

            final GameClient gameClient = new GameClient(gameEntry.handle, gameEntry.server);
            final Thread gameClientThread = new Thread() {
                public void run()
                {
                    try
                    {
                        gameClient.runLoop();
                    }
                    catch (IOException e)
                    {
                        // Ignore, not much to do.
                    }

                    cleanupListeners(gameClient);
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() { showMainGUI(); }
                    });
                }
            };

            final IViewListener viewListener = new IViewListener() {
                public void viewClosed()
                {
                    /* Disconnect from the game. */
                    gameClientThread.interrupt();
                }
            };

            final IGameEventListener audio = createAudioEngine();
            if (audio != null) gameClient.addListener(audio);

            gameClient.addListener(createView(fullName.playerName, viewListener));

            final AsyncPlayerController asyncController = 
                new AsyncPlayerController(localController);
            gameClient.addListener(asyncController); 
            gameClient.addListener(
                new ControllerStateDispatch(playerHandle, asyncController, serverUpdater));

            hideMainGUI();
            gameClientThread.start();            
        }
        catch (IOException e)
        {
            SwingUtils.showExceptionDialog(frame, "Server lookup problems.", e);
        }
    }

    /**
     * Start a server for a network game. 
     */
    private void runNetworkedGame()
    {
        assert SwingUtilities.isEventDispatchThread();

        if (!selectGameConfig(
            GameConfiguration.CLASSIC,
            GameConfiguration.WITH_BONUSES,
            GameConfiguration.MORE_BONUSES))
            return;

        final String name = promptForName();
        if (name == null) return;

        final PlayerTeamName fullName = new PlayerTeamName(name);
        hideMainGUI();

        /*
         * Start local server, create the game and attach the player to the server.
         */
        final GameServer server = new GameServer(config.UDPBroadcastPort, config.UDPport, config.TCPport);
        server.gameStateLogging = false;

        try
        {
            final IPlayerFactory playerFactory = 
                getKeyboardController(0, config.viewType, fullName.playerName);

            /*
             * Start the server. 
             */
            final ServerInfo serverInfo = server.start();
            final GameServerClient client = new GameServerClient(serverInfo);
            client.connect();

            /*
             * Create a new game on the server. 
             */
            final GameHandle gameHandle = client.createGame(gameConfig, "Game: " + board.name, board.name);
            final PlayerHandle playerHandle = client.joinGame(gameHandle, fullName.toString());
            client.disconnect();

            /*
             * Prepare asynchronous player using a loopback connection.
             */
            final IPlayerController localController = playerFactory.getController(fullName.playerName);

            final UDPPacketEmitter serverUpdater = new UDPPacketEmitter(new DatagramSocket());
            serverUpdater.setDefaultTarget(
                Inet4Address.getLocalHost(), serverInfo.UDPFeedbackPort);

            final GameClient gameClient = new GameClient(gameHandle, serverInfo);

            final IViewListener viewListener = new IViewListener() {
                public void viewClosed()
                {
                    /* Close the game by shutting down the server */
                    server.stop();
                    cleanupListeners(gameClient);
                    showMainGUI();
                }
            };
            
            final IGameEventListener audio = createAudioEngine();
            if (audio != null) gameClient.addListener(audio);
            
            gameClient.addListener(createView(fullName.playerName, viewListener));

            final AsyncPlayerController asyncController = 
                new AsyncPlayerController(localController);
            gameClient.addListener(asyncController); 
            gameClient.addListener(
                new ControllerStateDispatch(playerHandle, asyncController, serverUpdater));

            new Thread() {
                public void run()
                {
                    try
                    {
                        gameClient.runLoop();
                    }
                    catch (IOException e)
                    {
                        // Ignore, not much to do.
                    }
                }
            }.start();
        }
        catch (IOException e)
        {
            server.stop();
            showMainGUI();
            SwingUtils.showExceptionDialog(frame, "Could not start server.", e);
            return;
        }
    }
    
    /**
     * Prompt for player name.
     */
    private String promptForName()
    {
        assert SwingUtilities.isEventDispatchThread();

        do
        {
            final String result = JOptionPane.showInputDialog(frame, 
                "Enter player identifier (team:playerName or playerName):", 
                "Player name", JOptionPane.QUESTION_MESSAGE);

            if (StringUtils.isEmpty(result))
            {
                return null;
            }

            if (!PlayerTeamName.isValid(result))
            {
                JOptionPane.showMessageDialog(frame, "Player name not valid: " + result);
                continue;
            }

            return result;            
        } while (true);
    }

    /**
     * Run a local game.
     */
    private void runLocalGame(Board board, GameConfiguration conf, String highlightPlayer, IPlayerFactory... players)
    {
        final BoardInfo boardInfo = new BoardInfo(
            new Dimension(board.width, board.height), Constants.DEFAULT_CELL_SIZE);

        final Game game = new Game(conf, board, boardInfo);
        ArrayList<IPlayerSprite> playerSprites = new ArrayList<IPlayerSprite>(1);
    
        for (IPlayerFactory pf : players)
        {
            final String name = pf.getDefaultPlayerName();
            final IPlayerSprite player = game.addPlayer(new Player(name, pf.getController(name)));
            if (StringUtils.isEmpty(highlightPlayer)
                || StringUtils.equals(name, highlightPlayer))
            {
                playerSprites.add(player);
            }
        }

        /*
         * Attach sound effects to the game.
         */
        final IGameEventListener audio = createAudioEngine();
        if (audio != null)
        {
            game.addListener(audio);
        }

        /*
         * Attach a swing display view to the game.
         */
        final Thread gameThread = new Thread() {
            @SuppressWarnings("unused")
            public void run()
            {
                final GameResult result = game.run(Game.Mode.INFINITE_DEATHMATCH);
            }
        };

        final IViewListener viewListener = new IViewListener() {
            public void viewClosed()
            {
                game.interrupt();
                try
                {
                    if (Thread.currentThread() != gameThread)
                        gameThread.join();
                }
                catch (InterruptedException e1)
                {
                    throw new RuntimeException();
                }

                cleanupListeners(game);
                showMainGUI();
            }
        };
        if (!StringUtils.isEmpty(highlightPlayer))
        {
            game.addListener(createView(highlightPlayer, viewListener));
        }
        else
        {
            IPlayerSprite [] playerSpritesArray = new IPlayerSprite [playerSprites.size()];
            for (int i = 0; i < playerSprites.size(); i++)
            {
                playerSpritesArray[i] = playerSprites.get(i);
            }
            game.addListener(createView(highlightPlayer, viewListener, playerSpritesArray));
        }
        gameThread.start();
    }

    /**
     * Cleanup (dispose) any listeners that we know should be disposed after a given
     * game is finished. 
     */
    private void cleanupListeners(IGameEventListenerHolder holder)
    {
        for (IGameEventListener listener : holder.getListeners())
        {
            if (listener instanceof JavaSoundSFX)
            {
                ((JavaSoundSFX) listener).dispose();
            }
            else if (listener instanceof BoardFrame)
            {
                SwingUtils.dispose((BoardFrame) listener);
            }
        }
    }

    /**
     * Create audio engine according to the current settings. 
     */
    private IGameEventListener createAudioEngine()
    {
        try
        {
            switch (config.soundEngine)
            {
                case NONE:  return null;
                case JAVA_AUDIO: return new JavaSoundSFX();
                case OPEN_AL: return new OpenALSFX();
                default:
                    throw new RuntimeException("Unexpected audio engine: " + config.soundEngine);
            }
        }
        catch (Throwable t)
        {
            SwingUtils.showExceptionDialog(frame, "Audio engine initialization failed.", t);
            return null;
        }
    }

    private IGameEventListener createView(String trackedPlayer, final IViewListener listener, IPlayerSprite... players) {
        switch (config.viewType) {
            case SWING_VIEW:
                return createSwingView(trackedPlayer, listener, players);
        }
        throw new RuntimeException("Unknown view: " + config.viewType);
    }
    
    /**
     * Create a game view for the given player.
     */
    private IGameEventListener createSwingView(String trackedPlayer,
        final IViewListener listener, IPlayerSprite... players)
    {
        final BoardFrame boardFrame = new BoardFrame();
        boardFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        if (!StringUtils.isEmpty(trackedPlayer))
        {
            boardFrame.trackPlayer(trackedPlayer);
            boardFrame.showStatusFor(trackedPlayer);
        }
        else
        {
            boardFrame.showStatusFor(players);
        }

        boardFrame.addWindowListener(new WindowAdapter()
        {
            public void windowClosed(WindowEvent e)
            {
                boardFrame.cleanup();
                if (listener != null) listener.viewClosed();
            }
        });
        
        boardFrame.setVisible(true);
        return boardFrame;
    }

    /**
     * Get bot for a given name.
     */
    private IPlayerFactory getBot(String bot)
    {
        return bots.get(bot);
    }

    /*
     * 
     */
    private void hideMainGUI()
    {
        assert SwingUtilities.isEventDispatchThread();
        frame.setVisible(false);
    }

    /*
     * 
     */
    private void showMainGUI()
    {
        assert SwingUtilities.isEventDispatchThread();
        frame.setVisible(true);
    }

    /*
     * 
     */
    private Icon getIcon(String resource)
    {
        try
        {
            return new ImageIcon(ImageUtilities.loadResourceImage(resource));
        }
        catch (IOException e)
        {
            throw new RuntimeException("Could not load resource: " + resource);
        }
    }

    /**
     * Command line entry point.
     */
    public static void main(String [] args)
    {
        try
        {
            final String lafName = Options.getCrossPlatformLookAndFeelClassName();
            UIManager.setLookAndFeel(lafName);
        }
        catch (Exception e)
        {
            // Ignore if not found.
        }

        new JDyna().start();
    }
}
