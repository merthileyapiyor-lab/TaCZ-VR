package com.taczvr;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which players are down on the ground in the zombie waves, as the client knows it (entity ids, sent by the
 * server). Every side works out player poses itself each tick, so each needs to know.
 */
public final class DownedState {
    public static final Set<Integer> CLIENT = ConcurrentHashMap.newKeySet();

    private DownedState() {
    }
}
