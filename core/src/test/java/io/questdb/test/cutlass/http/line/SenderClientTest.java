package io.questdb.test.cutlass.http.line;

import io.questdb.PropertyKey;
import io.questdb.ServerMain;
import io.questdb.client.Sender;
import io.questdb.cutlass.line.LineSenderException;
import io.questdb.griffin.model.IntervalUtils;
import io.questdb.std.NumericException;
import io.questdb.test.AbstractBootstrapTest;
import io.questdb.test.TestServerMain;
import io.questdb.test.tools.TestUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.temporal.ChronoUnit;

public class SenderClientTest extends AbstractBootstrapTest {
    @Before
    public void setUp() {
        super.setUp();
        TestUtils.unchecked(() -> createDummyConfiguration());
        dbPath.parent().$();
    }

    @Test
    public void testAppendErrors() throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            try (final TestServerMain serverMain = startWithEnvVariables(
                    PropertyKey.HTTP_RECEIVE_BUFFER_SIZE.getEnvVarName(), "2048"
            )) {
                serverMain.start();
                serverMain.compile("create table ex_tbl(b byte, s short, f float, d double, str string, sym symbol, tss timestamp, " +
                        "i int, l long, ip ipv4, g geohash(4c), ts timestamp) timestamp(ts) partition by DAY WAL");

                int port = IlpHttpUtils.getHttpPort(serverMain);
                try (Sender sender = Sender.builder()
                        .url("http://localhost:" + port)
                        .build()
                ) {

                    sender.table("ex_tbl")
                            .doubleColumn("b", 1234)
                            .at(1233456, ChronoUnit.NANOS);
                    flushAndAssertError(sender, "{" +
                            "\"code\":\"invalid\"," +
                            "\"message\":\"failed to parse line protocol:errors encountered on line(s):\\n" +
                            "error in line 1: table: ex_tbl, column: b; cast error from protocol type: FLOAT to column type: BYTE\",\"line\":1,\"errorId\":");

                    sender.table("ex_tbl")
                            .longColumn("b", 1024)
                            .at(1233456, ChronoUnit.NANOS);
                    flushAndAssertError(sender, "{" +
                            "\"code\":\"invalid\"," +
                            "\"message\":\"failed to parse line protocol:errors encountered on line(s):\\n" +
                            "error in line 1: table: ex_tbl, column: b; line protocol value: 1024 is out bounds of column type: BYTE\",\"line\":1,\"errorId\":");

                    sender.table("ex_tbl")
                            .doubleColumn("i", 1024.2)
                            .at(1233456, ChronoUnit.NANOS);
                    flushAndAssertError(sender, "{" +
                            "\"code\":\"invalid\"," +
                            "\"message\":\"failed to parse line protocol:errors encountered on line(s):\\n" +
                            "error in line 1: table: ex_tbl, column: i; cast error from protocol type: FLOAT to column type: INT\",\"line\":1,\"errorId\":");

                    sender.table("ex_tbl")
                            .doubleColumn("str", 1024.2)
                            .at(1233456, ChronoUnit.NANOS);
                    flushAndAssertError(sender, "{" +
                            "\"code\":\"invalid\"," +
                            "\"message\":\"failed to parse line protocol:errors encountered on line(s):\\n" +
                            "error in line 1: table: ex_tbl, column: str; cast error from protocol type: FLOAT to column type: STRING\",\"line\":1,\"errorId\":");

                }
            }
        });
    }

    @Test
    public void testInsertWithIlpHttp() throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            try (final TestServerMain serverMain = startWithEnvVariables(
                    PropertyKey.HTTP_RECEIVE_BUFFER_SIZE.getEnvVarName(), "2048"
            )) {
                serverMain.start();

                String tableName = "h2o_feet";
                int count = 9250;

                sendIlp(tableName, count, serverMain);

                serverMain.waitWalTxnApplied(tableName, 2);
                serverMain.assertSql("SELECT count() FROM h2o_feet", "count\n" + count + "\n");
                serverMain.assertSql("SELECT sum(water_level) FROM h2o_feet", "sum\n" + (count * (count - 1) / 2) + "\n");
            }
        });
    }

    @Test
    public void testInsertWithIlpHttpServerKeepAliveOff() throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            try (final TestServerMain serverMain = startWithEnvVariables(
                    PropertyKey.HTTP_RECEIVE_BUFFER_SIZE.getEnvVarName(), "2048",
                    PropertyKey.HTTP_SERVER_KEEP_ALIVE.getEnvVarName(), "false"
            )) {
                serverMain.start();

                String tableName = "h2o_feet";
                int count = 9250;

                sendIlp(tableName, count, serverMain);

                serverMain.waitWalTxnApplied(tableName, 2);
                serverMain.assertSql("SELECT count() FROM h2o_feet", "count\n" + count + "\n");
                serverMain.assertSql("SELECT sum(water_level) FROM h2o_feet", "sum\n" + (count * (count - 1) / 2) + "\n");
            }
        });
    }

    @Test
    public void testRestrictedCreateColumnsError() throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            try (final TestServerMain serverMain = startWithEnvVariables(
                    PropertyKey.HTTP_RECEIVE_BUFFER_SIZE.getEnvVarName(), "2048",
                    PropertyKey.LINE_AUTO_CREATE_NEW_COLUMNS.getEnvVarName(), "false"
            )) {
                serverMain.start();
                serverMain.compile("create table ex_tbl(b byte, s short, f float, d double, str string, sym symbol, tss timestamp, " +
                        "i int, l long, ip ipv4, g geohash(4c), ts timestamp) timestamp(ts) partition by DAY WAL");

                int port = IlpHttpUtils.getHttpPort(serverMain);
                try (Sender sender = Sender.builder()
                        .url("http://localhost:" + port)
                        .build()
                ) {
                    sender.table("ex_tbl")
                            .symbol("a3", "3")
                            .at(1222233456, ChronoUnit.NANOS);
                    flushAndAssertError(sender, "{" +
                            "\"code\":\"invalid\"," +
                            "\"message\":\"failed to parse line protocol:errors encountered on line(s):\\n" +
                            "error in line 1: table: ex_tbl, column: a3 does not exist, creating new columns is disabled\",\"line\":1,\"errorId\":");

                    sender.table("ex_tbl2")
                            .doubleColumn("d", 2)
                            .at(1222233456, ChronoUnit.NANOS);
                    flushAndAssertError(sender, "{" +
                            "\"code\":\"invalid\"," +
                            "\"message\":\"failed to parse line protocol:errors encountered on line(s):\\n" +
                            "error in line 1: table: ex_tbl2; table does not exist, cannot create table, creating new columns is disabled\",\"line\":1,\"errorId\":");
                }
            }
        });
    }

    @Test
    public void testRestrictedCreateTableError() throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            try (final TestServerMain serverMain = startWithEnvVariables(
                    PropertyKey.HTTP_RECEIVE_BUFFER_SIZE.getEnvVarName(), "2048",
                    PropertyKey.LINE_AUTO_CREATE_NEW_COLUMNS.getEnvVarName(), "false",
                    PropertyKey.LINE_AUTO_CREATE_NEW_TABLES.getEnvVarName(), "false"
            )) {
                serverMain.start();
                serverMain.compile("create table ex_tbl(b byte, s short, f float, d double, str string, sym symbol, tss timestamp, " +
                        "i int, l long, ip ipv4, g geohash(4c), ts timestamp) timestamp(ts) partition by DAY WAL");

                int port = IlpHttpUtils.getHttpPort(serverMain);
                try (Sender sender = Sender.builder()
                        .url("http://localhost:" + port)
                        .build()
                ) {
                    sender.table("ex_tbl")
                            .symbol("a3", "2")
                            .at(1222233456, ChronoUnit.NANOS);
                    flushAndAssertError(sender, "{" +
                            "\"code\":\"invalid\"," +
                            "\"message\":\"failed to parse line protocol:errors encountered on line(s):\\n" +
                            "error in line 1: table: ex_tbl, column: a3 does not exist, creating new columns is disabled\",\"line\":1,\"errorId\":");

                    sender.table("ex_tbl2")
                            .doubleColumn("d", 2)
                            .at(1222233456, ChronoUnit.NANOS);
                    flushAndAssertError(sender, "{" +
                            "\"code\":\"invalid\"," +
                            "\"message\":\"failed to parse line protocol:errors encountered on line(s):\\n" +
                            "error in line 1: table: ex_tbl2; table does not exist, creating new tables is disabled\",\"line\":1,\"errorId\":");
                }
            }
        });
    }

    private static void flushAndAssertError(Sender sender, String expectedError) {
        try {
            sender.flush();
            Assert.fail("Expected exception");
        } catch (LineSenderException e) {
            TestUtils.assertContains(e.getMessage(), expectedError);
        }
    }

    private static void sendIlp(String tableName, int count, ServerMain serverMain) throws NumericException, NoSuchAlgorithmException, KeyManagementException {
        long timestamp = IntervalUtils.parseFloorPartialTimestamp("2023-11-27T18:53:24.834Z");
        int i = 0;

        int port = IlpHttpUtils.getHttpPort(serverMain);
        try (Sender sender = Sender.builder()
                .url("http://localhost:" + port)
                .build()
        ) {
            if (count / 2 > 0) {
                String tableNameUpper = tableName.toUpperCase();
                for (; i < count / 2; i++) {
                    String tn = i % 2 == 0 ? tableName : tableNameUpper;
                    sender.table(tn)
                            .symbol("async", "true")
                            .symbol("location", "santa_monica")
                            .stringColumn("level", "below 3 feet asd fasd fasfd asdf asdf asdfasdf asdf asdfasdfas dfads".substring(0, i % 68))
                            .longColumn("water_level", i)
                            .at(timestamp, ChronoUnit.MICROS);
                }
                sender.flush();
            }

            for (; i < count; i++) {
                String tableNameUpper = tableName.toUpperCase();
                String tn = i % 2 == 0 ? tableName : tableNameUpper;
                sender.table(tn)
                        .symbol("async", "true")
                        .symbol("location", "santa_monica")
                        .stringColumn("level", "below 3 feet asd fasd fasfd asdf asdf asdfasdf asdf asdfasdfas dfads".substring(0, i % 68))
                        .longColumn("water_level", i)
                        .at(timestamp, ChronoUnit.MICROS);
            }
        }
    }
}