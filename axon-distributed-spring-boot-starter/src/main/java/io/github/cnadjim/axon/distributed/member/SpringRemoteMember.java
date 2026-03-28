package com.axon.distributed.member;

import java.time.Instant;
import java.util.Set;

public class SpringRemoteMember implements RemoteMember {

    private final String name;
    private final int loadFactor;
    private final Instant lastHeartbeat;
    private final Set<String> queryNames;
    private final Set<String> commandNames;

    public SpringRemoteMember(String name,
                              int loadFactor,
                              Instant lastHeartbeat,
                              Set<String> queryNames,
                              Set<String> commandNames) {
        this.name = name;
        this.lastHeartbeat = lastHeartbeat;
        this.loadFactor = loadFactor;
        this.commandNames = commandNames;
        this.queryNames = queryNames;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int getLoadFactor() {
        return loadFactor;
    }

    @Override
    public Set<String> getCommandCapabilities() {
        return commandNames;
    }

    @Override
    public Set<String> getQueryCapabilities() {
        return queryNames;
    }

    @Override
    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }
}
