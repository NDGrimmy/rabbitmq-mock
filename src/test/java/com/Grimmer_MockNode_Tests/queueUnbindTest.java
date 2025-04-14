package com.Grimmer_MockNode_Tests;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

public class queueUnbindTest {
    
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

    // Testing for queue unbind
    @Test
    void testQueueUnBind() {
        Map<String, Object> arguments = new HashMap<>();
        mockNode.queueDeclare(
            "test-queue",
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
            "test-queue",
            "publish-test-exchange",
            "test-routing-key",
            arguments
        );

        mockNode.queueUnbind(
            "test-queue",
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

        // Verify message count
        assertFalse(mockNode.messageCount("test-queue") > 0);
    }

    // Test without binding
    @Test
    void testQueueUnbindWithoutBind() {
        Map<String, Object> arguments = new HashMap<>();
        mockNode.queueDeclare("queue", false, false, false, arguments);
        mockNode.exchangeDeclare("exchange", "topic", false, false, false, arguments);

        // Assert unbind without binding doesnt error
        assertDoesNotThrow(() -> mockNode.queueUnbind(
            "queue",
            "exchange",
            "key?",
            arguments
        ));
    }

    // Test multiple bindings 
    @Test
    void testUnbindForMultipleRoutingKeys() {
        Map<String, Object> arguments = new HashMap<>();
        mockNode.queueDeclare("queue", false, false, false, arguments);
        mockNode.exchangeDeclare("exchange", "topic", false, false, false, arguments);

        mockNode.queueBind("queue", "exchange", "key1", arguments);
        mockNode.queueBind("queue", "exchange", "key2", arguments);

        // Unbind only one key
        mockNode.queueUnbind("queue", "exchange", "key1", arguments);

        AMQP.BasicProperties props = new AMQP.BasicProperties();
        mockNode.basicPublish("exchange", "key1", false, false, props, "msg".getBytes());
        mockNode.basicPublish("exchange", "key2", false, false, props, "msg".getBytes());

        assertEquals(1, mockNode.messageCount("queue"));
    }
}
