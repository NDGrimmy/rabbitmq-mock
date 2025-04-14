package com.Grimmer_MockNode_Tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;

public class closeTest {
    
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

    // Test for close method
    @Test
    void testClose() {
        // Create Queue
        Map<String, Object> arguments = new HashMap<>();
        mockNode.queueDeclare(
            "test-close-queue",
            false,
            false,
            false,
            arguments
        );

        // Call basicConsume
        String consumerTag = mockNode.basicConsume(
            "test-close-queue", 
            true, 
            "test-consumer", 
            false, 
            false, 
            Map.of(), 
            mockConsumer, 
            deliveryTagSupplier, 
            mockConnection, 
            mockChannel
        );

        // Assert consumer Tag is generated
        assertTrue(consumerTag != "");
        
        // Close connection
        mockNode.close(mockConnection);
        
        // Assert that consumerCount is 0 after close
        assertEquals(0, mockNode.consumerCount("test-close-queue"));
    }

    // There isnt any way to create test cases here that would change anything
}
