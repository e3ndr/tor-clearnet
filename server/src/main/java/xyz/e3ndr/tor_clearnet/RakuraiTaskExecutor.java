package xyz.e3ndr.tor_clearnet;

import java.lang.Thread.Builder.OfVirtual;

import co.casterlabs.rhs.util.TaskExecutor;

public class RakuraiTaskExecutor implements TaskExecutor {
    private static final OfVirtual THREAD_FACTORY = Thread.ofVirtual().name("Flux - HTTP - Task Pool - #", 0);

    public static final TaskExecutor INSTANCE = new RakuraiTaskExecutor();

    @Override
    public Task execute(Runnable toRun) {
        return new Task() {
            private final Thread thread = THREAD_FACTORY.start(toRun);

            @Override
            public void interrupt() {
                this.thread.interrupt();
            }

            @Override
            public void waitFor() throws InterruptedException {
                this.thread.join();
            }

            @Override
            public boolean isAlive() {
                return this.thread.isAlive();
            }
        };
    }

}
