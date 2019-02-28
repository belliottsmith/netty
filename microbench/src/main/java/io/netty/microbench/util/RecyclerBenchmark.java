/*
 * Copyright 2018 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.microbench.util;

import io.netty.util.Recycler;
import io.netty.util.internal.ThreadLocalRandom;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 1)
@Measurement(iterations = 1, time = 1, timeUnit = TimeUnit.MINUTES)
public class RecyclerBenchmark extends AbstractMicrobenchmark {
    private Recycler<DummyObject> recycler = new Recycler<DummyObject>() {
        @Override
        protected DummyObject newObject(Recycler.Handle<DummyObject> handle) {
            return new DummyObject(handle);
        }
    };

    @Override
    protected ChainedOptionsBuilder newOptionsBuilder() throws Exception {
        return super.newOptionsBuilder()
            .addProfiler("gc");
    }

    @Benchmark
    public DummyObject plainNew() {
        return new DummyObject();
    }

    @Benchmark
    public DummyObject recyclerGetAndOrphan() {
        return recycler.get();
    }

    @Benchmark
    public DummyObject recyclerGetAndRecycle() {
        DummyObject o = recycler.get();
        o.recycle();
        return o;
    }

    @State(Scope.Thread)
    public static class InUse {
        static final int MAX_CAPACITY = 1024;
        int targetCapacity;
        final List<DummyObject> list = new ArrayList<DummyObject>(MAX_CAPACITY);
    }

    @Benchmark
    public void recyclerGetAndRecycleX(InUse inUse) {
        if (inUse.list.size() == inUse.targetCapacity) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            inUse.targetCapacity = random.nextInt(1, InUse.MAX_CAPACITY);
        }
        if (inUse.list.size() < inUse.targetCapacity) {
            inUse.list.add(recycler.get());
        } else {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int recycle = random.nextInt(0, inUse.list.size());
            inUse.list.get(recycle).recycle();
            inUse.list.set(recycle, inUse.list.get(inUse.list.size() - 1));
            inUse.list.remove(inUse.list.size() - 1);
            if (random.nextInt(0, 100) == 0) {
                System.gc();
            }
        }
    }

    @State(Scope.Benchmark)
    public static class ProducerConsumerState {
        final ArrayBlockingQueue<DummyObject> queue = new ArrayBlockingQueue(100);
    }

    // The allocation stats are the main thing interesting about this benchmark
    @Benchmark
    @Group("producerConsumer")
    public void producer(ProducerConsumerState state) throws Exception {
        state.queue.put(recycler.get());
    }

    @Benchmark
    @Group("producerConsumer")
    public void consumer(ProducerConsumerState state) throws Exception {
        state.queue.take().recycle();
    }

    private static final class DummyObject {
        private final Recycler.Handle<DummyObject> handle;
        private long l1;
        private long l2;
        private long l3;
        private long l4;
        private long l5;
        private Object o1;
        private Object o2;
        private Object o3;
        private Object o4;
        private Object o5;

        public DummyObject() {
            this(null);
        }

        public DummyObject(Recycler.Handle<DummyObject> handle) {
            this.handle = handle;
        }

        public void recycle() {
            handle.recycle(this);
        }
    }
}
