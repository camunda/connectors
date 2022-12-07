package io.camunda.connector.slack;

import com.slack.api.methods.response.conversations.ConversationsInviteResponse;
import java.util.Objects;

public class ConversationsInviteSlackResponse implements SlackResponse {

    private final Conversation channel;
    private final String needed;
    private final String provided;

    public ConversationsInviteSlackResponse(ConversationsInviteResponse conversationsInviteResponse) {
        this.channel = new Conversation(conversationsInviteResponse.getChannel());
        this.needed = conversationsInviteResponse.getNeeded();
        this.provided = conversationsInviteResponse.getProvided();
    }

    public Conversation getChannel() {
        return channel;
    }

    public String getNeeded() {
        return needed;
    }

    public String getProvided() {
        return provided;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConversationsInviteSlackResponse that = (ConversationsInviteSlackResponse) o;
        return Objects.equals(channel, that.channel) && Objects.equals(needed, that.needed) && Objects.equals(provided, that.provided);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channel, needed, provided);
    }
}
