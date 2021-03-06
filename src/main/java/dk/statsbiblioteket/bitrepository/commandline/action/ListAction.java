package dk.statsbiblioteket.bitrepository.commandline.action;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.bitrepository.access.ContributorQuery;
import org.bitrepository.access.getchecksums.GetChecksumsClient;
import org.bitrepository.bitrepositoryelements.ChecksumDataForChecksumSpecTYPE;
import org.bitrepository.bitrepositoryelements.ChecksumSpecTYPE;
import org.bitrepository.bitrepositoryelements.ChecksumType;
import org.bitrepository.common.utils.Base16Utils;
import org.bitrepository.common.utils.CalendarUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.statsbiblioteket.bitrepository.commandline.CliOptions;
import dk.statsbiblioteket.bitrepository.commandline.action.list.ListChecksumsEventHandler;
import dk.statsbiblioteket.bitrepository.commandline.util.ArgumentValidationUtils;
import dk.statsbiblioteket.bitrepository.commandline.util.FileIDTranslationUtil;
import dk.statsbiblioteket.bitrepository.commandline.util.InvalidParameterException;
import dk.statsbiblioteket.bitrepository.commandline.util.MD5SumFileWriter;
import dk.statsbiblioteket.bitrepository.commandline.util.SkipFileException;

/**
 * Action to produce a sumfile from a local directory tree.
 * The class uses the functionality of {@link RetryingConcurrentClientAction} to handle 
 * concurrency and retry logic.  
 */
public class ListAction implements ClientAction {

    private final Logger log = LoggerFactory.getLogger(getClass());

    protected final static int PAGE_SIZE = 10000;
    private GetChecksumsClient getChecksumsClient;
    private Set<String> lastPage = new HashSet<>();
    
    private final String collectionID;
    private final String pillarID;
    private final String localPrefix;
    private final String remotePrefix;
    private final Path sumFile;

    /**
     * Constructor for the action
     * @param cmd The {@link CommandLine} with parsed arguments
     * @param getChecksumsClient The {@link GetChecksumsClient} to retrieve FileIDs and checksums with 
     * @throws InvalidParameterException if input fails validation
     */
    public ListAction(CommandLine cmd, GetChecksumsClient getChecksumsClient) throws InvalidParameterException {
        this.getChecksumsClient = getChecksumsClient;
        collectionID = cmd.getOptionValue(CliOptions.COLLECTION_OPT);
        pillarID = cmd.getOptionValue(CliOptions.PILLAR_OPT);
        sumFile = Paths.get(cmd.getOptionValue(CliOptions.SUMFILE_OPT));
        localPrefix =cmd.getOptionValue(CliOptions.LOCAL_PREFIX_OPT);
        remotePrefix = cmd.getOptionValue(CliOptions.REMOTE_PREFIX_OPT);

        ArgumentValidationUtils.validateCollection(collectionID);
        ArgumentValidationUtils.validatePillar(pillarID, collectionID);
    }
    
    @Override
    public void performAction() {
        ChecksumSpecTYPE checksumSpec = new ChecksumSpecTYPE();
        checksumSpec.setChecksumType(ChecksumType.MD5);
        Date latestResultDate = new Date(0);
        boolean notFinished = true;
        
        try (MD5SumFileWriter md5SumFileWriter = new MD5SumFileWriter(sumFile)) {
            do {
                ListChecksumsEventHandler eventHandler = new ListChecksumsEventHandler(pillarID);
                ContributorQuery[] query = makeQuery(latestResultDate);
                getChecksumsClient.getChecksums(collectionID, query, null, checksumSpec, null, eventHandler, null);
                eventHandler.waitForFinish();
                if(eventHandler.hasFailed()) {
                    log.error("Failed collecting checksumdata");
                    throw new RuntimeException("Error getting checksumdata from pillar: '" + pillarID + "'");
                } else {
                    latestResultDate = reportResults(eventHandler.getChecksumData(), md5SumFileWriter);
                    notFinished = eventHandler.partialResults();
                }
            } while(notFinished);
        } catch (InterruptedException e) {
            log.error("Got interrupted while getting checksums", e);
            throw new RuntimeException(e);
        } catch (IOException e) {
            log.error("Caught IOException while listing files", e);
            throw new RuntimeException(e); 
        } 
    }

    /**
     * Method to report a set of received data. 
     * Results are filtered using the optional local and remote prefixes. The results that is not filtered away
     * are written to the sumfile.  
     * @param results Results to report
     * @param md5SumFileWriter The {@link MD5SumFileWriter} to report results to
     * @return Date The date of the latest file.
     * @throws IOException if writing to the sum file fails
     */
    private Date reportResults(List<ChecksumDataForChecksumSpecTYPE> results, MD5SumFileWriter md5SumFileWriter) 
            throws IOException {
        Date latestDate = new Date(0);
        Set<String> currentPage = new HashSet<>();
        
        for (ChecksumDataForChecksumSpecTYPE checksumData : results) {
            Date calculationDate = CalendarUtils.convertFromXMLGregorianCalendar(checksumData.getCalculationTimestamp());
            if(calculationDate.after(latestDate)) {
                latestDate = calculationDate;
            }
            Path file;
            try {
                file = Paths.get(FileIDTranslationUtil.remoteToLocal(checksumData.getFileID(), localPrefix, remotePrefix));
            } catch (SkipFileException e) {
                log.debug("Skipping file '{}' due to '{}'", checksumData.getFileID(), e.getMessage());
                continue;
            }
            
            if(lastPage.contains(checksumData.getFileID())) {
                continue;
            } else {
                currentPage.add(checksumData.getFileID());
            }
            
            String checksum = Base16Utils.decodeBase16(checksumData.getChecksumValue());
            md5SumFileWriter.writeChecksumLine(file, checksum);
        }    
        
        lastPage = currentPage;
        return latestDate;
    }
    
    /**
     * Method to build the ContributorQuery for the pillar asked to deliver the filelist
     * @param latestResult The first point in time to deliver results from.
     * @return array of {@link ContributorQuery} for a pillar for the next page of results  
     */
    private ContributorQuery[] makeQuery(Date latestResult) {
        List<ContributorQuery> res = new ArrayList<ContributorQuery>();
        res.add(new ContributorQuery(pillarID, latestResult, null, PAGE_SIZE));
        return res.toArray(new ContributorQuery[1]);
    }

}
