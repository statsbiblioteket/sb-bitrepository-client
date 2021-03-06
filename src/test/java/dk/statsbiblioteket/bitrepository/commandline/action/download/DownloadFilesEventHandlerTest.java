package dk.statsbiblioteket.bitrepository.commandline.action.download;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;

import org.bitrepository.client.eventhandler.CompleteEvent;
import org.bitrepository.client.eventhandler.IdentificationCompleteEvent;
import org.bitrepository.client.eventhandler.OperationFailedEvent;
import org.bitrepository.protocol.FileExchange;
import org.mockito.InOrder;
import org.testng.annotations.Test;

import dk.statsbiblioteket.bitrepository.commandline.action.job.Job;
import dk.statsbiblioteket.bitrepository.commandline.action.job.RunningJobs;
import dk.statsbiblioteket.bitrepository.commandline.util.StatusReporter;

public class DownloadFilesEventHandlerTest {

    public final static String TEST_COLLECTION = "collection1";
    public final static String TEST_PILLAR = "test-pillar";
        
    @Test
    public void testCompleteEvent() throws IOException, URISyntaxException {
        String testFileID = "target/testfile-testCompleteEvent";
        URL fileExchangeUrl = new URL("http://fake-server/dav/file1");    
        Paths.get(testFileID).toFile().deleteOnExit();
        RunningJobs runningJobs = mock(RunningJobs.class);
        BlockingQueue<Job> failedQueue = (BlockingQueue<Job>) mock(BlockingQueue.class);
        StatusReporter reporter = mock(StatusReporter.class);
        FileExchange fileExchange = mock(FileExchange.class);
        
        Job testJob = new Job(Paths.get(testFileID), testFileID, null, fileExchangeUrl);
        when(runningJobs.getJob(testFileID)).thenReturn(testJob);
        
        DownloadFilesEventHandler handler = new DownloadFilesEventHandler(fileExchange, runningJobs, failedQueue, 
                reporter);
        
        CompleteEvent completeEvent = new CompleteEvent(TEST_COLLECTION, null);
        completeEvent.setFileID(testFileID);
        handler.handleEvent(completeEvent);

        InOrder order = inOrder(runningJobs, reporter, fileExchange, failedQueue);
        
        order.verify(runningJobs, times(1)).getJob(eq(testFileID));
        order.verify(fileExchange, times(1)).getFile(any(OutputStream.class), eq(testJob.getUrl()));
        order.verify(reporter, times(1)).reportFinish(eq(testFileID));
        order.verify(runningJobs, times(1)).removeJob(eq(testJob));
        order.verify(fileExchange, times(1)).deleteFile(eq(testJob.getUrl()));
        order.verifyNoMoreInteractions();
    }
    
    @Test
    public void testFailureEvent() throws IOException, URISyntaxException {
        String testFileID = "target/testfile-testFailureEvent";
        URL fileExchangeUrl = new URL("http://fake-server/dav/file1");    
        RunningJobs runningJobs = mock(RunningJobs.class);
        BlockingQueue<Job> failedQueue = (BlockingQueue<Job>) mock(BlockingQueue.class);
        StatusReporter reporter = mock(StatusReporter.class);
        FileExchange fileExchange = mock(FileExchange.class);
        
        Job testJob = new Job(Paths.get(testFileID), testFileID, null, fileExchangeUrl);
        when(runningJobs.getJob(testFileID)).thenReturn(testJob);
        
        DownloadFilesEventHandler handler = new DownloadFilesEventHandler(fileExchange, runningJobs, failedQueue, 
                reporter);
        
        OperationFailedEvent completeEvent = new OperationFailedEvent(TEST_COLLECTION, null ,null);
        completeEvent.setFileID(testFileID);
        handler.handleEvent(completeEvent);
        
        InOrder order = inOrder(runningJobs, reporter, fileExchange, failedQueue);
        
        order.verify(runningJobs, times(1)).getJob(eq(testFileID));
        order.verify(fileExchange, times(1)).deleteFile(eq(testJob.getUrl()));
        order.verify(failedQueue, times(1)).add(eq(testJob));
        order.verify(runningJobs, times(1)).removeJob(eq(testJob));
        order.verifyNoMoreInteractions();
    }
    
    @Test
    public void testUnhandledEvent() throws IOException, URISyntaxException {
        String testFileID = "target/testfile-testUnhandledEvent";
        URL fileExchangeUrl = new URL("http://fake-server/dav/file1");    
        RunningJobs runningJobs = mock(RunningJobs.class);
        BlockingQueue<Job> failedQueue = (BlockingQueue<Job>) mock(BlockingQueue.class);
        StatusReporter reporter = mock(StatusReporter.class);
        FileExchange fileExchange = mock(FileExchange.class);
        
        Job testJob = new Job(Paths.get(testFileID), testFileID, null, fileExchangeUrl);
        when(runningJobs.getJob(testFileID)).thenReturn(testJob);
        
        DownloadFilesEventHandler handler = new DownloadFilesEventHandler(fileExchange, runningJobs, failedQueue, 
                reporter);
        
        IdentificationCompleteEvent identificationEvent = new IdentificationCompleteEvent(TEST_COLLECTION, 
                Arrays.asList(TEST_PILLAR));
        identificationEvent.setFileID(testFileID);
        handler.handleEvent(identificationEvent);
        
        InOrder order = inOrder(runningJobs, reporter, fileExchange, failedQueue);
        order.verify(runningJobs, times(1)).getJob(eq(testFileID));
        order.verifyNoMoreInteractions();
    }
    
    @Test
    public void testFileExchangeFailure() throws IOException, URISyntaxException {
        String testFileID = "target/testfile-testFileExchangeFailure";
        URL fileExchangeUrl = new URL("http://fake-server/dav/file1");    
        Paths.get(testFileID).toFile().deleteOnExit();
        RunningJobs runningJobs = mock(RunningJobs.class);
        BlockingQueue<Job> failedQueue = (BlockingQueue<Job>) mock(BlockingQueue.class);
        StatusReporter reporter = mock(StatusReporter.class);
        FileExchange fileExchange = mock(FileExchange.class);
        
        doThrow(new IOException("Bogus IOException testing fileexchange failure"))
            .when(fileExchange).getFile(any(OutputStream.class), any(URL.class));
        
        Job testJob = new Job(Paths.get(testFileID), testFileID, null, fileExchangeUrl);
        when(runningJobs.getJob(testFileID)).thenReturn(testJob);
        
        DownloadFilesEventHandler handler = new DownloadFilesEventHandler(fileExchange, runningJobs, failedQueue, 
                reporter);
        
        CompleteEvent completeEvent = new CompleteEvent(TEST_COLLECTION, null);
        completeEvent.setFileID(testFileID);
        handler.handleEvent(completeEvent);
        
        InOrder order = inOrder(runningJobs, reporter, fileExchange, failedQueue);
        order.verify(runningJobs, times(1)).getJob(eq(testFileID));
        order.verify(fileExchange, times(1)).getFile(any(OutputStream.class), eq(testJob.getUrl()));
        order.verify(failedQueue, times(1)).add(eq(testJob));
        order.verify(runningJobs, times(1)).removeJob(eq(testJob));
        order.verify(fileExchange, times(1)).deleteFile(eq(testJob.getUrl()));
        order.verifyNoMoreInteractions();
    }
    
}
