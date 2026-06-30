package storagesign;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;
import org.bukkit.command.TabCompleter;

import storagesign.registry.MaterialRegistry;

/** Shared tab completion for StorageSign commands. */
public final class StorageSignCommandTabCompleter implements TabCompleter {
    private static final List<String> BASE_SEARCH_FLAGS = List.of("--contains", "--coords", "--front", "--world", "--page");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command == null) return List.of();

        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "storagesigngive" -> completeGive(args);
            case "storagesignindex" -> completeIndex(sender, args);
            case "storagesignsearch" -> completeSearch(sender, args);
            case "storagesignwarp" -> completeWarp(args);
            default -> List.of();
        };
    }

    private List<String> completeGive(String[] args) {
        if (args.length == 1) return complete(args[0], identifierCandidates());
        if (args.length == 2) return complete(args[1], List.of("1", "8", "16", "64", "128"));
        if (args.length == 3) return complete(args[2], signMaterialCandidates());
        return List.of();
    }

    private List<String> completeIndex(CommandSender sender, String[] args) {
        if (args.length == 1) return complete(args[0], List.of("status", "rebuild"));
        if (args.length == 2 && args[0].equalsIgnoreCase("rebuild")) {
            List<String> values = new ArrayList<>();
            values.add("all");
            for (World world : Bukkit.getWorlds()) values.add(world.getName());
            return complete(args[1], values);
        }
        return List.of();
    }

    private List<String> completeSearch(CommandSender sender, String[] args) {
        if (args.length == 1) return complete(args[0], List.of("item"));
        if (!args[0].equalsIgnoreCase("item")) return List.of();
        if (args.length == 2) return complete(args[1], identifierCandidates());
        if (args.length >= 3) {
            String current = args[args.length - 1];
            if ("--world".equalsIgnoreCase(args[args.length - 2])) {
                List<String> worlds = new ArrayList<>();
                for (World world : Bukkit.getWorlds()) worlds.add(world.getName());
                return complete(current, worlds);
            }
            if ("--page".equalsIgnoreCase(args[args.length - 2])) {
                return complete(current, List.of("1", "2", "3", "4", "5"));
            }
            return complete(current, remainingSearchFlags(args));
        }
        return List.of();
    }

    private List<String> completeWarp(String[] args) {
        if (args.length == 1) {
            LinkedHashSet<String> values = new LinkedHashSet<>();
            values.add("--hand");
            values.addAll(identifierCandidates());
            return complete(args[0], values);
        }
        return List.of();
    }

    private List<String> remainingSearchFlags(String[] args) {
        Set<String> used = new LinkedHashSet<>();
        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) used.add(arg.toLowerCase(Locale.ROOT));
        }
        List<String> remaining = new ArrayList<>();
        for (String flag : BASE_SEARCH_FLAGS) {
            if (used.contains(flag.toLowerCase(Locale.ROOT))) continue;
            if (("--coords".equals(flag) && used.contains("--front"))
                || ("--front".equals(flag) && used.contains("--coords"))) {
                continue;
            }
            remaining.add(flag);
        }
        return remaining;
    }

    private List<String> complete(String token, Collection<String> candidates) {
        List<String> matches = new ArrayList<>();
        StringUtil.copyPartialMatches(token == null ? "" : token, candidates, matches);
        matches.sort(String.CASE_INSENSITIVE_ORDER);
        return matches;
    }

    private List<String> identifierCandidates() {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.addAll(ConfigLoader.getIdentifierAliases().keySet());
        values.addAll(StorageSign.DEFAULT_IDENTIFIER_ALIASES.keySet());
        values.addAll(ConfigLoader.getVirtualItemIdentifiers().keySet());
        values.addAll(StorageSign.DEFAULT_VIRTUAL_IDENTIFIERS.keySet());
        for (Material material : Material.values()) {
            if (material.isLegacy()) continue;
            values.add(material.name());
        }
        return List.copyOf(values);
    }

    private List<String> signMaterialCandidates() {
        return MaterialRegistry.SIGN_MATERIALS.stream().map(Material::name).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
