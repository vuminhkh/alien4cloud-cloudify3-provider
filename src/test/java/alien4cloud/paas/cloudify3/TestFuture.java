package alien4cloud.paas.cloudify3;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

public class TestFuture {

    @Test
    public void testFuture() throws Exception {
        final ListeningScheduledExecutorService executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(4));
        ListenableFuture<Integer> future1 = execute(executor, 1);
        AsyncFunction<Integer, Integer> future1to2 = new AsyncFunction<Integer, Integer>() {
            @Override
            public ListenableFuture<Integer> apply(Integer input1) throws Exception {
                return execute(executor, input1 + 1);
            }
        };
        ListenableFuture<Integer> future2 = Futures.transform(future1, future1to2);
        AsyncFunction<Integer, Integer> future2to3 = new AsyncFunction<Integer, Integer>() {
            @Override
            public ListenableFuture<Integer> apply(final Integer input2) throws Exception {
                return Futures.dereference(executor.schedule(new Callable<ListenableFuture<? extends Integer>>() {
                    @Override
                    public ListenableFuture<? extends Integer> call() throws Exception {
                        return execute(executor, input2 + 1);
                    }
                }, 1, TimeUnit.SECONDS));
            }
        };
        ListenableFuture<Integer> future3 = Futures.transform(future2, future2to3);
        System.out.println("Result " + future3.get());
    }

    private ListenableFuture<Integer> execute(ListeningExecutorService executor, final Integer toReturn) {
        if (toReturn == 3) {
            throw new RuntimeException("fuck");
        }
        return executor.submit(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return toReturn;
            }
        });
    }
}
