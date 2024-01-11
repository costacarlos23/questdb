package io.questdb.test.cutlass.line.http;

import io.questdb.client.Sender;
import io.questdb.cutlass.line.LineSenderException;
import io.questdb.cutlass.line.http.LineHttpSender;
import io.questdb.std.Rnd;
import io.questdb.test.AbstractBootstrapTest;
import io.questdb.test.TestServerMain;
import io.questdb.test.tools.TestUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static io.questdb.PropertyKey.DEBUG_FORCE_RECV_FRAGMENTATION_CHUNK_SIZE;
import static io.questdb.PropertyKey.LINE_HTTP_ENABLED;
import static io.questdb.test.cutlass.http.line.IlpHttpUtils.getHttpPort;

public class LineHttpSenderTest extends AbstractBootstrapTest {

    @Before
    public void setUp() {
        super.setUp();
        TestUtils.unchecked(() -> createDummyConfiguration());
        dbPath.parent().$();
    }

    @Test
    public void testAutoFlush() throws Exception {
        Rnd rnd = TestUtils.generateRandom(LOG);
        TestUtils.assertMemoryLeak(() -> {
            int fragmentation = 1 + rnd.nextInt(5);
            LOG.info().$("=== fragmentation=").$(fragmentation).$();
            try (final TestServerMain serverMain = startWithEnvVariables(
                    DEBUG_FORCE_RECV_FRAGMENTATION_CHUNK_SIZE.getEnvVarName(), String.valueOf(fragmentation)
            )) {
                int httpPort = getHttpPort(serverMain);

                int totalCount = 100_000;
                int maxPendingRows = 1000;
                try (LineHttpSender sender = new LineHttpSender("localhost", httpPort, -1, false, Sender.TlsValidationMode.DEFAULT, maxPendingRows, null, null, null)) {
                    for (int i = 0; i < totalCount; i++) {
                        if (i != 0 && i % maxPendingRows == 0) {
                            serverMain.waitWalTxnApplied("table with space");
                            serverMain.assertSql("select count() from 'table with space'", "count\n" +
                                    i + "\n");
                        }
                        sender.table("table with space")
                                .symbol("tag1", "value" + i % 10)
                                .timestampColumn("tcol4", 10, ChronoUnit.HOURS)
                                .atNow();
                    }
                    serverMain.waitWalTxnApplied("table with space");
                    serverMain.assertSql("select count() from 'table with space'", "count\n" +
                            totalCount + "\n");
                }
            }
        });
    }

    @Test
    public void testLineHttpDisabled() throws Exception {
        Rnd rnd = TestUtils.generateRandom(LOG);
        TestUtils.assertMemoryLeak(() -> {
            int fragmentation = 1 + rnd.nextInt(5);
            LOG.info().$("=== fragmentation=").$(fragmentation).$();
            try (final TestServerMain serverMain = startWithEnvVariables(
                    LINE_HTTP_ENABLED.getEnvVarName(), "false"
            )) {
                int httpPort = getHttpPort(serverMain);

                int totalCount = 1_000;
                try (LineHttpSender sender = new LineHttpSender("localhost", httpPort, -1, false, Sender.TlsValidationMode.DEFAULT, 100_000, null, null, null)) {
                    for (int i = 0; i < totalCount; i++) {
                        sender.table("table")
                                .longColumn("lcol1", i)
                                .atNow();
                    }
                    try {
                        sender.flush();
                        Assert.fail("Expected exception");
                    } catch (LineSenderException e) {
                        TestUtils.assertContains(e.getMessage(), "http-status=404");
                        TestUtils.assertContains(e.getMessage(), "Could not flush buffer: HTTP endpoint does not support ILP.");
                    }
                }
            }
        });
    }

    @Test
    public void testSmoke() throws Exception {
        Rnd rnd = TestUtils.generateRandom(LOG);
        TestUtils.assertMemoryLeak(() -> {
            int fragmentation = 1 + rnd.nextInt(5);
            LOG.info().$("=== fragmentation=").$(fragmentation).$();
            try (final TestServerMain serverMain = startWithEnvVariables(
                    DEBUG_FORCE_RECV_FRAGMENTATION_CHUNK_SIZE.getEnvVarName(), String.valueOf(fragmentation)
            )) {
                int httpPort = getHttpPort(serverMain);

                int totalCount = 1_000_000;
                try (LineHttpSender sender = new LineHttpSender("localhost", httpPort, -1, false, Sender.TlsValidationMode.DEFAULT, 100_000, null, null, null)) {
                    for (int i = 0; i < totalCount; i++) {
                        sender.table("table with space")
                                .symbol("tag1", "value" + i % 10)
                                .symbol("tag2", "value " + i % 10)
                                .stringColumn("scol1", "value" + i)
                                .stringColumn("scol2", "value" + i)
                                .longColumn("lcol 1", i)
                                .longColumn("lcol2", i)
                                .doubleColumn("dcol1", i)
                                .doubleColumn("dcol2", i)
                                .boolColumn("bcol1", i % 2 == 0)
                                .boolColumn("bcol2", i % 2 == 0)
                                .timestampColumn("tcol1", Instant.now())
                                .timestampColumn("tcol2", Instant.now())
                                .timestampColumn("tcol3", 1, ChronoUnit.HOURS)
                                .timestampColumn("tcol4", 10, ChronoUnit.HOURS)
                                .atNow();
                    }
                    sender.flush();
                }
                serverMain.waitWalTxnApplied("table with space");
                serverMain.assertSql("select count() from 'table with space'", "count\n" +
                        totalCount + "\n");
            }
        });
    }
}
