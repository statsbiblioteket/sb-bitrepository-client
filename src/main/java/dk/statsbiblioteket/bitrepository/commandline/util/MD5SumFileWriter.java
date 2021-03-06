package dk.statsbiblioteket.bitrepository.commandline.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Helper class to write a file formatted like md5sum in text mode.  
 */
public class MD5SumFileWriter implements AutoCloseable {
    public final static String MD5_FILE_FIELD_SEPERATOR = "  ";
    private final BufferedWriter writer;
    Charset charset = Charset.forName("UTF-8");

    public MD5SumFileWriter(Path sumfile) throws IOException {
        if(Files.exists(sumfile)) {
            throw new RuntimeException("The sum file exists, cannot proceed (sumfile: '" + sumfile + "')");
        }
        this.writer = Files.newBufferedWriter(sumfile, charset, 
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
    
    /**
     * Write a line for a file. 
     * Each line follows the format of coreutils md5sum in text mode and is terminated
     * with the operatingsystem specific newline.
     * @param file The file that the line is about
     * @param checksum The checksum for the file. 
     * @throws IOException if there is trouble writing the output   
     */
    public void writeChecksumLine(Path file, String checksum) throws IOException {
        String line = checksum + MD5_FILE_FIELD_SEPERATOR + file.toString();
        writer.write(line);
        writer.newLine();
    }

    @Override
    public void close() throws IOException {
        writer.flush();
        writer.close();
    }
}
