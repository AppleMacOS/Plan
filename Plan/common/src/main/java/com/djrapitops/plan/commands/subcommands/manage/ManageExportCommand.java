/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License v3 as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Plan is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with Plan. If not, see <https://www.gnu.org/licenses/>.
 */
package com.djrapitops.plan.commands.subcommands.manage;

import com.djrapitops.plan.delivery.export.Exporter;
import com.djrapitops.plan.delivery.export.HtmlExport;
import com.djrapitops.plan.exceptions.ExportException;
import com.djrapitops.plan.processing.Processing;
import com.djrapitops.plan.settings.Permissions;
import com.djrapitops.plan.settings.config.PlanConfig;
import com.djrapitops.plan.settings.config.paths.ExportSettings;
import com.djrapitops.plan.settings.locale.Locale;
import com.djrapitops.plan.settings.locale.lang.CmdHelpLang;
import com.djrapitops.plan.settings.locale.lang.CommandLang;
import com.djrapitops.plan.settings.locale.lang.DeepHelpLang;
import com.djrapitops.plan.settings.locale.lang.ManageLang;
import com.djrapitops.plan.storage.database.DBSystem;
import com.djrapitops.plan.storage.database.Database;
import com.djrapitops.plan.storage.database.queries.objects.UserIdentifierQueries;
import com.djrapitops.plugin.command.ColorScheme;
import com.djrapitops.plugin.command.CommandNode;
import com.djrapitops.plugin.command.CommandType;
import com.djrapitops.plugin.command.Sender;
import com.djrapitops.plugin.utilities.Verify;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * This manage SubCommand is used to import data from 3rd party plugins.
 *
 * @author Rsl1122
 */
@Singleton
public class ManageExportCommand extends CommandNode {

    private final Locale locale;
    private final ColorScheme colorScheme;
    private final PlanConfig config;
    private final DBSystem dbSystem;
    private final Exporter exporter;
    private final HtmlExport htmlExport;
    private final Processing processing;

    @Inject
    public ManageExportCommand(
            Locale locale,
            ColorScheme colorScheme,
            PlanConfig config,
            DBSystem dbSystem,
            Exporter exporter,
            Processing processing,
            HtmlExport htmlExport
    ) {
        super("export", Permissions.MANAGE.getPermission(), CommandType.CONSOLE);

        this.locale = locale;
        this.colorScheme = colorScheme;
        this.config = config;
        this.dbSystem = dbSystem;
        this.exporter = exporter;
        this.htmlExport = htmlExport;
        this.processing = processing;

        setArguments("<export_kind>/list");
        setShortHelp(locale.getString(CmdHelpLang.MANAGE_EXPORT));
        setInDepthHelp(locale.getArray(DeepHelpLang.MANAGE_EXPORT));
    }

    @Override
    public void onCommand(Sender sender, String commandLabel, String[] args) {
        Verify.isTrue(args.length >= 1,
                () -> new IllegalArgumentException(locale.getString(CommandLang.FAIL_REQ_ARGS, "1+", Arrays.toString(this.getArguments()))));

        String exportArg = args[0];

        if ("list".equals(exportArg)) {
            sender.sendMessage("> " + colorScheme.getMainColor() + "players");
            return;
        }

        Database.State dbState = dbSystem.getDatabase().getState();
        if (dbState != Database.State.OPEN) {
            sender.sendMessage(locale.getString(CommandLang.FAIL_DATABASE_NOT_OPEN, dbState.name()));
            return;
        }

        getExportFunction(exportArg).accept(sender);
    }

    private Consumer<Sender> getExportFunction(String exportArg) {
        if ("players".equals(exportArg)) {
            return this::exportPlayers;
        }
        return sender -> sender.sendMessage(locale.getString(ManageLang.FAIL_EXPORTER_NOT_FOUND, exportArg));
    }

    private void exportPlayers(Sender sender) {
        sender.sendMessage(locale.getString(ManageLang.PROGRESS_START));
        if (config.get(ExportSettings.PLAYERS_PAGE)) {
            processing.submitNonCritical(htmlExport::exportPlayersPage);
        }
        Boolean exportPlayerJSON = config.get(ExportSettings.PLAYER_JSON);
        Boolean exportPlayerHTML = config.get(ExportSettings.PLAYER_PAGES);
        if (!exportPlayerJSON && !exportPlayerHTML) {
            sender.sendMessage(locale.getString(ManageLang.PROGRESS_FAIL));
            sender.sendMessage("§c'" + ExportSettings.PLAYER_JSON.getPath() + "' & '" + ExportSettings.PLAYER_PAGES.getPath() + "': false");
            return;
        }

        processing.submitNonCritical(() -> {
            Map<UUID, String> players = dbSystem.getDatabase().query(UserIdentifierQueries.fetchAllPlayerNames());
            int size = players.size();
            int failed = 0;

            int i = 1;
            for (Map.Entry<UUID, String> entry : players.entrySet()) {
                try {
                    if (exportPlayerJSON) exporter.exportPlayerJSON(entry.getKey(), entry.getValue());
                    if (exportPlayerHTML) exporter.exportPlayerPage(entry.getKey(), entry.getValue());
                } catch (ExportException e) {
                    failed++;
                }
                i++;
                if (i % 1000 == 0) {
                    sender.sendMessage(i + " / " + size + " processed..");
                }
            }
            sender.sendMessage(locale.getString(ManageLang.PROGRESS_SUCCESS));
            if (failed != 0) {
                sender.sendMessage(locale.getString(ManageLang.PROGRESS_FAIL));
                sender.sendMessage(" §2✔: §f" + (i - failed));
                sender.sendMessage(" §c✕: §f" + failed);
            }
        });
    }
}
