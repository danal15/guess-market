# Guess Market

A prediction market, built up over four exercises of a Java course. Each exercise
is tagged at the commit that was submitted, so `git checkout ex2` is exactly what
the grader of exercise 2 received.

| Tag | Exercise | What it adds | Readme |
|---|---|---|---|
| `ex1` | Console application | The engine: LMSR events, buying shares, closing and paying out; save and load | (inside the tag) |
| `ex2` | JavaFX desktop | Users and market makers, the order book with matching and minting, filters, skins and animations, charts | `README-EX2.pdf` |
| `ex3` | Client / server | The engine behind a Tomcat war with thin servlets; a JavaFX client that talks to it over HTTP; many users at once; chat | `README-EX3.pdf` |
| `ex4` | Web client (bonus) | A browser client in plain HTML, CSS and JavaScript against the unchanged exercise 3 server | `README-EX4.pdf` |

## Modules

- `engine/` - the market itself. No JavaFX, no servlet code; it answers whoever calls it.
- `desktop/` - the exercise 2 JavaFX application, kept as the screens the later clients reuse.
- `server/` - the war: one servlet per thing a user can do, each a thin call into the engine.
- `client/` - the exercise 3 JavaFX client. `MarketClient` implements the same interface the screens were written against, only over HTTP.
- `web/` - the exercise 4 browser client, and the small Node server that serves it and passes its calls through to Tomcat.
- `tests/` - the test suites, and `tests/README.md` on what each covers.
- `test-files/` - the sample event files for each exercise.

## Building and running

```
build-ex3.bat        builds engine, war and the JavaFX client into ex3-client\
run-server.bat       deploys the war into lib\tomcat and starts it
run-client.bat       starts the JavaFX client
run-web.bat          starts the web client; then open http://localhost:3000
tests\run-tests.bat  runs the suites (see tests\README.md)
```

Java 25, Tomcat 10.1 and Node 16 or newer. JavaFX and Gson are packed into the
jars; the web client has no packages to install.

Dan Alushe - 212472864
