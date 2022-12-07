package io.camunda.connector.slack;

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.conversations.ConversationsListRequest;
import com.slack.api.methods.request.users.UsersListRequest;
import com.slack.api.methods.request.users.UsersLookupByEmailRequest;
import com.slack.api.methods.response.conversations.ConversationsListResponse;
import com.slack.api.methods.response.users.UsersListResponse;
import com.slack.api.methods.response.users.UsersLookupByEmailResponse;
import com.slack.api.model.Conversation;
import com.slack.api.model.User;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataLookupService {

    private static final String EMAIL_REGEX = "^.+[@].+[.].{2,4}$";

    public static List<String> convertStringToList(String string) {
        if(StringUtils.isBlank(string)) {
            return new ArrayList<>();
        }
        return Arrays.stream(string.split(",")).map(s -> s.trim()).collect(Collectors.toList());
    }

    public static boolean isEmail(final String str) {
        return str.matches(EMAIL_REGEX);
    }

    public static List<String> getUserIdsFromNameOrEmail(List<String> userList, MethodsClient methodsClient) {
        if(Objects.isNull(userList) || userList.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> emails = new ArrayList<>();
        List<String> usernames = new ArrayList<>();
        userList.stream().forEach(user -> (isEmail(user) ? emails : usernames).add(user));

        List<String> idListByEmail = emails.stream()
                .map(email -> {
                    try {
                        return getUserIdByEmail(email, methodsClient);
                    } catch (Exception e) {
                        throw new RuntimeException("Unable to find user with name or email : " + email, e);
                    }
                })
                .collect(Collectors.toList());

        List<String> idListByUserName = getIdListByUserNameList(usernames, methodsClient);

        return Stream.concat(idListByEmail.stream(), idListByUserName.stream()).collect(Collectors.toList());
    }

    public static String getUserIdByEmail(String email, MethodsClient methodsClient) throws SlackApiException, IOException {
        UsersLookupByEmailRequest lookupByEmailRequest = UsersLookupByEmailRequest.builder().email(email).build();
        return Optional.ofNullable(methodsClient.usersLookupByEmail(lookupByEmailRequest))
                .filter(UsersLookupByEmailResponse::isOk)
                .map(UsersLookupByEmailResponse::getUser)
                .map(User::getId)
                .orElseThrow(() -> new RuntimeException("Unable to find user with email: " + email));
    }

    public static List<String> getIdListByUserNameList(List<String> userNameList, MethodsClient methodsClient) {
        if(userNameList == null || userNameList.isEmpty()) {
            return new ArrayList<>();
        }
        String nextCursor = null;
        List<String> idList = new ArrayList<>();
        do {
            UsersListRequest request = UsersListRequest.builder().limit(100).cursor(nextCursor).build();

            try {
                UsersListResponse response = methodsClient.usersList(request);
                if (response.isOk()) {
                        idList.addAll(response.getMembers().stream()
                                    .filter(user -> userNameList.contains(user.getRealName()))
                                    .map(User::getId)
                                    .collect(Collectors.toList()));
                        nextCursor = response.getResponseMetadata().getNextCursor();
                } else {
                    throw new RuntimeException(
                            "Unable to get users; message: " + response.getError());
                }
            } catch (Exception e) {
                throw new RuntimeException("Unable to find users by name", e);
            }

        } while (idList.size() < userNameList.size() && nextCursor != null && !nextCursor.isBlank());
        return idList;
    }

    public static String getChannelIdByName(String channelName, MethodsClient methodsClient) {
        String channelId = null;
        String nextCursor = null;

        do {
            ConversationsListRequest request = ConversationsListRequest.builder().limit(100).cursor(nextCursor).build();

            try {
                ConversationsListResponse response = methodsClient.conversationsList(request);
                if (response.isOk()) {
                    channelId =
                            response.getChannels().stream()
                                    .filter(channel -> channelName.equals(channel.getName()))
                                    .map(Conversation::getId)
                                    .findFirst()
                                    .orElse(null);
                    nextCursor = response.getResponseMetadata().getNextCursor();
                } else {
                    throw new RuntimeException(
                            "Unable to find conversation with channel name: " + channelName + "; message: " + response.getError());
                }
            } catch (Exception e) {
                throw new RuntimeException("Unable to find conversation with name: " + channelName, e);
            }

        } while (channelId == null && nextCursor != null && !nextCursor.isBlank());

        if (channelId == null) {
            throw new RuntimeException("Unable to find conversation with name: " + channelName);
        }

        return channelId;
    }

    public static String getUserIdByName(String userName, MethodsClient methodsClient) {
        String userId = null;
        String nextCursor = null;

        do {
            UsersListRequest request = UsersListRequest.builder().limit(100).cursor(nextCursor).build();

            try {
                UsersListResponse response = methodsClient.usersList(request);
                if (response.isOk()) {
                    userId =
                            response.getMembers().stream()
                                    .filter(user -> userName.equals(user.getRealName()))
                                    .map(User::getId)
                                    .findFirst()
                                    .orElse(null);
                    nextCursor = response.getResponseMetadata().getNextCursor();
                } else {
                    throw new RuntimeException(
                            "Unable to find user with name: " + userName + "; message: " + response.getError());
                }
            } catch (Exception e) {
                throw new RuntimeException("Unable to find user with name: " + userName, e);
            }

        } while (userId == null && nextCursor != null && !nextCursor.isBlank());

        if (userId == null) {
            throw new RuntimeException("Unable to find user with name: " + userName);
        }

        return userId;
    }


}
