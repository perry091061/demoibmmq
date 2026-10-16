# MQ Message Reader

A small Java program that connects to an IBM MQ queue manager, reads one message from a queue and prints its details.

It shows:

- the message ID
- the put date and time, converted to the local time zone of the machine it runs on
- the message body

## What I wanted to practise

- Connecting to IBM MQ from Java using the IBM MQ classes for Java
- Reading a message from a queue with a wait interval
- Getting at the message descriptor data (message ID, put date and time) that MQ fills in on the message object
- Handling the "no message available" case (reason code 2033) as a normal result rather than an error
- Closing the queue and disconnecting from the queue manager properly, even when something goes wrong

## Requirements

- Java 17
- Maven
- An IBM MQ queue manager to connect to (a local developer container works well)
- The IBM MQ client library, which is declared in `pom.xml` and downloaded by Maven

## Setting up a local queue manager

The program expects these defaults, which match IBM's MQ developer container:

| Setting | Value |
| --- | --- |
| Queue manager | `QM1` |
| Queue | `DEV.QUEUE.2` |
| Host and port | `localhost`, `1414` |
| Channel | `DEV.APP.SVRCONN` |

An example of starting a container with Docker (check IBM's documentation for the current image and options):

```bash
docker run --env LICENSE=accept --env MQ_QMGR_NAME=QM1 \
  --env MQ_ADMIN_PASSWORD=<add password here> \
  --publish 1414:1414 --publish 9443:9443 \
  --detach --name QM1 icr.io/ibm-messaging/mq:latest
```

## Credentials

The user name and password are not stored in the code. The program reads them from environment variables:

| Variable | Meaning |
| --- | --- |
| `MQ_USER` | MQ user name |
| `MQ_PASSWORD` | MQ password |

In PowerShell:

```powershell
$env:MQ_USER = "admin"
$env:MQ_PASSWORD = "your-password"
```

In IntelliJ IDEA, add them under Run, Edit Configurations, Environment variables, for example `MQ_USER=admin;MQ_PASSWORD=your-password`.

## Running it

1. Start the queue manager and make sure `DEV.QUEUE.2` exists.
2. Put a test message on the queue. The MQ web console is at `https://localhost:9443/ibmmq/console` if you are using the developer container.
3. Set the environment variables above.
4. Run the `Main` class.

Example output:

```
Connecting to queue manager: QM1
Connected.
Opening queue: DEV.QUEUE.2
Waiting for a message...
-------------------------------------------------
Message received.
Message ID: 414d5120514d31202020202020202020...
Put date:   16 Oct 2026
Put time:   21:40:41 (Europe/London)
Content:    this is another test
Queue closed.
Disconnected from queue manager.
```

If the queue is empty, the program waits for 10 seconds and then prints `No messages available after waiting.`

## Notes on the put time

MQ records the put date and time in UTC. The calendar object that the MQ classes build carries the machine's local time zone label while holding the UTC values, so calling `toInstant()` on it directly gives the wrong time (an hour out in British Summer Time).

To handle this, the code takes the date and time fields, treats them as UTC, and then converts to the system's default time zone. I found this by comparing the output with my own clock while the UK was on summer time. In winter the two are the same, so the problem would not show up until the clocks change.

## Possible next steps

- Read all messages on the queue in a loop rather than just one
- Add a unit test for the time conversion
- Use `MQGMO_BROWSE_FIRST` and related options to look at messages without removing them
- Send a message to the queue as well as reading one

## Project structure

```
src/main/java/com/example/demoibmmq/Main.java   the MQ reader
pom.xml                                        Maven build and dependencies
```
