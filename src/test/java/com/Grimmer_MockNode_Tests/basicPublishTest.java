package com.Grimmer_MockNode_Tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
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

public class basicPublishTest {
    
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

    // Testing publishing message
    @Test
    void testbasicPublish() {
        Map<String, Object> arguments = new HashMap<>();
        mockNode.queueDeclare(
            "publish-test-queue",
            false,
            false,
            false,
            arguments
        );

        mockNode.exchangeDeclare(
            "publish-test-exchange",
            "topic",
            true,
            false,
            false,
            arguments
        );

        // Bind the queue to the exchange with a routing key
        mockNode.queueBind(
            "publish-test-queue",
            "publish-test-exchange",
            "test-routing-key",
            arguments
        );

        // Publish message
        byte[] body = "This is a test message".getBytes();
        AMQP.BasicProperties props = new AMQP.BasicProperties();
        mockNode.basicPublish(
            "publish-test-exchange",
            "test-routing-key",
            false,
            false,
            props,
            body
        );

        // Assert message count
        assertTrue(mockNode.messageCount("publish-test-queue") > 0);
    }

    // Test for multiple queues on one exchange
    @Test
    void testPublishToDirectExchangeWithMultipleBindings() {
        Map<String, Object> arguments = new HashMap<>();
        
        // Create two queues
        mockNode.queueDeclare("direct-queue-1", false, false, false, arguments);
        mockNode.queueDeclare("direct-queue-2", false, false, false, arguments);
        
        // Create direct exchange
        mockNode.exchangeDeclare("direct-exchange", "direct", false, false, false, arguments);
        
        // Bind both queues to the same exchange with different routing keys
        mockNode.queueBind("direct-queue-1", "direct-exchange", "key1", arguments);
        mockNode.queueBind("direct-queue-2", "direct-exchange", "key2", arguments);
        
        // Publish message to first routing key
        byte[] body1 = "Message for queue 1".getBytes();
        AMQP.BasicProperties props = new AMQP.BasicProperties();
        mockNode.basicPublish("direct-exchange", "key1", false, false, props, body1);
        
        // Publish message to second routing key
        byte[] body2 = "Message for queue 2".getBytes();
        mockNode.basicPublish("direct-exchange", "key2", false, false, props, body2);
        
        // Assert queue 1 received the first message
        assertEquals(1, mockNode.messageCount("direct-queue-1"));
        GetResponse response1 = mockNode.basicGet("direct-queue-1", true, deliveryTagSupplier);
        assertNotNull(response1);
        assertEquals("Message for queue 1", new String(response1.getBody()));
        
        // Assert queue 2 received the second message
        assertEquals(1, mockNode.messageCount("direct-queue-2"));
        GetResponse response2 = mockNode.basicGet("direct-queue-2", true, deliveryTagSupplier);
        assertNotNull(response2);
        assertEquals("Message for queue 2", new String(response2.getBody()));
    }

    // Test for non existent exchange
    @Test
    void testPublishToNonExistentExchange() {
        byte[] body = "Message to nowhere".getBytes();
        AMQP.BasicProperties props = new AMQP.BasicProperties();
        
        // This should throw an exception
        assertThrows(IllegalArgumentException.class, () -> {
            mockNode.basicPublish("non-existent-exchange", "any-key", false, false, props, body);
        }, "Publishing to non-existent exchange should throw IllegalArgumentException");
    }

    // Test for publishing 100 messages
    @Test
    void testPublishMultipleMessages() {
        Map<String, Object> arguments = new HashMap<>();
        mockNode.queueDeclare("multi-msg-queue", false, false, false, arguments);
        mockNode.exchangeDeclare("multi-msg-exchange", "direct", false, false, false, arguments);
        mockNode.queueBind("multi-msg-queue", "multi-msg-exchange", "multi-msg-key", arguments);
        
        // Publish 100 messages
        AMQP.BasicProperties props = new AMQP.BasicProperties();
        int messageCount = 100;
        
        for (int i = 0; i < messageCount; i++) {
            byte[] body = ("Message " + i).getBytes();
            mockNode.basicPublish("multi-msg-exchange", "multi-msg-key", false, false, props, body);
        }
        
        // Assert all messages were published
        assertEquals(messageCount, mockNode.messageCount("multi-msg-queue"));
    }

    
}
