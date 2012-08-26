package org.powerbot.gui.component;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import org.powerbot.Boot;
import org.powerbot.game.api.ActiveScript;
import org.powerbot.game.api.util.Time;
import org.powerbot.game.bot.Bot;
import org.powerbot.gui.BotChrome;
import org.powerbot.gui.BotScripts;
import org.powerbot.ipc.Controller;
import org.powerbot.service.NetworkAccount;
import org.powerbot.util.Configuration;
import org.powerbot.util.io.IniParser;
import org.powerbot.util.io.Resources;

/**
 * @author Paris
 */
public final class BotToolBar extends JToolBar implements ActionListener {
	private static final long serialVersionUID = 1L;
	public final BotChrome parent;
	public final JButton botButton, tabAdd, scriptPlay, scriptStop, panelInput;

	public BotToolBar(final BotChrome parent) {
		this.parent = parent;
		setFloatable(false);
		setBorder(new EmptyBorder(1, 3, 1, 3));
		setBackground(Color.BLACK);

		botButton = new BotButton("Game");
		botButton.setVisible(false);
		add(botButton);

		tabAdd = new JButton(new ImageIcon(Resources.getImage(Resources.Paths.ADD_WHITE)));
		tabAdd.setEnabled(false);
		tabAdd.setBackground(getBackground());
		tabAdd.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						tabAdd.setEnabled(false);
						SwingUtilities.invokeLater(new Runnable() {
							@Override
							public void run() {
								addTab();
							}
						});
					}
				});
			}
		});
		tabAdd.setToolTipText(BotLocale.NEWTAB);
		tabAdd.setFocusable(false);
		add(tabAdd);

		add(Box.createHorizontalGlue());

		scriptPlay = new JButton(new ImageIcon(Resources.getImage(Resources.Paths.PLAY)));
		scriptPlay.setBackground(getBackground());
		scriptPlay.addActionListener(this);
		scriptPlay.setToolTipText(BotLocale.PLAYSCRIPT);
		scriptPlay.setFocusable(false);
		scriptPlay.setVisible(false);
		scriptPlay.setEnabled(false);
		add(scriptPlay);
		scriptStop = new JButton(new ImageIcon(Resources.getImage(Resources.Paths.STOP)));
		scriptStop.setBackground(getBackground());
		scriptStop.addActionListener(this);
		scriptStop.setToolTipText(BotLocale.STOPSCRIPT);
		scriptStop.setFocusable(false);
		scriptStop.setVisible(false);
		add(scriptStop);
		panelInput = new JButton(new ImageIcon(Resources.getImage(Resources.Paths.KEYBOARD_WHITE)));
		panelInput.setBackground(getBackground());
		panelInput.addActionListener(this);
		panelInput.setToolTipText(BotLocale.INPUT);
		panelInput.setFocusable(false);
		add(panelInput);
		add(Box.createHorizontalStrut(16));

		final BotToolBar t = this;
		final JButton settings = new JButton(new ImageIcon(Resources.getImage(Resources.Paths.SETTINGS_WHITE)));
		settings.setBackground(getBackground());
		settings.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent arg0) {
				new BotMenu(t).show(settings, settings.getWidth() / 2, settings.getHeight() / 2);
			}
		});
		settings.setFocusable(false);
		add(settings);
	}

	public void actionPerformed(final ActionEvent e) {
		final Component c = (Component) e.getSource();
		if (c == scriptPlay) {
			final Bot bot = Bot.getInstance();
			final ActiveScript script = bot.getActiveScript();
			if (script != null && script.isRunning()) {
				if (!script.isSilentlyLocked()) {
					if (script.isPaused()) {
						script.resume();
						updateScriptControls();
					} else {
						script.pause();
						updateScriptControls();
					}
				}
				return;
			}

			if (bot.getClient() != null) {
				new BotScripts(this);
			}
		} else if (c == scriptStop) {
			if (!Bot.isInstantiated()) {
				return;
			}
			final Bot bot = Bot.getInstance();
			final ActiveScript activeScript = bot.getActiveScript();
			if (activeScript != null) {
				if (activeScript.isRunning()) {
					bot.stopScript();
					bot.getContainer().submit(new Runnable() {
						public void run() {
							while (!activeScript.getContainer().isTerminated()) {
								Time.sleep(150);
							}
							updateScriptControls();
						}
					});
				} else {
					if (!activeScript.getContainer().isTerminated()) {
						activeScript.log.info("Forcing script stop");
						activeScript.kill();
						updateScriptControls();
					}
				}
			}
		} else if (c == panelInput) {
			final JPopupMenu menu = new JPopupMenu();

			JCheckBoxMenuItem item;
			final int panelInputMask = BotChrome.getInstance().panel.getInputMask();

			final Map<String, Integer> map = new LinkedHashMap<String, Integer>();
			map.put(BotLocale.ALLOW, BotPanel.INPUT_MOUSE | BotPanel.INPUT_KEYBOARD);
			map.put(BotLocale.KEYBOARD, BotPanel.INPUT_KEYBOARD);
			map.put(BotLocale.BLOCK, 0);

			for (final Map.Entry<String, Integer> inputMask : map.entrySet()) {
				final int mask = inputMask.getValue();
				item = new JCheckBoxMenuItem(inputMask.getKey(), panelInputMask == mask);
				item.addActionListener(new ActionListener() {
					public void actionPerformed(final ActionEvent e1) {
						BotChrome.getInstance().panel.setInputMask(mask);
					}
				});

				menu.add(item);
			}

			menu.show(c, c.getWidth() / 2, c.getHeight() / 2);
		}
	}

	public synchronized void addTab() {
		final int s = Bot.isInstantiated() ? 1 : 0;
		final int n = Controller.getInstance().getRunningInstances();
		final Logger log = Logger.getLogger(BotChrome.class.getName());
		log.info(BotLocale.LOADINGTAB);
		if (!NetworkAccount.getInstance().isVIP() && Configuration.isServerOS()) {
			log.info(BotLocale.NEEDVIPVPS);
		} else if (n > 0 && !NetworkAccount.getInstance().isLoggedIn()) {
			log.severe(BotLocale.NEEDSIGNINMULTITAB);
		} else if (n > 2 && !NetworkAccount.getInstance().isVIP()) {
			log.severe(BotLocale.NEEDVIPMULTITAB);
		} else {
			final Map<String, String> info = NetworkAccount.getInstance().session(0);
			if (info == null || !info.containsKey("success") || !IniParser.parseBool(info.get("success"))) {
				final String msg = info != null && info.containsKey("message") ? info.get("message") : BotLocale.CANTOPENTAB;
				log.severe(msg);
			} else {
				if (s > 0) {
					Boot.fork(Boot.SWITCH_NEWTAB);
				} else {
					final Bot bot = Bot.getInstance();
					botButton.setVisible(true);
					new Thread(bot.threadGroup, bot).start();
					parent.panel.setBot(bot);
				}
			}
		}
		tabAdd.setEnabled(true);
	}

	public void closeTab(final boolean silent) {
		if (!Bot.isInstantiated()) {
			parent.panel.setBot(null);
			return;
		}
		boolean loggedIn = false;
		final Bot bot = Bot.getInstance();
		if (bot != null && bot.getClient() != null && bot.composite.multipliers != null && bot.composite.constants != null) {
			final int state = bot.getClient().getLoginIndex() * bot.composite.multipliers.GLOBAL_LOGININDEX;
			loggedIn = state == bot.composite.constants.CLIENTSTATE_11 || state == bot.composite.constants.CLIENTSTATE_12;
		}
		if (!silent) {
			try {
				if (loggedIn && JOptionPane.showConfirmDialog(parent, "Are you sure you want to close this tab?", BotLocale.CLOSETAB, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION) {
					return;
				}
			} catch (final RuntimeException ignored) {
			}
		}
		botButton.setVisible(false);
		parent.panel.setBot(null);
		updateScriptControls();
		Bot.getInstance().stop();
		parent.panel.repaint();
		Logger.getLogger(Bot.class.getName()).log(Level.INFO, "Add a tab to start another bot", "Closed");
		System.gc();
	}

	public void closeTabIfInactive() {
		closeTab(true);
	}

	public void updateScriptControls() {
		final boolean b = Bot.isInstantiated();
		scriptPlay.setVisible(b);
		scriptStop.setVisible(b);
		scriptPlay.setEnabled(b);
		scriptStop.setEnabled(b);
		if (b) {
			scriptPlay.setVisible(true);
			scriptStop.setVisible(true);
			scriptPlay.setEnabled(true);
			scriptStop.setEnabled(true);
			final Bot bot = Bot.getInstance();
			final ActiveScript script = bot.getActiveScript();
			final boolean running = script != null && script.isRunning();
			final boolean processing = running && !script.isPaused();
			scriptPlay.setIcon(new ImageIcon(Resources.getImage(processing ? Resources.Paths.PAUSE : Resources.Paths.PLAY)));
			scriptPlay.setToolTipText(processing ? BotLocale.PAUSESCRIPT : running ? BotLocale.RESUMESCRIPT : BotLocale.PLAYSCRIPT);
			scriptStop.setEnabled(running);
		}
	}

	private final class BotButton extends JButton {
		private static final long serialVersionUID = 1L;

		public BotButton(final String name) {
			super(name);
			setBackground(Color.BLACK);
			setForeground(Color.WHITE);
			setFont(getFont().deriveFont(Font.BOLD));
			setFocusable(false);
			setHorizontalTextPosition(SwingConstants.LEFT);
			setIcon(new ImageIcon(Resources.getImage(Resources.Paths.CROSS_SMALL_GRAY)));
			addMouseMotionListener(new MouseMotionAdapter() {
				@Override
				public void mouseMoved(final MouseEvent e) {
					if (e.getX() > getWidth() - getIcon().getIconWidth()
							&& e.getX() < getWidth() - getIconTextGap()) {
						setIcon(new ImageIcon(Resources.getImage(Resources.Paths.CROSS_SMALL)));
					} else {
						setIcon(new ImageIcon(Resources.getImage(Resources.Paths.CROSS_SMALL_GRAY)));
					}
				}
			});
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(final MouseEvent e) {
					if (e.getX() > getWidth() - getIcon().getIconWidth()
							&& e.getX() < getWidth() - getIconTextGap()) {
						closeTab(false);
					}
				}

				@Override
				public void mouseExited(final MouseEvent e) {
					setIcon(new ImageIcon(Resources.getImage(Resources.Paths.CROSS_SMALL_GRAY)));
				}
			});
		}
	}
}
