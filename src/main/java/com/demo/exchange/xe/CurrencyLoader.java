package com.demo.exchange.xe;

import com.demo.exchange.Currency;
import com.demo.exchange.core.CurrencyEvent;
import com.demo.exchange.core.CurrencyEventListener;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.Validate;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CurrencyLoader {

    private final static String MIN_DATE = "1995-11-16";
    private final static String URL_DATE_FORMAT = "yyyy-MM-dd";
    private final static String URL_PATTERN = "http://www.xe.com/currencytables/?from=USD&date=%s";

    private Logger logger = LoggerFactory.getLogger(getClass());

    private final List<CurrencyEventListener> listeners = new CopyOnWriteArrayList<CurrencyEventListener>();

    private ExecutorService pool;
    private long maximumTimeToWait;
    private TimeUnit maximumTimeToWaitUnit;
    private int taskTimeToWait;
    private TimeUnit taskTimeToWaitUnit;
    private int numOfRetry;
    private long pauseBeforeEachRetry;
    private final String startDate;
    private CurrencyConverter converter;
    private int numOfDaysBefore = 0;

    private CurrencyLoader(String startDate) {
        this.startDate = startDate;
    }

    public List<Currency> load() {

        List<Currency> finalBatchResult = new ArrayList<Currency>();
        List<String> allDates = getAllDatesFrom(startDate);
        List<Future<List<Currency>>> futureTasks = new ArrayList<Future<List<Currency>>>(allDates.size());

        for (final String date : allDates) {
            Future<List<Currency>> futureTask = pool.submit(new Callable<List<Currency>>() {

                @Override
                public List<Currency> call() throws Exception {
                    List<Currency> currencies = loadCurrencyForDate(date);

                    return currencies;
                }
            });
            futureTasks.add(futureTask);
        }

        for (Future<List<Currency>> futureTask : futureTasks) {
            try {
                List<Currency> result = futureTask.get(taskTimeToWait, taskTimeToWaitUnit);
                finalBatchResult.addAll(result);
            } catch (Exception ex) {
                logger.error("Thread interrupted", ex);
            }
        }

        shutdownAndAwaitTermination(pool);

        return finalBatchResult;
    }

    public List<Currency> loadCurrencyForDate(String date) {

        if (listeners.isEmpty()) {
            // if there are no listeners here
            throw new IllegalStateException("No listeners have been subscribed for data.");
        }

        final List<Currency> finalResult = new ArrayList<Currency>();

        String url = getUrlForDate(date);

        Document document;
        try {
            document = getDocument(url);
        } catch (IOException e) {
            logger.error("Can't get document by url: " + url, e);
            return finalResult;
        }

        try {
            // get page location
            String location = document.location();
            logger.info("load from url: {}", location);

            Elements tableBodyRows = document.select("table#historicalRateTbl.tablesorter tbody tr");
            for (Element element : tableBodyRows) {
                Elements tableData = element.select("td");
                try {
                    Currency currency = converter.convert(date, tableData);
                    notifyCurrencyEventListeners(currency);
                } catch (NumberFormatException e) {
                    logger.error("Could not process currency for date: " + date + ". URL: " + url, e);
                }
            }
        } catch (Exception e) {
            logger.error("Can't parse document: " + url, e);
        }

        return finalResult;
    }

    private Document getDocument(String url) throws IOException {

        Document document = null;
        IOException exception = null;
        for (int i = 0; i < numOfRetry; i++) {
            try {
                // need http protocol
                document = Jsoup.connect(url)
                    .userAgent("Mozilla")
                    .get();
                exception = null;
            } catch (IOException e) {
                exception = e;
            }

            if (document != null) {
                break;
            }

            try {
                Thread.currentThread().sleep(pauseBeforeEachRetry);
            } catch (InterruptedException ex) {
            }
        }

        if (document == null && exception != null) {
            throw exception;
        }

        return document;
    }

    private String getUrlForDate(String date) {
        String resultUrl = String.format(URL_PATTERN, date);
        return resultUrl;
    }

    private String getPrevDate() {
        Calendar date = Calendar.getInstance();
        if (numOfDaysBefore != 0) {
            date.add(Calendar.DATE, numOfDaysBefore);
        }

        numOfDaysBefore--;

        String dateStr = dateToString(date);

        return dateStr;
    }

    private List<String> getAllDatesFrom(String startDate) {
        List<String> dates = new ArrayList<String>();
        String prevDate = getPrevDate();
        boolean prevDateMatched = startDate.equals(prevDate);

        while (!prevDateMatched) {
            dates.add(prevDate);

            prevDate = getPrevDate();
            prevDateMatched = startDate.equals(prevDate);
        }

        return dates;
    }

    private void shutdownAndAwaitTermination(ExecutorService pool) {
        // Disable new tasks from being submitted
        pool.shutdown();
        try {
            // Wait a while for existing tasks to terminate
            if (!pool.awaitTermination(maximumTimeToWait, maximumTimeToWaitUnit)) {
                // Cancel currently executing tasks
                pool.shutdownNow();
                // Wait a while for tasks to respond to being cancelled
                if (!pool.awaitTermination(maximumTimeToWait, maximumTimeToWaitUnit)) {
                    logger.error("Pool was not terminated.");
                }
            }
        } catch (InterruptedException e) {
            // (Re-)Cancel if current thread also interrupted
            pool.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    public void addCurrencyEventListener(final CurrencyEventListener listener) {
        listeners.add(listener);
    }

    public void removeCurrencyEventListener(final CurrencyEventListener listener) {
        listeners.remove(listener);
    }

    public void notifyCurrencyEventListeners(final Currency currency) {
        for (CurrencyEventListener listener : listeners) {
            listener.handle(new CurrencyEvent(currency));
        }
    }

    public static String dateToString(Calendar date) {
        SimpleDateFormat format = new SimpleDateFormat(URL_DATE_FORMAT);

        String formattedDate = format.format(date.getTime());

        return formattedDate;
    }

    public static Date dateFromString(String dateStr) throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat(URL_DATE_FORMAT);

        Date date = dateFormat.parse(dateStr);

        return date;
    }

    public static class Builder {

        private int numOfThreads = 10;
        private int numOfRetry = 10;
        private long pauseBeforeEachRetry = 200L;
        private String startDate;
        private int taskTimeToWait = 5;
        private TimeUnit taskTimeToWaitUnit = TimeUnit.MINUTES;
        private long maximumTimeToWait = 1;
        private TimeUnit maximumTimeToWaitUnit = TimeUnit.HOURS;

        private CurrencyConverter converter;

        public Builder maximumTimeToWait(long maximumTimeToWait) {
            this.maximumTimeToWait = maximumTimeToWait;

            return this;
        }

        public Builder maximumTimeToWaitUnit(TimeUnit maximumTimeToWaitUnit) {
            this.maximumTimeToWaitUnit = maximumTimeToWaitUnit;

            return this;
        }

        public Builder numOfRetry(int numOfRetry) {
            this.numOfRetry = numOfRetry;

            return this;
        }

        public Builder pauseBeforeEachRetry(long pauseBeforeEachRetry) {
            this.pauseBeforeEachRetry = pauseBeforeEachRetry;

            return this;
        }

        public Builder startDate(String startDate) {
            this.startDate = startDate;

            return this;
        }

        public Builder converter(CurrencyConverter converter) {
            this.converter = converter;

            return this;
        }

        public Builder numOfThreads(int numOfThreads) {
            this.numOfThreads = numOfThreads;

            return this;
        }

        public Builder taskTimeout(int taskTimeout) {
            this.taskTimeToWait = taskTimeout;

            return this;
        }

        public Builder taskTimeoutUnit(TimeUnit taskTimeoutUnit) {
            this.taskTimeToWaitUnit = taskTimeoutUnit;

            return this;
        }

        public CurrencyLoader build() {
            Validate.notBlank(startDate, "Start date must not be blank.");
            Validate.notNull(taskTimeToWaitUnit, "Task time to wait unit must not be null.");
            Validate.notNull(maximumTimeToWaitUnit, "Maximum time to wait unit must not be null.");

            Date startDateFromString = null;
            try {
                startDateFromString = CurrencyLoader.dateFromString(startDate);
            } catch (ParseException e) {
                throw new IllegalArgumentException("Date must be formatted as " + URL_DATE_FORMAT + ". But passed: " + startDate);
            }

            Date minDateFromString = null;
            try {
                minDateFromString = CurrencyLoader.dateFromString(MIN_DATE);
            } catch (ParseException e) {
                throw new IllegalArgumentException("Minimal date must be formatted as " + URL_DATE_FORMAT + ". But passed: " + MIN_DATE);
            }

            if (startDateFromString != null && minDateFromString != null) {
                if (startDateFromString.compareTo(minDateFromString) < 0) {
                    throw new IllegalArgumentException("Start date must be >= " + MIN_DATE);
                }
            }

            if (numOfRetry < 1) {
                throw new IllegalArgumentException("Number of retry must not be < 1");
            }
            if (pauseBeforeEachRetry < 1) {
                throw new IllegalArgumentException("Number of retry must not be < 1");
            }
            if (converter == null) {
                converter = new CurrencyConverter();
            }
            if (numOfThreads < 1) {
                throw new IllegalArgumentException("Number of thread must not be < 1");
            }
            if (taskTimeToWait < 1) {
                throw new IllegalArgumentException("Task time to wait must not be < 1");
            }
            if (maximumTimeToWait < 1) {
                throw new IllegalArgumentException("Maximum time to wait must not be < 1");
            }

            CurrencyLoader loader = new CurrencyLoader(startDate);
            loader.numOfRetry = numOfRetry;
            loader.pauseBeforeEachRetry = pauseBeforeEachRetry;
            loader.converter = converter;
            loader.taskTimeToWait = taskTimeToWait;
            loader.taskTimeToWaitUnit = taskTimeToWaitUnit;
            loader.maximumTimeToWait = maximumTimeToWait;
            loader.maximumTimeToWaitUnit = maximumTimeToWaitUnit;
            loader.pool = Executors.newFixedThreadPool(numOfThreads);

            return loader;
        }
    }
}
