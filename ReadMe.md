# Demonstrate Setting Dynamic Log Level

    curl http://localhost:8080/logging
    
An error message from `LoggingController` should show on the server's console.

Set the log level to `TRACE`:

    curl -i -X POST -H 'Content-Type: application/json' -d '{"configuredLevel": "TRACE"}' http://localhost:8080/loggers/demo.logging.LoggingController
    
Send the request to `/logging` again and you should see both a log and a trace log message.

Uncomment `compile "org.grails:grails-logging"` from `build.gradle`, restart the app and do the same steps.  The dynamic level change doesn't appear to work.
