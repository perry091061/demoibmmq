package com.example.demoibmmq;

import com.ibm.mq.MQException;
import com.ibm.mq.MQGetMessageOptions;
import com.ibm.mq.MQMessage;
import com.ibm.mq.MQQueue;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.MQConstants;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.GregorianCalendar;
import java.util.HexFormat;
import java.util.Hashtable;

/**
 * Connects to a local IBM MQ queue manager, reads one message from a queue
 * and prints its ID, put date/time (in local time) and body.
 */
public class Main {

    // Connection details for the local MQ developer container
    private static final String QUEUE_MANAGER = "QM1";
    private static final String QUEUE_NAME = "DEV.QUEUE.2";
    private static final String HOST = "localhost";
    private static final int PORT = 1414;
    private static final String CHANNEL = "DEV.APP.SVRCONN";
    private static final String USER = System.getenv().getOrDefault("MQ_USER", "");
    private static final String PASSWORD = System.getenv().getOrDefault("MQ_PASSWORD", "");
    // How long to wait for a message before giving up (milliseconds)
    private static final int WAIT_INTERVAL_MS = 10_000;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void main(String[] args) {
        MQQueueManager queueManager = null;
        MQQueue queue = null;

        try {
            // Connect to the queue manager using client (TCP) connection properties
            Hashtable<String, Object> connectionProps = new Hashtable<>();
            connectionProps.put(MQConstants.HOST_NAME_PROPERTY, HOST);
            connectionProps.put(MQConstants.PORT_PROPERTY, PORT);
            connectionProps.put(MQConstants.CHANNEL_PROPERTY, CHANNEL);
            connectionProps.put(MQConstants.USER_ID_PROPERTY, USER);
            connectionProps.put(MQConstants.PASSWORD_PROPERTY, PASSWORD);

            System.out.println("Connecting to queue manager: " + QUEUE_MANAGER);
            queueManager = new MQQueueManager(QUEUE_MANAGER, connectionProps);
            System.out.println("Connected.");

            // Open the queue for reading, using whatever sharing mode the queue
            // defines by default. Fail cleanly if the queue manager is shutting down.
            int openOptions = MQConstants.MQOO_INPUT_AS_Q_DEF | MQConstants.MQOO_FAIL_IF_QUIESCING;
            System.out.println("Opening queue: " + QUEUE_NAME);
            queue = queueManager.accessQueue(QUEUE_NAME, openOptions);

            // Wait up to WAIT_INTERVAL_MS for a message to arrive
            MQMessage message = new MQMessage();
            MQGetMessageOptions getOptions = new MQGetMessageOptions();
            getOptions.options = MQConstants.MQGMO_WAIT | MQConstants.MQGMO_FAIL_IF_QUIESCING;
            getOptions.waitInterval = WAIT_INTERVAL_MS;

            System.out.println("Waiting for a message...");
            queue.get(message, getOptions);

            // Read the body, then the descriptor fields that MQ fills in on the message object
            String body = message.readStringOfByteLength(message.getDataLength());
            String messageId = HexFormat.of().formatHex(message.messageId);
            ZonedDateTime putTime = toLocalTime(message.putDateTime);

            System.out.println("-------------------------------------------------");
            System.out.println("Message received.");
            System.out.println("Message ID: " + messageId);
            System.out.println("Put date:   " + putTime.format(DATE_FORMAT));
            System.out.println("Put time:   " + putTime.format(TIME_FORMAT) + " (" + putTime.getZone() + ")");
            System.out.println("Content:    " + body);

        } catch (MQException e) {
            if (e.reasonCode == MQConstants.MQRC_NO_MSG_AVAILABLE) {
                // Not a real failure: the wait simply timed out with an empty queue
                System.out.println("No messages available after waiting.");
            } else {
                System.err.println("IBM MQ error. Completion code: " + e.completionCode
                        + ", reason code: " + e.reasonCode);
                e.printStackTrace();
            }
        } catch (Exception e) {
            System.err.println("Unexpected error.");
            e.printStackTrace();
        } finally {
            closeQuietly(queue, queueManager);
        }
    }

    /**
     * Converts MQ's put timestamp to the machine's local time zone.
     *
     * MQ stores the put date and time in UTC, but the calendar it builds is
     * labelled with the JVM's default time zone, so calling toInstant() on it
     * gives the wrong moment (an hour out during British Summer Time).
     * To avoid that, take the raw date/time fields, treat them as UTC and
     * then convert to local time.
     */
    private static ZonedDateTime toLocalTime(GregorianCalendar putDateTime) {
        return putDateTime.toZonedDateTime()
                .toLocalDateTime()
                .atZone(ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.systemDefault());
    }

    /** Closes the queue and disconnects from the queue manager, whatever happened earlier. */
    private static void closeQuietly(MQQueue queue, MQQueueManager queueManager) {
        try {
            if (queue != null) {
                queue.close();
                System.out.println("Queue closed.");
            }
            if (queueManager != null) {
                queueManager.disconnect();
                System.out.println("Disconnected from queue manager.");
            }
        } catch (MQException e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }
}