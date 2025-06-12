## Kafka topology

Consider better error handling, backpressure management etc..

```clojure
{:consumer {:subscriptions [:flight-events]
            :group-id "flight-processor"
            :poll-duration 500
            :max-poll-records 100
            :processing {:parallelism 4
                         :batch-size 50
                         :retry-policy {:max-attempts 3
                                        :backoff-ms 1000}}}}
```


```
"auto.offset.reset" "earliest"
"max.poll.records" "100"
"session.timeout.ms" "30000"
"heartbeat.interval.ms" "10000"}
```

I had a case where the system halted between persisting the event and
producing to the next topic. Meaning the record was persisted, but then threw on closed producer causing the batch to fail.

This is a big edge case, since the system won't likely just shut down like this. However it may be worthwile making these into revertible transactions.
