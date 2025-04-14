package com.Grimmer_MockNode_Tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

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
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;

public class basicConsumeTest {
    
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

    // Test that a consumerTag is being generated. This is an example of boundary value testing
    @Test
    void testBasicConsume_RegistersConsumer() {

        // Create a MockQueue with valid constructor arguments
        mockNode.queueDeclare(
            "testQueue", 
            false, 
            false, 
            false, 
            null
        );

        // Call basicConsume
        String consumerTag = mockNode.basicConsume(
            "testQueue", 
            true, 
            "consumer-test", 
            false, 
            false, 
            Map.of(), 
            mockConsumer, 
            deliveryTagSupplier, 
            mockConnection, 
            mockChannel
        );

        // Assert consumer Tag is kept
        assertTrue(consumerTag == "consumer-test");
        // Assert Consumer added to queue
        assertEquals(1, mockNode.consumerCount("testQueue"));
    }


    @Test
    void testBasicConsume_RegistersNullConsumer() {

        // Create a MockQueue with valid constructor arguments
        mockNode.queueDeclare("testQueue", 
            false, 
            false, 
            false, 
            null
        );

        // Call basicConsume
        String consumerTag = mockNode.basicConsume("testQueue", true, "", false, false, 
            Map.of(), mockConsumer, deliveryTagSupplier, mockConnection, mockChannel);

        // Assert consumer Tag is generated
        assertTrue(consumerTag != "");
        // Assert Consumer added to queue
        assertEquals(1, mockNode.consumerCount("testQueue"));
    }

    // Test registering multiple consumers on the same queue
    @Test
    void testBasicConsume_MultipleConsumers() {
        // Create a MockQueue with valid constructor arguments
        mockNode.queueDeclare(
            "multi-consumer-queue", 
            false, 
            false, 
            false, 
            null
        );

        // Create additional mock consumers
        Consumer mockConsumer2 = mock(Consumer.class);
        Consumer mockConsumer3 = mock(Consumer.class);
        doNothing().when(mockConsumer2).handleConsumeOk(anyString());
        doNothing().when(mockConsumer3).handleConsumeOk(anyString());

        // Register multiple consumers
        String tag1 = mockNode.basicConsume(
            "multi-consumer-queue", 
            true, 
            "consumer1", 
            false, 
            false, 
            Map.of(), 
            mockConsumer, 
            deliveryTagSupplier, 
            mockConnection, 
            mockChannel
        );

        String tag2 = mockNode.basicConsume(
            "multi-consumer-queue", 
            true, 
            "consumer2", 
            false, 
            false, 
            Map.of(), 
            mockConsumer2, 
            deliveryTagSupplier, 
            mockConnection, 
            mockChannel
        );

        String tag3 = mockNode.basicConsume(
            "multi-consumer-queue", 
            true, 
            "consumer3", 
            false, 
            false, 
            Map.of(), 
            mockConsumer3, 
            deliveryTagSupplier, 
            mockConnection, 
            mockChannel
        );

        // Assert all consumers were registered with correct tags
        assertEquals("consumer1", tag1);
        assertEquals("consumer2", tag2);
        assertEquals("consumer3", tag3);
        
        // Assert all consumers were added to the queue
        assertEquals(3, mockNode.consumerCount("multi-consumer-queue"));
    }

    // Test consumer registration with non-existent queue
    @Test
    void testBasicConsume_NonExistentQueue() {
        // Attempt to consume from non-existent queue (should throw exception)
        assertThrows(
            IllegalArgumentException.class,
            () -> mockNode.basicConsume(
                "non-existent-queue", 
                true, 
                "consumer-test", 
                false, 
                false, 
                Map.of(), 
                mockConsumer, 
                deliveryTagSupplier, 
                mockConnection, 
                mockChannel
            )
        );
    }

    // Test consumer registration with null consumer
    @Test
    void testBasicConsume_NullConsumer() {
        // Create a queue
        mockNode.queueDeclare(
            "null-consumer-queue", 
            false, 
            false, 
            false, 
            null
        );

        // Attempt to register null consumer
        assertThrows(
            NullPointerException.class,
            () -> mockNode.basicConsume(
                "null-consumer-queue", 
                true, 
                "consumer-test", 
                false, 
                false, 
                Map.of(), 
                null, // Null consumer
                deliveryTagSupplier, 
                mockConnection, 
                mockChannel
            )
        );
    }

    // Test consumer registration across multiple queues
    @Test
    void testBasicConsume_MultipleQueues() {
        // Create multiple queues
        mockNode.queueDeclare("queue1", false, false, false, null);
        mockNode.queueDeclare("queue2", false, false, false, null);
        mockNode.queueDeclare("queue3", false, false, false, null);

        // Register consumers on each queue
        String tag1 = mockNode.basicConsume(
            "queue1", true, "consumer-q1", false, false, 
            Map.of(), mockConsumer, deliveryTagSupplier, mockConnection, mockChannel
        );
        
        String tag2 = mockNode.basicConsume(
            "queue2", true, "consumer-q2", false, false, 
            Map.of(), mockConsumer, deliveryTagSupplier, mockConnection, mockChannel
        );
        
        String tag3 = mockNode.basicConsume(
            "queue3", true, "consumer-q3", false, false, 
            Map.of(), mockConsumer, deliveryTagSupplier, mockConnection, mockChannel
        );

        // Assert consumers were registered on each queue
        assertEquals(1, mockNode.consumerCount("queue1"));
        assertEquals(1, mockNode.consumerCount("queue2"));
        assertEquals(1, mockNode.consumerCount("queue3"));
    }
}
