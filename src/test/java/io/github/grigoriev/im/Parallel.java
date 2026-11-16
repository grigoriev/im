package io.github.grigoriev.im;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Runs calls at the same time, for the tests of a row lock. */
public final class Parallel {

    private Parallel() {
    }

    /**
     * Runs the calls on a thread each, all at once.
     *
     * @param calls what runs
     * @return the result of each call in their order, or the exception it threw
     * @throws InterruptedException if the test is interrupted
     */
    public static List<Object> run(List<? extends Callable<?>> calls) throws InterruptedException {
        List<Object> outcomes = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(calls.size())) {
            for (Future<?> future : pool.invokeAll(calls.stream().<Callable<Object>>map(call -> call::call).toList())) {
                try {
                    outcomes.add(future.get());
                } catch (ExecutionException e) {
                    outcomes.add(e.getCause());
                }
            }
        }
        return outcomes;
    }

    /**
     * Runs one call on several threads at once.
     *
     * @param times how many threads
     * @param call what each runs
     * @return the result of each run, or the exception it threw
     * @throws InterruptedException if the test is interrupted
     */
    public static List<Object> run(int times, Callable<?> call) throws InterruptedException {
        return run(Collections.nCopies(times, call));
    }
}
