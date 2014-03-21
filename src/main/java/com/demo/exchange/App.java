package com.demo.exchange;

import com.demo.exchange.core.CurrencyEvent;
import com.demo.exchange.core.CurrencyEventListener;
import com.demo.exchange.xe.CurrencyLoader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {

    private final static String DESTINATION = "CurrencyExchange.csv";
    private final static int BUFFER_SIZE = 20;

    Logger logger = LoggerFactory.getLogger(getClass());

    public static void main(String[] args) throws Exception {
        new App().load(new File(DESTINATION));
    }

    public void load(File destination) throws IOException {
        // minimal date (startDate) for http://www.xe.com
//        final String startDate = "1995-11-16";
        final String startDate = "2014-03-16";

        final CsvWriter csvWriter = new CsvWriter(destination);
        csvWriter.open();

        final List<Currency> buffer = Collections.synchronizedList(new ArrayList<Currency>());
        final int maxBufferSize = BUFFER_SIZE;
        CurrencyLoader loader = new CurrencyLoader.Builder().startDate(startDate).build();
        loader.addCurrencyEventListener(new CurrencyEventListener() {

            @Override
            public void handle(CurrencyEvent event) {
                Currency currency = event.getCurrency();
                buffer.add(currency);

                // flush buffer
                if (buffer.size() % maxBufferSize == 0) {
                    synchronized (buffer) {
                        writeAll(buffer, csvWriter);
                    }
                }
            }
        });

        loader.load();

        if (!buffer.isEmpty()) {
            // flush buffer
            writeAll(buffer, csvWriter);
        }

        csvWriter.close();
        logger.info("Processing finished");
    }

    private void writeAll(final List<Currency> buffer, final CsvWriter csvWriter) {
        try {
            csvWriter.writeAll(buffer);
        } catch (IOException e) {
            logger.error("Can't write currencies: " + buffer, e);
        }

        buffer.clear();
    }
}
