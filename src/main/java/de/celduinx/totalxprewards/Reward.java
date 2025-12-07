package de.celduinx.totalxprewards;

import java.util.List;

/**
 * Represents a reward configuration consisting of:
 * - a threshold (XP amount required)
 * - a list of console commands to execute
 * - an optional broadcast message
 * - a display name for the rank
 *
 * Commands are executed in the order defined in the config.
 * The threshold must be positive.
 */
public class Reward {

    private final long threshold;
    private final List<String> commands;
    private final String broadcast;
    private final String name;

    /**
     * Creates a new reward definition.
     *
     * @param threshold the XP threshold required to trigger this reward
     * @param commands  a list of commands to execute when the reward triggers
     * @param broadcast an optional broadcast message
     * @param name      the display name of this rank
     */
    public Reward(long threshold, List<String> commands, String broadcast, String name) {
        this.threshold = threshold;
        this.commands = commands;
        this.broadcast = broadcast;
        this.name = name;
    }

    /**
     * @return the XP threshold required to trigger this reward
     */
    public long getThreshold() {
        return threshold;
    }

    /**
     * @return the list of commands that should be executed
     */
    public List<String> getCommands() {
        return commands;
    }

    /**
     * @return the broadcast message (may be null or empty)
     */
    public String getBroadcast() {
        return broadcast;
    }

    /**
     * @return the display name of this rank
     */
    public String getName() {
        return name;
    }
}
