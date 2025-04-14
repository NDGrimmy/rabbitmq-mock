package com.Grimmer_MockNode_Tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.fridujo.rabbitmq.mock.AmqArguments;
import com.github.fridujo.rabbitmq.mock.MockChannel;
import com.github.fridujo.rabbitmq.mock.MockConnection;
import com.github.fridujo.rabbitmq.mock.MockNode;
import com.github.fridujo.rabbitmq.mock.MockQueue;
import com.github.fridujo.rabbitmq.mock.Receiver;
import com.github.fridujo.rabbitmq.mock.ReceiverPointer;
import com.github.fridujo.rabbitmq.mock.ReceiverRegistry;
import com.github.fridujo.rabbitmq.mock.metrics.MetricsCollectorWrapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.GetResponse;

public class basicRejectTest {
    
    private MockNode mockNode;
    private MockQueue mockQueue;
    private MockConnection mockConnection;
    private Consumer mockConsumer;
    private Supplier<Long> deliveryTagSupplier;
    private MockChannel mockChannel;
    private ReceiverRegistry receiverRegistry = new ReceiverRegistry() {
        @Override
        public Optional<Receiver> getReceiver(ReceiverPointer receiverPointer) {
            return Optional.empty(); // No actual receiver used in this test
        }
    };

    // All are used in these tests
    @BeforeEach
    void setUp() {
        // Create a real MockNode instance
        mockNode = new MockNode();

        // Create a real MockQueue with valid constructor arguments
        mockQueue = new MockQueue("testQueue", AmqArguments.empty(), receiverRegistry);

        // Correctly instantiate MockConnection with MockNode and MetricsCollectorWrapper
        ConnectionFactory connectionFactory = new ConnectionFactory(); // Needed for MetricsCollectorWrapper
        MetricsCollectorWrapper metricsCollector = MetricsCollectorWrapper.Builder.build(connectionFactory);
        mockConnection = new MockConnection(mockNode, metricsCollector);

        // Mock Channel creation
        mockChannel = new MockChannel(1, mockNode, mockConnection, metricsCollector);

        // Comsumer interface
        mockConsumer = mock(Consumer.class);
        doNothing().when(mockConsumer).handleConsumeOk(anyString());

        // deliveryTagSupplier cannot be null error
        deliveryTagSupplier = () -> 1L;
    }

    // Test basicReject
    @Test
    void testBasicReject() {
        // Declare queue
        Map<String, Object> arguments = new HashMap<>();
        mockNode.queueDeclare(
            "test-basic-reject",
            false,
            false,
            false,
            arguments
        );

        mockNode.exchangeDeclare(
            "test-basic-reject-exchange",
            "direct",
            false,
            false,
            false,
            arguments
        );

        // Bind queue to exchange
        mockNode.queueBind(
            "test-basic-reject",
            "test-basic-reject-exchange",
            "test-routing-key",
            arguments
        );

        // Use basicPublish
        byte[] body = "Dummy ahh test message".getBytes();
        AMQP.BasicProperties props = new AMQP.BasicProperties();
        mockNode.basicPublish(
            "test-basic-reject-exchange",
            "test-routing-key",
            false,
            false,
            props,
            body
        );
        
        // Assert message was added to queue
        assertEquals(1, mockNode.messageCount("test-basic-reject"));
        
        // Get response from MockNode function
        GetResponse response = mockNode.basicGet("test-basic-reject", true, deliveryTagSupplier);

        // Reject the message with requeue set to false so it doesn't requeue
        mockNode.basicReject(response.getEnvelope().getDeliveryTag(), false);
        
        // Assert message was removed from queue
        assertEquals(0, mockNode.messageCount("test-basic-reject"));
    }

    @Test
    void testBasicRejectMultipleMessages() {
        Map<String, Object> arguments = new HashMap<>();
        String queueName = "test-reject-multiple";
        
        mockNode.queueDeclare(queueName, false, false, false, arguments);
        mockNode.exchangeDeclare("multiple-exchange", "direct", false, false, false, arguments);
        mockNode.queueBind(queueName, "multiple-exchange", "multiple-key", arguments);
        
        // Publish multiple messages
        int messageCount = 5;
        AMQP.BasicProperties props = new AMQP.BasicProperties();
        
        for (int i = 0; i < messageCount; i++) {
            byte[] body = ("Message " + i).getBytes();
            mockNode.basicPublish("multiple-exchange", "multiple-key", false, false, props, body);
        }
        
        // Assert all messages were published before rejecting
        assertEquals(messageCount, mockNode.messageCount(queueName));
        
        // Create a list to store delivery tags
        List<Long> deliveryTags = new ArrayList<>();
        
        for (int i = 0; i < messageCount; i++) {
            // Use a different delivery tag for each message
            final int index = i;
            Supplier<Long> customTagSupplier = () -> (long)(index + 1);
            
            GetResponse response = mockNode.basicGet(queueName, false, customTagSupplier);
            assertNotNull(response);
            assertEquals("Message " + i, new String(response.getBody()));
            
            // Store the delivery tag
            deliveryTags.add(response.getEnvelope().getDeliveryTag());
        }
        
        // Reject each message
        for (Long tag : deliveryTags) {
            mockNode.basicReject(tag, false);
        }
        
        // Assert all messages were removed
        assertEquals(0, mockNode.messageCount(queueName));
    }
}
