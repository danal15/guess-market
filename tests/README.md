# Tests

What each suite covers, and what it needs.

| Suite | Needs a server | Checks | What it is for |
|---|---|---|---|
| `CloseBug.java` | no | 12 | Closing an order book event after a mint. Sweeps all 99 whole-cent prices at five quantities, because a mint splits the base value between two prices and in binary the halves do not always add back to the whole. A close that threw used to leave the event active with its winners already paid, so it could be closed again and pay them twice. |
| `Ex2Regress.java` | no | 79 | That exercise 2 still behaves after exercise 3 changed code they share: the exercise 2 file format, the filters, the balance history now derived from movements, the LMSR figures to the cent, the official order book simulation, minting, and save/reload. |
| `MultiClient.java` | yes | 80 | Four real clients side by side, each with its own session, window and controllers. Thirteen scenarios driven by one client and checked on the others' screens - uploads reaching everybody, trading, minting, going below zero and back, chat, all five acting at once, closing, and that the money adds up. |
| `ClientWalkthrough.java` | yes | 46 | The client from the login screen outwards: two users, the filters, an upload, a trade seen from both sides, resize at 760x560 with the chat open and shut, the three skins, and an unreachable server. |
| `Hammer.java` | yes | 8 | Many clients at once. Sixteen racing for one name (exactly one may win), then twelve trading, polling and chatting together, and the money counted afterwards. |
| `api_test.sh` | yes, **fresh** | 80 | The servlets on their own, the way the lecturer suggests checking them with Postman. Login, uploads and their refusals, money, trading, closing, chat. |
| `edge_test.sh` | yes | 32 | The awkward corners: a user who does nothing, names differing by case or space, boundary values in a file, an event opened with no stock, closing with nobody holding the winner, a stale event id, and what one user may learn about another. |
| `web_test.sh` | yes, **and run-web.bat** | 29 | The web client's own server, which is what exercise 4 adds. That it serves the page, that it hands out nothing outside its folder, and that every kind of call the page makes - form bodies, a JSON body, a query string, a session cookie, a refusal - reaches Tomcat and comes back unchanged. One check sends the same request both ways round and compares the answers, because the proxy has to be a pipe and not a translator. |

## Running them

```
build-ex3.bat          in the project root
run-server.bat         leave it running
tests\run-tests.bat
```

and the two shell ones from Git Bash:

```
bash tests/api_test.sh
bash tests/edge_test.sh
```

`web_test.sh` needs `run-web.bat` running as well as the server:

```
bash tests/web_test.sh
```

`api_test.sh` wants a server that has just started, because the market keeps
its users and event names for as long as it runs and the sample files carry
fixed event names. It says so plainly rather than failing in a confusing way.

## A note on reading failures

Three of the failures these suites found during development were the test
being wrong, not the code - the most instructive being a mint that did not
happen because an earlier scenario had left an ask on the book, and a resale
is cheaper than a mint so the engine correctly took it. Check the scenario
before changing the engine.
