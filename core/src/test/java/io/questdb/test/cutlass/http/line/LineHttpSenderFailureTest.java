package io.questdb.test.cutlass.http.line;

import io.questdb.client.Sender;
import io.questdb.griffin.SqlException;
import io.questdb.std.Misc;
import io.questdb.std.Os;
import io.questdb.std.str.Path;
import io.questdb.test.AbstractBootstrapTest;
import io.questdb.test.TestServerMain;
import io.questdb.test.tools.TestUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.Closeable;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class LineHttpSenderFailureTest extends AbstractBootstrapTest {

    public static void scenarioRetryWithDeduplication(ServerController controller) throws Exception {
        String tableName = UUID.randomUUID().toString();
        TestUtils.assertMemoryLeak(() -> {
            try {
                controller.startAndExecute("create table '" + tableName + "' (value long, ts timestamp) timestamp (ts) partition by DAY WAL DEDUP UPSERT KEYS(ts)");
                CountDownLatch senderLatch = new CountDownLatch(2); // one for Sender and one for Restarter

                AtomicReference<Exception> senderException = new AtomicReference<>();
                new Thread(() -> {
                    try (Sender sender = controller.newSender()) {
                        for (int i = 0; i < 1_000_000; i++) {
                            sender.table(tableName).longColumn("value", 42).at(i * 10, ChronoUnit.MICROS);
                        }
                    } catch (Exception t) {
                        senderException.set(t);
                    } finally {
                        senderLatch.countDown();
                    }
                }).start();

                new Thread(() -> {
                    // keeping restarting the server until Sender is done
                    while (senderLatch.getCount() == 2) {
                        Os.sleep(500);
                        controller.restart();
                    }
                    controller.stop(); // stop will clear the thread local Path
                    senderLatch.countDown();
                }).start();

                senderLatch.await();

                if (senderException.get() != null) {
                    Assert.fail("Sender failed: " + senderException.get().getMessage());
                }
                controller.start();
                controller.assertSqlEventually("select count() from '" + tableName + "'", "count\n1000000\n");
            } finally {
                Misc.free(controller);
            }
        });
    }

    @Test
    public void scenarioRetryWithDeduplication() throws Exception {
        scenarioRetryWithDeduplication(new ServerController());
    }

    @Before
    public void setUp() {
        super.setUp();
        TestUtils.unchecked(() -> createDummyConfiguration());
        dbPath.parent().$();
    }

    public static class ServerController implements Closeable {
        private TestServerMain serverMain;

        public void assertSqlEventually(String sql, String expected) {
            TestUtils.assertEventually(() -> serverMain.assertSql(sql, expected));
        }

        @Override
        public void close() {
            serverMain = Misc.free(serverMain);
        }

        public Sender newSender() {
            String url = "http://localhost:" + HTTP_PORT;
            return Sender.builder().url(url).maxPendingRows(100).retryTimeoutMillis(1000).build();
        }

        public void restart() {
            stop();
            start();
        }

        public void start() {
            serverMain = startWithEnvVariables();
            serverMain.start();
        }

        public void startAndExecute(String sqlText) throws SqlException {
            start();
            serverMain.compile(sqlText);
        }

        public void stop() {
            serverMain = Misc.free(serverMain);
            Path.clearThreadLocals();
        }
    }
}
