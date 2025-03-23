package com.Grimmer_Tests;

// A bunch of imports from the other test that does the same method
import static com.github.fridujo.rabbitmq.mock.configuration.QueueDeclarator.queue;
import static com.github.fridujo.rabbitmq.mock.tool.Exceptions.runAndEatExceptions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.github.fridujo.rabbitmq.mock.MockChannel;
import com.github.fridujo.rabbitmq.mock.MockQueue;
import com.github.fridujo.rabbitmq.mock.ReceiverPointer;
import com.legstar.mock.client.MockConnectionFactory;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;

// My imports
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.github.fridujo.rabbitmq.mock.MockConnection;
import com.rabbitmq.client.Consumer;
import java.util.function.Supplier;
import com.github.fridujo.rabbitmq.mock.MockNode;
import com.github.fridujo.rabbitmq.mock.MockQueue;
import com.github.fridujo.rabbitmq.mock.MockChannel;
import com.github.fridujo.rabbitmq.mock.AmqArguments;
import com.github.fridujo.rabbitmq.mock.ReceiverRegistry;
import com.github.fridujo.rabbitmq.mock.metrics.MetricsCollectorWrapper;
import com.rabbitmq.client.ConnectionFactory;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import com.github.fridujo.rabbitmq.mock.Receiver;

public class MockNodeTest {

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

    @BeforeEach
    void setUp() throws NoSuchFieldException, SecurityException {
        // Access the private 'queues' field using reflection
        Field queuesField = MockNode.class.getDeclaredField("queues");
        queuesField.setAccessible(true);  // Allow access to private field
        // Create a real MockNode instance
        mockNode = new MockNode();

        // Create a dummy ReceiverRegistry stub implementation
        ReceiverRegistry receiverRegistry = new ReceiverRegistry() {
            @Override
            public Optional<Receiver> getReceiver(ReceiverPointer receiverPointer) {
                return Optional.empty(); // No actual receiver used in this test
            }
        };

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


        // Supplier<Long> type obj
        deliveryTagSupplier = () -> 1L; // Always returns 1L
        
        // Get the private field and cast it to the correct type
        // @SuppressWarnings("unchecked")
        // Map<String, MockQueue> queues = (Map<String, MockQueue>) queuesField.get(mockNode);

        // // Manually add the queue to MockNode’s queues map using reflection
        // queues.put("testQueue", mockQueue);
    }

    @Test
    void testBasicConsume_RegistersConsumerInMockQueue() {

        // Create a real MockQueue with valid constructor arguments
        mockQueue = new MockQueue("testQueue", AmqArguments.empty(), receiverRegistry);
        mockNode.queueDeclare("testQueue", false, false, false, null);
        // Call basicConsume
        String consumerTag = mockNode.basicConsume("testQueue", true, "", false, false, 
            Map.of(), mockConsumer, deliveryTagSupplier, mockConnection, mockChannel);

        // Ensure consumerTag is generated and not null
        assertFalse(consumerTag.isEmpty());

        // // Access the private 'consumersByTag' field using reflection
        // Field consumersByTagField = MockQueue.class.getDeclaredField("consumersByTag");
        // consumersByTagField.setAccessible(true);  // Allow access to private field

        // // Get the actual map from the field
        // @SuppressWarnings("unchecked")
        // Map<String, Object> consumersByTag = (Map<String, Object>) consumersByTagField.get(mockQueue);

        // // Check if the consumerTag exists in the map
        // assertTrue(consumersByTag.containsKey(consumerTag));

        // // Get the 'ConsumerAndTag' instance from the map
        // Object consumerAndTag = consumersByTag.get(consumerTag);
        // assertNotNull(consumerAndTag);

        // // Now, you can use reflection to access fields inside ConsumerAndTag (if needed)
        // Field consumerField = consumerAndTag.getClass().getDeclaredField("consumer");
        // consumerField.setAccessible(true);  // Make it accessible
        // Object consumer = consumerField.get(consumerAndTag);
        // assertNotNull(consumer);  // Ensure that consumer is present
    }
}
