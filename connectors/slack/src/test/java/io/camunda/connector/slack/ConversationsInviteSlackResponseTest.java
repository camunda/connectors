package io.camunda.connector.slack;

import com.slack.api.methods.response.conversations.ConversationsInviteResponse;
import com.slack.api.model.Conversation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ConversationsInviteSlackResponseTest {
    private static final String CHANNEL_NAME = "channel";
    @Mock private ConversationsInviteResponse conversationsInviteResponse;
    @Mock private Conversation conversation;

    @Test
    public void shouldReturnCorrectResponse() {
        // Given
        when(conversationsInviteResponse.getChannel()).thenReturn(conversation);
        when(conversation.getName()).thenReturn(CHANNEL_NAME);
        // When
        ConversationsInviteSlackResponse response =
                new ConversationsInviteSlackResponse(conversationsInviteResponse);
        // Then
        assertThat(response.getChannel().getName()).isEqualTo(CHANNEL_NAME);
    }

}
