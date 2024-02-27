package com.erudika.scoold.slack;

import com.erudika.scoold.controllers.QuestionsController;
import com.erudika.scoold.core.Comment;
import com.erudika.scoold.core.Profile;
import com.erudika.scoold.core.Question;
import com.erudika.scoold.utils.ScooldUtils;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.conversations.ConversationsListRequest;
import com.slack.api.methods.request.conversations.ConversationsRepliesRequest;
import com.slack.api.methods.request.search.SearchMessagesRequest;
import com.slack.api.methods.response.conversations.ConversationsRepliesResponse;
import com.slack.api.model.Conversation;
import com.slack.api.model.MatchedItem;
import com.slack.api.model.Message;
import com.slack.api.model.SearchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import com.erudika.para.core.utils.Config;
import com.erudika.para.core.utils.Para;
import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class SlackAPI {
    private final String authToken;
    private final Slack slack;
    private final MethodsClient methods;

    @Autowired
    private ScooldUtils scooldUtils;

    public SlackAPI() {
        authToken = ScooldUtils.getConfig().slackAPIToken();
        slack = Slack.getInstance();
        methods = slack.methods(authToken);
    }

    public ArrayList<String> listChannels() {
        try {
            ArrayList<String> channels = new ArrayList<>();
            ConversationsListRequest request = ConversationsListRequest.builder().build();
            request.setToken(authToken);
            request.setLimit(10);
            request.setExcludeArchived(true);
            for (Conversation channel : methods.conversationsList(request).getChannels()) {
                if (channel.getName().contains("ask")) {
                    channels.add(channel.getName());
                }
            }
            return channels;
        } catch (SlackApiException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<MatchedItem> searchMessages() {
        try {
            List<MatchedItem> messages = new ArrayList<>();
            SearchMessagesRequest request = SearchMessagesRequest.builder().build();
            request.setToken(authToken);

            for (String channel : listChannels()) {
                request.setQuery(String.format("in:#%s ?", channel));
                messages.addAll(methods.searchMessages(request).getMessages().getMatches());
            }

            return messages;
        } catch (SlackApiException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<List<Message>> getMainMessagesAndThreads() {
        // Loops through each message
        // Uses the conversations.replies API method to determine which message is a "main" message (question) or a response (answer)
        // returns only the main messages in as a list in a format somewhat like the one here: https://expedia.slack.com/archives/C0607N98RHA/p1696977461311769?thread_ts=1696976547.185539&cid=C0607N98RHA
        // doesn't necessarily need to return a list of MatchedItems, just a placeholder
        try {
            List<List<Message>> messages = new ArrayList<>();
            ConversationsRepliesRequest request = ConversationsRepliesRequest.builder().build();
            request.setToken(authToken);
            for (MatchedItem message : searchMessages()) {
                request.setTs(message.getTs());
                request.setChannel(message.getChannel().getId());
                ConversationsRepliesResponse response = methods.conversationsReplies(request);

                if (isMainMessage(response.getMessages().size(), response.getMessages().get(0))) {
                    messages.add(response.getMessages());
                }
            }
            return messages;
        } catch (SlackApiException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // public mainMessagesTo

    public boolean isMainMessage(int size, Message message) {
        // explanation for deciding this is shown in the following slack thread:
        // https://expedia.slack.com/archives/C0607N98RHA/p1696976547185539
        if (size > 1) {
            return true;
        }
        return message.getThreadTs() == null;
    }

    public void createPosts() {
        List<List<Message>> messages = getMainMessagesAndThreads();
        for (List<Message> thread : messages) {
            Message mainMessage = thread.get(0);
            String title = mainMessage.getText();
            Message.MessageItem comment = mainMessage.getComment();
            Question question = new Question();
            question.setTitle(title);
            question.setComments((List<Comment>) comment);
            List<String> list = new ArrayList<>();
            list.add("Expedia");
            question.setTags(list);
            ScooldUtils.getInstance().getParaClient().create(question);
        }

    }
}