package fr.smp.core.managers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Short-lived, in-memory team invitations: invitee UUID -> team id. */
public class TeamInviteManager {

    private final Map<UUID, String> invites = new ConcurrentHashMap<>();

    public void invite(UUID invitee, String teamId) {
        invites.put(invitee, teamId);
    }

    public String consume(UUID invitee) {
        return invites.remove(invitee);
    }

    public String peek(UUID invitee) {
        return invites.get(invitee);
    }

    public void clear(UUID invitee) {
        invites.remove(invitee);
    }
}
