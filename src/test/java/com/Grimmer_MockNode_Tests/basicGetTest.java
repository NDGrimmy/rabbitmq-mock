package com.Grimmer_MockNode_Tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

public class basicGetTest {
    
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

    // Test basicGet
    @Test
    void testBasicGet() {
        // Setup
        Map<String, Object> arguments = new HashMap<>();
        mockNode.queueDeclare(
            "test-basic-get",
            false,
            false,
            false,
            arguments
        );

        mockNode.exchangeDeclare(
            "test-basic-get-exchange",
            "direct",
            false,
            false,
            false,
            arguments
        );

        mockNode.queueBind(
            "test-basic-get",
            "test-basic-get-exchange",
            "test-routing-key",
            arguments
        );

        // Publish a message
        byte[] body = "Dumb ahh test message".getBytes();
        AMQP.BasicProperties props = new AMQP.BasicProperties();
        mockNode.basicPublish(
            "test-basic-get-exchange",
            "test-routing-key",
            false,
            false,
            props,
            body
        );

        // Get response from MockQueue class function call
        GetResponse response = mockNode.basicGet("test-basic-get", false, deliveryTagSupplier);

        // Assert message was retrieved
        assertEquals("Dumb ahh test message", new String(response.getBody())); // Convert btyes to string with message
    }

    @Test
    void testBasicGetFromEmptyQueue() {
        // Declare queue but don't publish any messages
        Map<String, Object> arguments = new HashMap<>();
        mockNode.queueDeclare(
            "empty-queue",
            false,
            false,
            false,
            arguments
        );
        
        // Try to get a message from the empty queue
        GetResponse response = mockNode.basicGet("empty-queue", false, deliveryTagSupplier);
        
        // Assert null response for empty queue
        assertNull(response, "Response should be null for empty queue");
    }

    @Test
    void testBasicGetFromNonExistentQueue() {
        // Try to get from a queue that doesn't exist
        assertThrows(IllegalArgumentException.class, () -> {
            mockNode.basicGet("non-existent-queue", false, deliveryTagSupplier);
        }, "Should throw IllegalArgumentException when queue doesn't exist");
    }

    @Test
    void testBasicGetWithMultipleMessages() {
        // Setup
        Map<String, Object> arguments = new HashMap<>();
        String queueName = "multi-msg-queue";
        mockNode.queueDeclare(queueName, false, false, false, arguments);
        
        mockNode.exchangeDeclare("multi-exchange", "direct", false, false, false, arguments);
        mockNode.queueBind(queueName, "multi-exchange", "multi-key", arguments);
        
        // Publish multiple messages
        for (int i = 0; i < 5; i++) {
            byte[] body = ("Message " + i).getBytes();
            AMQP.BasicProperties props = new AMQP.BasicProperties();
            mockNode.basicPublish("multi-exchange", "multi-key", false, false, props, body);
        }
        
        // Get messages one by one
        for (int i = 0; i < 5; i++) {
            GetResponse response = mockNode.basicGet(queueName, true, deliveryTagSupplier);
            assertEquals("Message " + i, new String(response.getBody()));
        }
        
        // Assert queue is now empty
        GetResponse emptyResponse = mockNode.basicGet(queueName, true, deliveryTagSupplier);
        assertNull(emptyResponse, "Queue should be empty after retrieving all messages");
    }
}
