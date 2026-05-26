package com.waitlist.notification;

import com.waitlist.events.SignupEvent;
import com.waitlist.notification.service.EmailService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.thymeleaf.TemplateEngine;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 1,
    topics = {
        "waitlist.signup",
        "waitlist.signup.dlt",
        "waitlist.status-changed",
        "waitlist.status-changed.dlt"
    }
)
class DltRoutingTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @MockBean private EmailService emailService;
    @MockBean private JavaMailSender javaMailSender;
    @MockBean private TemplateEngine templateEngine;

    @Test
    void whenEmailServiceAlwaysThrows_signupRecordLandsOnDlt() throws Exception {
        doThrow(new RuntimeException("simulated mail failure"))
                .when(emailService).sendConfirmation(any(SignupEvent.class));

        var event = new SignupEvent(
                UUID.randomUUID(), Instant.now(), 1L,
                "dlt-test@example.com", "DLT Tester", "ACME",
                "DLT123", null);

        kafkaTemplate.send("waitlist.signup", event.email(), event).get();

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "dlt-verifier", "false", embeddedKafka);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, byte[]> dltConsumer =
                     new DefaultKafkaConsumerFactory<String, byte[]>(consumerProps).createConsumer()) {
            embeddedKafka.consumeFromAnEmbeddedTopic(dltConsumer, "waitlist.signup.dlt");

            ConsumerRecords<String, byte[]> dltRecords =
                    KafkaTestUtils.getRecords(dltConsumer, Duration.ofSeconds(15));

            assertThat(dltRecords.isEmpty())
                    .as("Expected at least one record in waitlist.signup.dlt")
                    .isFalse();
        }
    }
}
