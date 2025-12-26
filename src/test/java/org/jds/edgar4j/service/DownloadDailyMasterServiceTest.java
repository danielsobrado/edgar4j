package org.jds.edgar4j.service;

import org.jds.edgar4j.properties.Edgar4JProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DownloadDailyMasterServiceTest {

    @Mock
    private Edgar4JProperties edgar4jProperties;

    @InjectMocks
    private DownloadDailyMasterService downloadDailyMasterService;

    private String testDailyMasterContent;

    @BeforeEach
    public void setUp() {
        testDailyMasterContent = "CIK|Company Name|Form Type|Date Filed|Filename\n" +
                "--------------------------------------------------------\n" +
                "1000045|NICHOLAS FINANCIAL INC|10-Q|2019-02-14|edgar/data/1000045/0001193125-19-039489.txt";
    }

    @Test
    public void testDownloadDailyMaster() {
        LocalDate inputDate = LocalDate.of(2019, 2, 14);
        String inputDateString = inputDate.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        String dailyMasterUrl = "https://www.sec.gov/Archives/edgar/daily-index/test-url";

        when(edgar4jProperties.getDailyMasterUrl(inputDate)).thenReturn(dailyMasterUrl);

        downloadDailyMasterService.downloadDailyMaster(inputDateString);

        verify(edgar4jProperties, times(1)).getUserAgent();
        verify(edgar4jProperties, times(1)).getDailyIndexesPath();
    }
}
