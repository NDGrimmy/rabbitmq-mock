package com.Grimmer_MockNode_Tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

public class basicCancelTest {
    
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

    // Test cancel method
    @Test
    void testBasicCancel() {
        // Declare Queue
        Map<String, Object> arguments = new HashMap<>();
        mockNode.queueDeclare(
            "test-basic-cancel",
            false,
            false,
            false,
            arguments
        );

        // Call basicConsume
        String consumerTag = mockNode.basicConsume(
            "test-basic-cancel", 
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
        
        // Assert it worked
        assertEquals(1, mockNode.consumerCount("test-basic-cancel"));

        // Cancel the consumer
        mockNode.basicCancel(consumerTag);
        
        // Assert consumer was cancelled
        assertEquals(0, mockNode.consumerCount("test-basic-cancel"));
    }

    // Test cancelling a consumer with a generated consumer tag
    @Test
    void testBasicCancelGeneratedTag() {
        // Declare Queue
        Map<String, Object> arguments = new HashMap<>();
        mockNode.queueDeclare(
            "test-cancel-generated",
            false,
            false,
            false,
            arguments
        );

        // Call basicConsume with empty consumer tag (will be generated)
        String consumerTag = mockNode.basicConsume(
            "test-cancel-generated", 
            true, 
            "",
            false, 
            false, 
            Map.of(), 
            mockConsumer, 
            deliveryTagSupplier, 
            mockConnection, 
            mockChannel
        );
        
        // Verify tag was generated
        assertNotEquals("", consumerTag);
        assertEquals(1, mockNode.consumerCount("test-cancel-generated"));
        
        // Cancel the consumer
        mockNode.basicCancel(consumerTag);
        
        // Assert consumer was cancelled
        assertEquals(0, mockNode.consumerCount("test-cancel-generated"));
    }
    
    // Test cancelling a non-existent consumer tag
    @Test
    void testBasicCancelNonExistentTag() {
        // Declare Queue
        Map<String, Object> arguments = new HashMap<>();
        mockNode.queueDeclare(
            "test-cancel-nonexistent",
            false,
            false,
            false,
            arguments
        );

        // Call basicConsume
        String consumerTag = mockNode.basicConsume(
            "test-cancel-nonexistent", 
            true, 
            "real-consumer", 
            false, 
            false, 
            Map.of(), 
            mockConsumer, 
            deliveryTagSupplier, 
            mockConnection, 
            mockChannel
        );
        
        // Assert consumer was added
        assertEquals(1, mockNode.consumerCount("test-cancel-nonexistent"));
        
        // Cancel with a non-existent tag
        mockNode.basicCancel("consumer-ahh-tag");
        
        // Assert real consumer is still there
        assertEquals(1, mockNode.consumerCount("test-cancel-nonexistent"));
    }
    
    // Test cancelling multiple consumers
    @Test
    void testBasicCancelMultipleConsumers() {
        // Declare Queue
        Map<String, Object> arguments = new HashMap<>();
        mockNode.queueDeclare(
            "test-cancel-multiple",
            false,
            false,
            false,
            arguments
        );

        // Create additional mock consumers
        Consumer mockConsumer2 = mock(Consumer.class);
        Consumer mockConsumer3 = mock(Consumer.class);
        doNothing().when(mockConsumer2).handleConsumeOk(anyString());
        doNothing().when(mockConsumer3).handleConsumeOk(anyString());
        
        // Call basicConsume
        String tag1 = mockNode.basicConsume(
            "test-cancel-multiple", 
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
            "test-cancel-multiple", 
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
            "test-cancel-multiple", 
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
        
        // Assert all consumers were added
        assertEquals(3, mockNode.consumerCount("test-cancel-multiple"));
        
        // Cancel one consumer
        mockNode.basicCancel(tag2);
        
        // Assert only one was removed
        assertEquals(2, mockNode.consumerCount("test-cancel-multiple"));
    }
}
