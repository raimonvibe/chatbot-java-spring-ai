package com.prayer_chat.chatbot.repository;

import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.Conversation;
import com.prayer_chat.chatbot.model.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("MessageRepository userIp counting tests")
class MessageRepositoryUserIpTest {

    @Autowired private TestEntityManager em;
    @Autowired private MessageRepository messageRepository;

    @Test
    @DisplayName("Counts today's user messages by conversation.userIp")
    void countsTodayUserMessagesByUserIp() {
        Chatbot bot = new Chatbot("Bot", "https://example.com");
        bot.setIsActive(true);
        em.persist(bot);

        Conversation c1 = new Conversation(bot, "s1");
        c1.setUserIp("203.0.113.55");
        c1.setUserAgent("ua");
        c1.setUserLanguage("en");
        em.persist(c1);

        // user message -> should count
        em.persist(new Message(c1, Message.MessageType.TEXT, "hi", true));
        // AI message -> should NOT count
        em.persist(new Message(c1, Message.MessageType.TEXT, "reply", false));

        Conversation c2 = new Conversation(bot, "s2");
        c2.setUserIp("203.0.113.99");
        em.persist(c2);
        em.persist(new Message(c2, Message.MessageType.TEXT, "hi", true));

        em.flush();

        Long countC1Ip = messageRepository.countUserMessagesTodayByUserIp("203.0.113.55");
        Long countOtherIp = messageRepository.countUserMessagesTodayByUserIp("203.0.113.99");
        Long countUnknown = messageRepository.countUserMessagesTodayByUserIp("203.0.113.1");

        assertThat(countC1Ip).isEqualTo(1L);
        assertThat(countOtherIp).isEqualTo(1L);
        assertThat(countUnknown).isEqualTo(0L);
    }
}

