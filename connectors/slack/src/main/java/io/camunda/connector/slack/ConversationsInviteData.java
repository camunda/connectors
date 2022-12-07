package io.camunda.connector.slack;

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.conversations.ConversationsInviteRequest;
import com.slack.api.methods.response.conversations.ConversationsInviteResponse;
import io.camunda.connector.api.annotation.Secret;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class ConversationsInviteData implements SlackRequestData {

    @NotBlank
    @Secret
    private String channelName;
    @NotBlank
    @Secret
    private String users;

    @Override
    public SlackResponse invoke(MethodsClient methodsClient) throws SlackApiException, IOException {
        List<String> userList = DataLookupService.getUserIdsFromNameOrEmail(DataLookupService.convertStringToList(users), methodsClient);
        ConversationsInviteRequest request =
                ConversationsInviteRequest.builder()
                        .channel(DataLookupService.getChannelIdByName(channelName, methodsClient))
                        .users(userList)
                        .build();

        ConversationsInviteResponse response = methodsClient.conversationsInvite(request);

        if (response.isOk()) {
            return new ConversationsInviteSlackResponse(response);
        } else {
            throw new RuntimeException(response.getError());
        }
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getUsers() {
        return users;
    }

    public void setUsers(String users) {
        this.users = users;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConversationsInviteData that = (ConversationsInviteData) o;
        return channelName.equals(that.channelName) && Objects.equals(users, that.users);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channelName, users);
    }

    @Override
    public String toString() {
        return "ConversationsInviteData{" +
                "channelName='" + channelName + '\'' +
                ", users=" + users +
                '}';
    }
}
