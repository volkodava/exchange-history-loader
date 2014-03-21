package com.demo.exchange;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.supercsv.cellprocessor.ConvertNullTo;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.CsvBeanWriter;
import org.supercsv.io.ICsvBeanWriter;
import org.supercsv.prefs.CsvPreference;

public class CsvWriter {

    private Logger logger = LoggerFactory.getLogger(getClass());
    private ICsvBeanWriter beanWriter;
    private final File destination;
    private String[] header;
    private CellProcessor[] processors;
    private final ReentrantLock lock = new ReentrantLock();

    public CsvWriter(File destination) {
        this.destination = destination;
    }

    public void open() {
        try {
            beanWriter = new CsvBeanWriter(new FileWriter(destination, true), CsvPreference.STANDARD_PREFERENCE);
            // "date", "code", "name", "unitsPerUsd", "usdPerUnit"
            processors = new CellProcessor[]{
                new ConvertNullTo("?"), // date
                new ConvertNullTo("?"), // code
                new ConvertNullTo("?"), // name
                new ConvertNullTo("?"), // unitsPerUsd
                new ConvertNullTo("?") // usdPerUnit
            };
            // the header elements are used to map the bean values to each column (names must match)
            header = new String[]{"date", "code", "name", "unitsPerUsd", "usdPerUnit"};

            // write the header
            beanWriter.writeHeader(header);
        } catch (IOException e) {
            logger.error("Error while writing data to file: " + destination.getAbsolutePath(), e);
        }
    }

    public void close() {
        if (beanWriter != null) {
            try {
                beanWriter.close();
            } catch (IOException e) {
                logger.error("Error while closing file: " + destination.getAbsolutePath(), e);
            }
        }
    }

    public void writeAll(List<Currency> currencies) throws IOException {
        lock.lock();
        try {
            // write the beans
            for (Currency currency : currencies) {
                beanWriter.write(currency, header, processors);
            }
        } finally {
            lock.unlock();
        }
    }

    public void write(Currency currency) throws IOException {
        lock.lock();
        try {
            // write the bean
            beanWriter.write(currency, header, processors);
        } finally {
            lock.unlock();
        }
    }
}
