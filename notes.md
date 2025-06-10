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
