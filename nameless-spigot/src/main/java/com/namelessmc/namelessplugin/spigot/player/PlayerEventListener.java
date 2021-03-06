package com.namelessmc.namelessplugin.spigot.player;

import java.io.File;
import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.namelessmc.namelessplugin.spigot.NamelessPlugin;
import com.namelessmc.namelessplugin.spigot.API.NamelessAPI;
import com.namelessmc.namelessplugin.spigot.API.Player.NamelessPlayer;
import com.namelessmc.namelessplugin.spigot.API.Player.NamelessPlayerNotifications;
import com.namelessmc.namelessplugin.spigot.API.Player.NamelessPlayerSetGroup;
import com.namelessmc.namelessplugin.spigot.API.Player.NamelessPlayerUpdateUsername;
import com.namelessmc.namelessplugin.spigot.API.utils.NamelessChat;
import com.namelessmc.namelessplugin.spigot.API.utils.NamelessMessages;

public class PlayerEventListener implements Listener {

	NamelessPlugin plugin;

	/*
	 * NamelessConfigs Files
	 */
	YamlConfiguration config;
	YamlConfiguration playerDataConfig;
	YamlConfiguration permissionConfig;

	/*
	 * Constructer
	 */
	public PlayerEventListener(NamelessPlugin pluginInstance) {
		this.plugin = pluginInstance;
	}

	/*
	 * User File check, Name Check, Get notification, Group sync.
	 */
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();

		Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {

			@Override
			public void run() {
				// TODO Auto-generated method stub
				if (plugin.hasSetUrl()) {
					NamelessAPI api = plugin.getAPI();
					NamelessChat chat = api.getChat();
					NamelessPlayer namelessPlayer = api.getPlayer(player.getUniqueId().toString());
					userFileCheck(player);
					if (namelessPlayer.exists()) {
						if (namelessPlayer.isValidated()) {
							userNameCheck(player);
							userGetNotification(player);
							userGroupSync(player);
						} else {
							player.sendMessage(chat.convertColors(chat.getMessage(NamelessMessages.PLAYER_NOT_VALID)));
						}
					}
				}
			}
		});
	}

	/*
	 * User Notifications.
	 */
	public void userGetNotification(Player player) {
		config = plugin.getAPI().getConfigs().getConfig();
		if (config.getBoolean("join-notifications")) {
			NamelessAPI api = plugin.getAPI();
			NamelessChat chat = api.getChat();
			NamelessPlayer namelessPlayer = api.getPlayer(player.getUniqueId().toString());
			NamelessPlayerNotifications notifications = namelessPlayer.getNotifications();
			Integer pms = notifications.getPMs();
			Integer alerts = notifications.getAlerts();
			String errorMessage = notifications.getErrorMessage();
			boolean hasError = notifications.hasError();

			String pmMessage = chat.getMessage(NamelessMessages.PM_NOTIFICATIONS_MESSAGE).replaceAll("%pms%",
					pms.toString());
			String alertMessage = chat.getMessage(NamelessMessages.ALERTS_NOTIFICATIONS_MESSAGE).replaceAll("%alerts%",
					alerts.toString());
			String noNotifications = chat.getMessage(NamelessMessages.NO_NOTIFICATIONS);

			if (hasError) {
				// Error with request
				player.sendMessage(ChatColor.RED + "Error: " + errorMessage);
			} else if (alerts.equals(0) && pms.equals(0)) {
				player.sendMessage(chat.convertColors(noNotifications));
			} else if (alerts.equals(0)) {
				player.sendMessage(chat.convertColors(pmMessage));
			} else if (pms.equals(0)) {
				player.sendMessage(chat.convertColors(alertMessage));
			} else {
				player.sendMessage(chat.convertColors(alertMessage));
				player.sendMessage(chat.convertColors(pmMessage));
			}
		}
	}

	/*
	 * User Group Synchronization.
	 */
	public void userGroupSync(Player player) {
		config = plugin.getAPI().getConfigs().getConfig();
		if (config.getBoolean("group-synchronization")) {
			permissionConfig = plugin.getAPI().getConfigs().getGroupSyncPermissionsConfig();
			ConfigurationSection section = permissionConfig.getConfigurationSection("permissions");
			try {
				for (String groupID : section.getKeys(true)) {
					NamelessPlayer namelessPlayer = plugin.getAPI().getPlayer(player.getUniqueId().toString());
					if (player.hasPermission(section.getString(groupID))) {
						if (namelessPlayer.getGroupID().toString().equals(groupID)) {
							return;
						} else {
							Integer previousgroup = namelessPlayer.getGroupID();
							namelessPlayer.setGroupID(groupID);

							NamelessPlayerSetGroup group = namelessPlayer.setGroupID(groupID);
							if (group.hasError()) {
								plugin.getAPI().getChat().sendToLog(NamelessMessages.PREFIX_WARNING,
										"&4Error trying to change &c" + player.getName() + "'s group: &4"
												+ group.getErrorMessage());
							} else if (group.hasSucceseded()) {
								plugin.getAPI().getChat().sendToLog(NamelessMessages.PREFIX_INFO,
										"&aSuccessfully changed &b" + player.getName() + "'s &agroup from &b"
												+ previousgroup + " &ato &b" + group.getNewGroup() + "&a!");
							}
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/*
	 * Check if the user exists in the Players Information File.
	 */
	public void userFileCheck(Player player) {

		playerDataConfig = plugin.getAPI().getConfigs().getPlayerDataConfig();

		if (!playerDataConfig.contains(player.getUniqueId().toString())) {
			plugin.getAPI().getChat().sendToLog(NamelessMessages.PREFIX_WARNING,

					"&c" + player.getName() + " &4is not contained in the Players Data File..");
			plugin.getAPI().getChat().sendToLog(NamelessMessages.PREFIX_INFO,

					"&aAdding &b" + player.getName() + " &ato the Players Data File.");
			playerDataConfig.set(player.getUniqueId().toString() + ".Username", player.getName());
			playerDataConfig.options().copyDefaults(true);

			try {
				playerDataConfig.save(new File(plugin.getDataFolder(), "PlayersData.yml"));
				plugin.getAPI().getChat().sendToLog(NamelessMessages.PREFIX_INFO,

						"&aAdded &b" + player.getName() + " &ato the Players Data File.");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/*
	 * Update Username on Login.
	 */
	public void userNameCheck(Player player) {

		config = plugin.getAPI().getConfigs().getConfig();
		playerDataConfig = plugin.getAPI().getConfigs().getPlayerDataConfig();

		// Check if user has changed Username
		// If so, change the username in the Players Information File.
		// And change the username on the website.
		if (config.getBoolean("update-username")) {
			if (!playerDataConfig.getString(player.getUniqueId().toString() + ".Username").equals(player.getName())
					&& playerDataConfig.contains(player.getUniqueId().toString())) {
				plugin.getAPI().getChat().sendToLog(NamelessMessages.PREFIX_INFO,

						"&aDetected that &b" + player.getName() + " &ahas changed his/her username!");
				String previousUsername = playerDataConfig.get(player.getUniqueId().toString() + ".Username")
						.toString();
				String newUsername = player.getName();
				playerDataConfig.set(player.getUniqueId().toString() + ".PreviousUsername", previousUsername);
				playerDataConfig.set(player.getUniqueId().toString() + ".Username", newUsername);
				playerDataConfig.options().copyDefaults(true);

				try {
					playerDataConfig.save(new File(plugin.getDataFolder(), "PlayersData.yml"));
					plugin.getAPI().getChat().sendToLog(NamelessMessages.PREFIX_INFO,
							"&aChanged &b" + player.getName() + "'s &ausername in the Players Data File.");
				} catch (IOException e) {
					e.printStackTrace();
				}

				NamelessPlayer namelessPlayer = plugin.getAPI().getPlayer(player.getUniqueId().toString());
				// Changing username on Website here.
				if (!namelessPlayer.getUserName().equals(newUsername)) {
					System.out.println(namelessPlayer.getUserName());
					NamelessPlayerUpdateUsername updateUsername = namelessPlayer.updateUsername(newUsername);
					if (updateUsername.hasError()) {
						plugin.getAPI().getChat().sendToLog(NamelessMessages.PREFIX_WARNING,

								"Failed changing &c" + player.getName() + "'s &4username in the website");
					} else {
						plugin.getAPI().getChat().sendToLog(NamelessMessages.PREFIX_INFO,
								"&aChanged &b" + player.getName() + "'s &ausername in the Website");
					}
				}
			}
		}
	}

}