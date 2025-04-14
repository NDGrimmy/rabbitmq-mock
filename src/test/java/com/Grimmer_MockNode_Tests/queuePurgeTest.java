package com.Grimmer_MockNode_Tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

public class queuePurgeTest {
    
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

    // Integration Test for purging the queue (MockNode -> MockQueue)
    @Test
    void testqueuePurge() {
        Map<String, Object> arguments = new HashMap<>();
        mockNode.queueDeclare(
            "publish-test-purge",
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
            "publish-test-purge",
            "publish-test-exchange",
            "test-routing-key",
            arguments
        );

        // Publish a message
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

        mockNode.queuePurge(
            "publish-test-purge"
        );

        // Verify message count < 1
        assertTrue(mockNode.messageCount("publish-test-purge") < 1);
    }

    @Test
    void testPurgeQueueWithMultipleMessages() {
        Map<String, Object> arguments = new HashMap<>();
        mockNode.queueDeclare(
            "queue",
            false,
            false,
            false,
            arguments
        );
    
        mockNode.exchangeDeclare(
            "exchange",
            "direct",
            false,
            false,
            false,
            arguments
        );

        mockNode.queueBind(
            "queue",
            "exchange",
            "key",
            arguments
        );
        
        AMQP.BasicProperties props = new AMQP.BasicProperties();
        for (int i = 0; i < 5; i++) {
            mockNode.basicPublish("exchange", "key", false, false, props, ("msg" + i).getBytes());
        }
    
        assertEquals(5, mockNode.messageCount("queue"));
    
        mockNode.queuePurge("queue");
        
        assertEquals(0, mockNode.messageCount("queue"));
    }
    
    @Test
    void testPurgeNonexistentQueue() {
        assertThrows(IllegalArgumentException.class, () -> mockNode.queuePurge("queue"));
    }
}
