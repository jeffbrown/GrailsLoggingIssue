# Grails Logging Plugin Issue Demonstrator

This application was created to illustrate an issue encountered when using the `grails-logging` plugin.

## Issue
When using the `grails-logging` plugin, we cannot use the Spring Boot `loggers` actuator endpoint to dynamically 
set the log level.  

## Setup

The Grails 3.3.3 application was created with:

`grails create-app GrailsLoggingIssue --profile=rest-api`

To support the log testing, I created a **trait** that would emit a log message for a certain level if that log level were in effect for 
that class:
```groovy
package demo

trait LogHelper {

    void log() {
        String simpleName = this.class.simpleName

        log.error ">>> ${simpleName} error message"
        log.warn ">>> ${simpleName} warn message"
        log.info ">>> ${simpleName} info message"
        log.debug ">>> ${simpleName} debug message"
        log.trace ">>> ${simpleName} trace message"
    }

}
```

I coded `Bootstrap.groovy` to use that trait:
```groovy
package grailsloggingissue

import demo.LogHelper
import groovy.util.logging.Slf4j

class BootStrap implements LogHelper {

    def init = { servletContext ->
        log()
    }
    def destroy = {
    }
}
```

For testing logging by non-Grails artifacts, I created a simple class that implemented the trait:
```groovy
package demo.logging

import demo.LogHelper
import groovy.util.logging.Slf4j

@Slf4j
class AnotherLogEmitter implements LogHelper {
    void doSomeLogging() {
        log()
    }
}
```

I created a controller to test logging by artifacts under `grails-app`. It also invokes the non-artifact class 
to cause log statements to be emitted:
```groovy
package demo.logging

import demo.LogHelper
import groovy.util.logging.Slf4j

class LoggingController implements LogHelper {

    static responseFormats = ['json']

    def index() {
        log()

        new AnotherLogEmitter().doSomeLogging()

        respond([message: 'done'])
    }
}
```

### Setup needed for Log4J2 and Spring boot Actuators

Because Spring Boot uses Logback by default, the `build.gradle` file was changed to exclude the `logback` 
and `spring-boot-starter-logging` dependencies from all configurations using the code block:

```groovy
configurations {
    all*.exclude group: 'ch.qos.logback'
    all*.exclude group: 'org.springframework.boot', module: 'spring-boot-starter-logging'
}
```

To use Log4J2 with YML configureation, the following dependencies were added:
```groovy
compile "org.springframework.boot:spring-boot-starter-log4j2"
compile "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.9.4" // enables yml format for log4j2
```

Also, the `logback.groovy` file was deleted. In its place, a `log4j2.xml` was added to configure the logging. 
The Loggers configuration was excerpted and shown below:

```yaml
Loggers:
  Root:
    level: WARN
    AppenderRef:
      - ref: Console
	
  logger:
    -
      name:  demo.logging
      level: INFO
      additivity: false
      AppenderRef:
        - ref: Console
    -
      name:  demo.logging.AnotherLogEmitter
	  level: DEBUG
	  additivity: false
      AppenderRef:
        - ref: Console
``` 

Finally, the `application.yml` file was changed to enable the actuator endpoints and configure the `loggers` 
endpoint to not be sensitive:
```yaml
endpoints:
  enabled: true
    loggers:
      sensitive: false
    jmx:
      enabled: true
```

## Scenario 1: Removed the `grails-logging` dependency
I saw the likely cause of the issue to be the `grails-logging` plugin.

To test my hypothesis the likely cause of the issue to be the `grails-logging` plugin, I removed the affects 
of the plugin by commenting it out in `build.gradle` and _manually_ adding the `@Slf4j` AST annotations to the 
Grails artifacts in the app; i.e. `Bootstrap` and `LoggingController`.

These artifacts would automatically get a **log** reference as a result of using the `grails-logging` plugin. 
The `AnotherLogEmitter` class also had the `@Slf4j` annotation added, but it would not automatically get that 
from the `grails-logging` plugin anyway. 

I then executed a series of tests to see how the logging behaved when the `grails-logging` plugin was out of 
the picture.

### Startup
When the application started, the console showed these log statements (edited for brevity):
```
grailsloggingissue.BootStrap : >>> BootStrap error message
grailsloggingissue.BootStrap : >>> BootStrap warn message
``` 
So far so good. We'd expect to get WARN and ERROR level messages based on the ROOT logger configuration:
```yaml
Root:
  level: WARN
  AppenderRef:
    - ref: Console
```

### Verify the loggers via the Spring Boot actuator `loggers` endpoint

Hitting the actuator endpoint at **`http://localhost:8080/loggers`** shows the following:
```json
{
    "levels": [
        "OFF",
        "FATAL",
        "ERROR",
        "WARN",
        "INFO",
        "DEBUG",
        "TRACE"
    ],
    "loggers": {
        "ROOT": {
            "configuredLevel": "WARN",
            "effectiveLevel": "WARN"
        },
        "demo.logging": {
            "configuredLevel": "INFO",
            "effectiveLevel": "INFO"
        },
        "demo.logging.AnotherLogEmitter": {
            "configuredLevel": "DEBUG",
            "effectiveLevel": "DEBUG"
        }
    }
}
```
### Verify the static logger configuration is in effect

When I hit the controller's logging action endpoint at **`http://localhost:8080/logging`**, I get the expected 
simple JSON message and the console shows the following (edited for brevity and clarity):
```
demo.logging.LoggingController : >>> LoggingController error message
demo.logging.LoggingController : >>> LoggingController warn message
demo.logging.LoggingController : >>> LoggingController info message

demo.logging.AnotherLogEmitter : >>> AnotherLogEmitter error message
demo.logging.AnotherLogEmitter : >>> AnotherLogEmitter warn message
demo.logging.AnotherLogEmitter : >>> AnotherLogEmitter info message
demo.logging.AnotherLogEmitter : >>> AnotherLogEmitter debug message
```
This matches up with the logger configuration.

### Dynamically change the log level

I then wanted to simulate a bumping up the logging level for the non-Grails artifact, `AnotherLogEmitter`. To 
accomplish this, I POSTed a change via the Spring boot actuator `loggers` endpoint. Using `curl`:
```bash
curl -i -X POST -H 'Content-Type: application/json' -d '{"configuredLevel": "TRACE"}' http://localhost:8080/loggers/demo.logging.AnotherLogEmitter
``` 
Doing a GET to the `loggers` endpoint, revealed the change was in effect:
```json
{
    "levels": [
        "OFF",
        "FATAL",
        "ERROR",
        "WARN",
        "INFO",
        "DEBUG",
        "TRACE"
    ],
    "loggers": {
        "ROOT": {
            "configuredLevel": "WARN",
            "effectiveLevel": "WARN"
        },
        "demo.logging": {
            "configuredLevel": "INFO",
            "effectiveLevel": "INFO"
        },
        "demo.logging.AnotherLogEmitter": {
            "configuredLevel": "TRACE",
            "effectiveLevel": "TRACE"
        }
    }
}
``` 
Now, exercising the `logging` action, I get this for log statements (edited for brevity and clarity):
```
demo.logging.LoggingController : >>> LoggingController error message
demo.logging.LoggingController : >>> LoggingController warn message
demo.logging.LoggingController : >>> LoggingController info message

demo.logging.AnotherLogEmitter : >>> AnotherLogEmitter error message
demo.logging.AnotherLogEmitter : >>> AnotherLogEmitter warn message
demo.logging.AnotherLogEmitter : >>> AnotherLogEmitter info message
demo.logging.AnotherLogEmitter : >>> AnotherLogEmitter debug message
demo.logging.AnotherLogEmitter : >>> AnotherLogEmitter trace message
```
Perfect! I dynamically set the logging level to TRACE and am now seeing TRACE level messages.

To verify Grails artifacts can get their log level changed dynamically, I repeated the same for the controller.

First, POST with `curl`:
```bash
curl -i -X POST -H 'Content-Type: application/json' -d '{"configuredLevel": "TRACE"}' http://localhost:8080/loggers/demo.logging.LoggingController
```

Let's see what the `loggers` endpoint says:
```json
{
    "levels": [
        "OFF",
        "FATAL",
        "ERROR",
        "WARN",
        "INFO",
        "DEBUG",
        "TRACE"
    ],
    "loggers": {
        "ROOT": {
            "configuredLevel": "WARN",
            "effectiveLevel": "WARN"
        },
        "demo.logging": {
            "configuredLevel": "INFO",
            "effectiveLevel": "INFO"
        },
        "demo.logging.AnotherLogEmitter": {
            "configuredLevel": "TRACE",
            "effectiveLevel": "TRACE"
        },
        "demo.logging.LoggingController": {
            "configuredLevel": "TRACE",
            "effectiveLevel": "TRACE"
        }
    }
}
```

Interestingly, an entirely new logger was added for the LoggingController. And of course, it was set to TRACE level logging. It's 
worth noting that, if I set the LoggingController logger back to its static log level, the dynamic logger entry remains.  This 
opens the door to the possibility that long-term application use with many dynamic logging changes could introduce resource or 
performance issues.

Finally, exercise the `logging` action and see the results:
```
demo.logging.LoggingController : >>> LoggingController error message
demo.logging.LoggingController : >>> LoggingController warn message
demo.logging.LoggingController : >>> LoggingController info message
demo.logging.LoggingController : >>> LoggingController debug message
demo.logging.LoggingController : >>> LoggingController trace message

demo.logging.AnotherLogEmitter : >>> AnotherLogEmitter error message
demo.logging.AnotherLogEmitter : >>> AnotherLogEmitter warn message
demo.logging.AnotherLogEmitter : >>> AnotherLogEmitter info message
demo.logging.AnotherLogEmitter : >>> AnotherLogEmitter debug message
demo.logging.AnotherLogEmitter : >>> AnotherLogEmitter trace message
```
Excellent! Again, we were able to dynamically change the log level, this time for a Grails artifact.

## Scenario 2: Adding back the `grails-logging` dependency

Proving dynamic log level adjustment works without the `grails-logging` plugin, I now needed to confirm my hypothesis 
the likely cause of the issue to be the plugin by reintroducing the affects of the plugin.
 
First, I uncommented it in `build.gradle` and removed the `@Slf4j` AST annotations to the Grails artifacts in the app; 
i.e. `Bootstrap` and `LoggingController`.

These artifacts will now automatically get a **log** reference as a result of using the `grails-logging` plugin. The 
`AnotherLogEmitter` class' `@Slf4j` annotation remains of course, as it will not automatically get a **log** reference 
from the `grails-logging` plugin. 

I then re-executed the same tests to see how the logging behaves with the `grails-logging` plugin.

**Important: No other configuration was changed.** I just re-enabled the `grails-logging` plugin and removed the two `@Slf4j` annotations 
I manually added before to the Grails artifacts.

To ensure no leftover configuration was used, I executed with a `gradle clean bootRun` to start the server.

### Startup
When the application started, the console showed these log statements (edited for brevity):
```
grailsloggingissue.BootStrap : >>> BootStrap error message
grailsloggingissue.BootStrap : >>> BootStrap warn message
``` 
That's what we got before; so far so good.

### Verify the loggers via the Spring Boot actuator `loggers` endpoint

Hitting the actuator endpoint at **`http://localhost:8080/loggers`** gives the same as before:
```json
{
    "levels": [
        "OFF",
        "FATAL",
        "ERROR",
        "WARN",
        "INFO",
        "DEBUG",
        "TRACE"
    ],
    "loggers": {
        "ROOT": {
            "configuredLevel": "WARN",
            "effectiveLevel": "WARN"
        },
        "demo.logging": {
            "configuredLevel": "INFO",
            "effectiveLevel": "INFO"
        },
        "demo.logging.AnotherLogEmitter": {
            "configuredLevel": "DEBUG",
            "effectiveLevel": "DEBUG"
        }
    }
}
```
### Verify the static logger configuration is in effect

When I hit the controller's logging action endpoint at **`http://localhost:8080/logging`**, I get the expected 
simple JSON message and the console shows the same results as before (edited for brevity and clarity):
```
demo.logging.LoggingController : >>> LoggingController error message
demo.logging.LoggingController : >>> LoggingController warn message
demo.logging.LoggingController : >>> LoggingController info message

demo.logging.AnotherLogEmitter : >>> AnotherLogEmitter error message
demo.logging.AnotherLogEmitter : >>> AnotherLogEmitter warn message
demo.logging.AnotherLogEmitter : >>> AnotherLogEmitter info message
demo.logging.AnotherLogEmitter : >>> AnotherLogEmitter debug message
```
This matches up with the logger configuration. It also tells us that the `grails-logging` plugin is not interfering with 
the static logger configuration, which is a good thing. 

### Dynamically change the log level

Next, I then wanted to simulate bumping up the logging level for the non-Grails artifact, `AnotherLogEmitter`. I POSTed the same 
change as before via the Spring boot actuator `loggers` endpoint. Using `curl`:
```bash
curl -i -X POST -H 'Content-Type: application/json' -d '{"configuredLevel": "TRACE"}' http://localhost:8080/loggers/demo.logging.AnotherLogEmitter
``` 
Doing a GET to the `loggers` endpoint, revealed the change was in effect:
```json
{
    "levels": [
        "OFF",
        "FATAL",
        "ERROR",
        "WARN",
        "INFO",
        "DEBUG",
        "TRACE"
    ],
    "loggers": {
        "ROOT": {
            "configuredLevel": "WARN",
            "effectiveLevel": "WARN"
        },
        "demo.logging": {
            "configuredLevel": "INFO",
            "effectiveLevel": "INFO"
        },
        "demo.logging.AnotherLogEmitter": {
            "configuredLevel": "TRACE",
            "effectiveLevel": "TRACE"
        }
    }
}
```
So far so good! The `grails-logging` plugin is not interfering with our ability _to dynamically set_ the logging level.

 
Now, exercising the `logging` action, I get this for log statements (edited for brevity and clarity):
```
demo.logging.LoggingController : >>> LoggingController error message
demo.logging.LoggingController : >>> LoggingController warn message
demo.logging.LoggingController : >>> LoggingController info message

demo.logging.AnotherLogEmitter : >>> AnotherLogEmitter error message
demo.logging.AnotherLogEmitter : >>> AnotherLogEmitter warn message
demo.logging.AnotherLogEmitter : >>> AnotherLogEmitter info message
demo.logging.AnotherLogEmitter : >>> AnotherLogEmitter debug message
demo.logging.AnotherLogEmitter : >>> AnotherLogEmitter trace message
```
Perfect! I dynamically set the logging level to TRACE and am now seeing TRACE level messages.

The last step was to exercise the `logging` action. Sadly, I get this for log statements (edited for brevity and clarity):
```
demo.logging.LoggingController : >>> LoggingController error message
demo.logging.LoggingController : >>> LoggingController warn message
demo.logging.LoggingController : >>> LoggingController info message

demo.logging.AnotherLogEmitter : >>> AnotherLogEmitter error message
demo.logging.AnotherLogEmitter : >>> AnotherLogEmitter warn message
demo.logging.AnotherLogEmitter : >>> AnotherLogEmitter info message
demo.logging.AnotherLogEmitter : >>> AnotherLogEmitter debug message
```
**Ouch! Even though I successfully, dynamically set the logging level to TRACE, I am still seeing DEBUG level messages.**

I repeated the steps for the LoggingController and got the same undesirable results so I'll spare you the console output.

## Conclusion

**The `grails-logging` plugin seems to be interfering with our ability to _use_ dynamically set Log4J2 logging levels. 
Statically defined log configuration works fine. Dynamically setting the log levels works fine. Applying those dynamic 
settings fails.** 

The `grails-logging` plugin is somehow interfering with the _use_ of the dynamically set log levels.

***
Edited with help from: [Markdown-Cheatsheet](https://github.com/adam-p/markdown-here/wiki/Markdown-Cheatsheet)