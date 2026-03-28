package io.github.cnadjim.axon.distributed.member;

import java.util.HashSet;
import java.util.Set;

public class SpringLocalMember implements LocalMember {

    private final String name;
    private final Set<String> commandNames;
    private final Set<String> queryNames;

    public SpringLocalMember(String name) {
        this.name = name;
        this.commandNames = new HashSet<>();
        this.queryNames = new HashSet<>();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void addCommandCapability(String commandName) {
        this.commandNames.add(commandName);
    }

    @Override
    public void addQueryCapability(String queryName) {
        this.queryNames.add(queryName);
    }

    @Override
    public Set<String> getCommandCapabilities() {
        return commandNames;
    }

    @Override
    public Set<String> getQueryCapabilities() {
        return queryNames;
    }
}
