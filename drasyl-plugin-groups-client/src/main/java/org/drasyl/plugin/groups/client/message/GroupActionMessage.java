package org.drasyl.plugin.groups.client.message;

import org.drasyl.plugin.groups.client.Group;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

abstract class GroupActionMessage implements GroupsPluginMessage {
    protected final Group group;

    protected GroupActionMessage(final Group group) {
        this.group = requireNonNull(group);
    }

    public Group getGroup() {
        return group;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final GroupActionMessage that = (GroupActionMessage) o;
        return Objects.equals(group, that.group);
    }

    @Override
    public int hashCode() {
        return Objects.hash(group);
    }
}
