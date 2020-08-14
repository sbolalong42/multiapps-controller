package org.cloudfoundry.multiapps.controller.core.cf.clients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;

import org.cloudfoundry.client.lib.CloudControllerClient;
import org.cloudfoundry.client.lib.domain.ApplicationLog;
import org.cloudfoundry.client.lib.domain.ImmutableApplicationLog;
import org.cloudfoundry.multiapps.common.util.Tester;
import org.cloudfoundry.multiapps.common.util.Tester.Expectation;
import org.cloudfoundry.multiapps.controller.core.util.ImmutableLogsOffset;
import org.cloudfoundry.multiapps.controller.core.util.LogsOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;

class RecentLogsRetrieverTest {

    private static final String APP_NAME = "my-app";
    private static final Calendar TIMESTAMP = new GregorianCalendar(2010, Calendar.JANUARY, 1);

    private final Tester tester = Tester.forClass(RecentLogsRetrieverTest.class);

    @Mock
    private CloudControllerClient client;

    private RecentLogsRetriever recentLogsRetriever = new RecentLogsRetriever();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void testGetRecentLogsWithError() {
        Mockito.when(client.getRecentLogs(APP_NAME))
               .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Something fails"));

        tester.test(() -> recentLogsRetriever.getRecentLogs(client, APP_NAME, null),
                    new Expectation(Expectation.Type.EXCEPTION, "500 Something fails"));
    }

    @Test
    void testGetRecentLogsWithErrorFailSafe() {
        Mockito.when(client.getRecentLogs(APP_NAME))
               .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Something fails"));
        assertTrue(recentLogsRetriever.getRecentLogsSafely(client, APP_NAME, null)
                                      .isEmpty());
    }

    @Test
    void testGetRecentLogsWithNoPriorOffset() {
        Mockito.when(client.getRecentLogs(APP_NAME))
               .thenReturn(Arrays.asList(createAppLog(1, "")));
        assertEquals(Arrays.asList(createAppLog(1, "")), recentLogsRetriever.getRecentLogs(client, APP_NAME, null));
    }

    @Test
    void testGetRecentLogsWithOffsetReturnsNoLogs() {
        LogsOffset offset = createLogsOffset(1, "");
        Mockito.when(client.getRecentLogs(APP_NAME))
               .thenReturn(Arrays.asList(createAppLog(0, "")));
        assertTrue(recentLogsRetriever.getRecentLogs(client, APP_NAME, offset)
                                      .isEmpty());
    }

    @Test
    void testGetRecentLogsWithOffsetSameMessageReturnsNoLogs() {
        LogsOffset offset = createLogsOffset(1, "msg");
        Mockito.when(client.getRecentLogs(APP_NAME))
               .thenReturn(Arrays.asList(createAppLog(1, "msg")));
        assertTrue(recentLogsRetriever.getRecentLogs(client, APP_NAME, offset)
                                      .isEmpty());
    }

    @Test
    void testGetRecentLogsWithOffsetReturnsFilteredLogs() {
        LogsOffset offset = createLogsOffset(1, "");
        Mockito.when(client.getRecentLogs(APP_NAME))
               .thenReturn(Arrays.asList(createAppLog(1, ""), createAppLog(2, "")));
        assertEquals(Arrays.asList(createAppLog(2, "")), recentLogsRetriever.getRecentLogs(client, APP_NAME, offset));
    }

    @Test
    void testGetRecentLogsWithOffsetSameTimestampReturnsFilteredLogs() {
        LogsOffset offset = createLogsOffset(1, "msg");
        Mockito.when(client.getRecentLogs(APP_NAME))
               .thenReturn(Arrays.asList(createAppLog(1, "msg"), createAppLog(1, "msg1")));
        assertEquals(Arrays.asList(createAppLog(1, "msg1")), recentLogsRetriever.getRecentLogs(client, APP_NAME, offset));
    }

    @Test
    void testGetRecentLogsWithOffsetSameMessageReturnsFilteredLogs() {
        LogsOffset offset = createLogsOffset(1, "msg1");
        Mockito.when(client.getRecentLogs(APP_NAME))
               .thenReturn(Arrays.asList(createAppLog(1, "msg"), createAppLog(1, "msg1")));
        assertEquals(Arrays.asList(createAppLog(1, "msg")), recentLogsRetriever.getRecentLogs(client, APP_NAME, offset));
    }

    private ApplicationLog createAppLog(int milis, String message) {
        Calendar cal = (Calendar) TIMESTAMP.clone();
        cal.add(Calendar.MILLISECOND, milis);
        return ImmutableApplicationLog.builder()
                                      .sourceId("")
                                      .sourceName("")
                                      .messageType(ApplicationLog.MessageType.STDOUT)
                                      .message(message)
                                      .applicationGuid("")
                                      .timestamp(cal.getTime())
                                      .build();
    }

    private LogsOffset createLogsOffset(int milis, String message) {
        Calendar cal = (Calendar) TIMESTAMP.clone();
        cal.add(Calendar.MILLISECOND, milis);
        return ImmutableLogsOffset.builder()
                                  .timestamp(cal.getTime())
                                  .message(message)
                                  .build();
    }

}
