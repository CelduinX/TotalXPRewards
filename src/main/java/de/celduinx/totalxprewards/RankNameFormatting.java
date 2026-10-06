package de.celduinx.totalxprewards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Converts ranks.yml names for LuckPerms metadata consumed by chat plugins. */
public final class RankNameFormatting {
    private RankNameFormatting() {}

    public static String plainName(String input) {
        return PlainTextComponentSerializer.plainText().serialize(parse(input));
    }

    public static String formattedName(String input) {
        Component component = parse(input);
        // EssentialsX accepts &#RRGGBB. Unlike §x§R§R§G§G§B§B this stays
        // compact in LuckPerms' 200-character permission column.
        String result = LegacyComponentSerializer.builder().character('§').hexColors()
                .build().serialize(component).replace('§', '&');
        if (result.length() <= 160) return result;
        // Preserve a readable suffix even for exceptionally long configured names.
        String firstColor = result.startsWith("&#") ? result.substring(0, 8) : "&e";
        return firstColor + plainName(input);
    }

    private static Component parse(String input) {
        if (input == null) return Component.empty();
        // The existing ranks.yml uses & codes. Convert them before MiniMessage parsing.
        StringBuilder text = new StringBuilder(input.length());
        String codes = "0123456789abcdefklmnor";
        String[] tags = {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red",
                "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua",
                "red", "light_purple", "yellow", "white", "obfuscated", "bold",
                "strikethrough", "underlined", "italic", "reset"};
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < input.length()) {
                int code = codes.indexOf(Character.toLowerCase(input.charAt(i + 1)));
                if (code >= 0) {
                    text.append('<').append(tags[code]).append('>');
                    i++;
                    continue;
                }
            }
            text.append(c);
        }
        return MiniMessage.miniMessage().deserialize(text.toString());
    }
}
