/*
 * Copyright 2015 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.benchmarks.latency;

import org.openjdk.jmh.annotations.*;
import uk.co.real_logic.agrona.concurrent.*;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@State(Scope.Benchmark)
public class LocklessManyToManyArrayQueueBenchmark
{
    @State(Scope.Benchmark)
    public static class SharedState
    {
        @Param({"1", "2", "10", "50", "100"})
        int numMessages;
        Integer[] values;

        final AtomicBoolean running = new AtomicBoolean(true);
        final AtomicInteger threadId = new AtomicInteger();
        final Queue<Integer> sendQueue = new ManyToManyConcurrentArrayQueue<>(Configuration.QUEUE_CAPACITY);

        @SuppressWarnings("unchecked")
        final Queue<Integer>[] responseQueues = new OneToOneConcurrentArrayQueue[Configuration.MAX_THREAD_COUNT];

        Thread receiverThread;

        @Setup
        public synchronized void setup()
        {
            for (int i = 0; i < Configuration.MAX_THREAD_COUNT; i++)
            {
                responseQueues[i] = new OneToOneConcurrentArrayQueue<>(Configuration.QUEUE_CAPACITY);
            }

            values = new Integer[numMessages];
            for (int i = 0; i < numMessages; i++)
            {
                values[i] = -(numMessages - i);
            }

            receiverThread = new Thread(
                () ->
                {
                    while (true)
                    {
                        final Integer value = sendQueue.poll();
                        if (null == value)
                        {
                            if (!running.get())
                            {
                                break;
                            }
                        }
                        else
                        {
                            final int intValue = value;
                            if (intValue >= 0)
                            {
                                responseQueues[intValue].offer(value);
                            }
                        }
                    }
                }
            );

            receiverThread.setName("receiver");
            receiverThread.start();
        }

        @TearDown
        public synchronized void tearDown() throws Exception
        {
            running.set(false);
            receiverThread.join();
        }
    }

    @State(Scope.Thread)
    public static class PerThreadState
    {
        int id;
        Integer[] values;
        Queue<Integer> sendQueue;
        Queue<Integer> responseQueue;

        @Setup
        public void setup(final SharedState sharedState)
        {
            id = sharedState.threadId.getAndIncrement();
            values = Arrays.copyOf(sharedState.values, sharedState.values.length);
            values[values.length - 1] = id;

            sendQueue = sharedState.sendQueue;
            responseQueue = sharedState.responseQueues[id];
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @Threads(1)
    public Integer test1Producer(final PerThreadState state)
    {
        for (final Integer value : state.values)
        {
            while (!state.sendQueue.offer(value))
            {
                // busy spin
            }
        }

        Integer value;
        do
        {
            value = state.responseQueue.poll();
        }
        while (null == value);

        return value;
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @Threads(2)
    public Integer test2Producers(final PerThreadState state)
    {
        for (final Integer value : state.values)
        {
            while (!state.sendQueue.offer(value))
            {
                // busy spin
            }
        }

        Integer value;
        do
        {
            value = state.responseQueue.poll();
        }
        while (null == value);

        return value;
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @Threads(3)
    public Integer test3Producers(final PerThreadState state)
    {
        for (final Integer value : state.values)
        {
            while (!state.sendQueue.offer(value))
            {
                // busy spin
            }
        }

        Integer value;
        do
        {
            value = state.responseQueue.poll();
        }
        while (null == value);

        return value;
    }
}