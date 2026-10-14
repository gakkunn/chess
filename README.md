# ♕ BYU CS 240 Chess

This project demonstrates mastery of proper software design, client/server architecture, networking using HTTP and WebSocket, database persistence, unit testing, serialization, and security.

## 10k Architecture Overview

The application implements a multiplayer chess server and a command line chess client.

[![Sequence Diagram](10k-architecture.png)](https://sequencediagram.org/index.html#initialData=C4S2BsFMAIGEAtIGckCh0AcCGAnUBjEbAO2DnBElIEZVs8RCSzYKrgAmO3AorU6AGVIOAG4jUAEyzAsAIyxIYAERnzFkdKgrFIuaKlaUa0ALQA+ISPE4AXNABWAexDFoAcywBbTcLEizS1VZBSVbbVc9HGgnADNYiN19QzZSDkCrfztHFzdPH1Q-Gwzg9TDEqJj4iuSjdmoMopF7LywAaxgvJ3FC6wCLaFLQyHCdSriEseSm6NMBurT7AFcMaWAYOSdcSRTjTka+7NaO6C6emZK1YdHI-Qma6N6ss3nU4Gpl1ZkNrZwdhfeByy9hwyBA7mIT2KAyGGhuSWi9wuc0sAI49nyMG6ElQQA)

## Phase 2 Server Design

The sequence diagram documents the server, handler, service, data access, and database interactions for all seven HTTP endpoints.

[![Phase 2 Chess Server Design](server-design.png)][phase-2-server-design]

[Open the Phase 2 server design in read-only Presentation Mode][phase-2-server-design].

[phase-2-server-design]: https://sequencediagram.org/index.html?presentationMode=readOnly#initialData=C4S2BsFMAIGEAtIGcnQMqQE4DcvQCLIgDmAdgFDkCGAxsAPaZzgiSnDkAOVmoNI3duiy5MXHnwFUhAVSRYAEtIAmUMd14h+g4NADiVALaQlpVVnGbt03bCg9T59RK1TZ8zBhxbIlyTv0jSC9sHz9XALtIHhCwjX8baDksfABBAHlw6yFUgFdgeDTM+IjEg2Mi8mUqYCoAIyp5Ahr6xt9yNmBMAE8kbn5SYmgABgA6AE5KYkx6XM4AYgAWAGYADgAmcZpoeZZieGA68FyYACVIYhAkYAtSehvoelEkj0c1ABo8gqKALmhzy7XLDnACOJ2uAApch5SEF3tBuCgAO6MZTwyCGKggcAASgAOqQAVcbphzkhcuBgFCYXDoFR8vAACr0ADWbHxpGSmHwLWpWFhxnhiKQKMwaOgGKxuIJX0KvPpBWZbNI8Oh-KCOPIdlYQgAtAA+YQ4LB-ADaAAV0mhGQBdaAAejVmAJAG8AEROgWQN0-N1u+Fu4Wi5Q+v0ByXY0NugC+5BCeANL0UKjUf3gKcgRKBmAhmEgYOQwAAQvRlN1NVy3gnDZWM5g-spkFgQFQWAAvM4XYnA-Pgji1sxqaCJrmxGiQP55wEkiFZkmgvua8h1PNUFnQVLQPNgkB55TQABmrHA+6u0EMVyQIEG5FHIh8w8T8fr0CLVGUC8LAFEAB7jzigPQFDPo+hrap0fyLMMwyum6xgoFQxDer6X6YDML4NPu259jGHRmJQd7eOOw41h4vzQEhwBcnymBehWZEZCRzS1A08h-EeZhJtyLTQHU3TQJ6QTkDyLFtKBXHkVyIlUI8TCkBS4CUCu0TroyiACTSxjQGerarmWW5dtmkDKLeDHpOJhGhOOfxSS0plYGOMC6k+Igmhu4B6d0jJrmwv7-oBwGuUwzlgSwEHQFByywfBSCIchbqoehfy6dE+m1MquFsCZ9ncoxIVcY5fzyeAimWQ+I5mX8NCrjc1G2bU9EpHlhrSaxE7aaQHi6PVVDCS0bUWZV0DkjQ44oDlRSDQ597WcNuSjcgSAER4jlMWVs1IaQWA1JAspKmwEKNZ4M1OYasrkdV0Q3LKELnS0mrnc1zGtGxHVdRuDLSX1olNPlj3pH8I1jUt-1TcdRHtUDi3LdNENg1WL5zj25KUjRXrwgqTKsuyOUI2trx1oDzatiAHb-IZ87IBS-YE4OCYucaL55n0QHyCWZZxkF4ngewfzrNBsGCcYUYBpj+2kFGsbkHcDxPHgz7vK1bR-JuB5SsZBCMfQnDbQF0AFDMSKoNJqQLSgfmQABIBAaMBJqTAHjPHmwC5LRqAAKzQdASJgPAdKkBKaGMOei1xaMeHZdMswLIw0hITsABisBfvgsCbgAMvQlwUDLMBy0wA5OJ8n0ZH8mfZ5+kJC5AQqNCKqIcuX15ktTaO0mL2OkByPVt4KCJ18G6KYtiHJ3bUEId8qqqaZAmo87oDOiGalrWna9ryCg1ukILM8i9AgYD6ikuc4z+PJnTL7pnTTekLmvaFuz5a43WZ+YAjDZNpgLbtjAN+VzT58nCv0KtAcAWdrwQj-vfa4S5lJrg3AZHce5DzHlPKgC8m8bzrScovNyb4PzQOABbK2QET7PHyvPSCAtSDuhinFUMiVGB-EwognCsYsow3BlZU6ElS4UUgFRDwvdZ4TSekrV6HF9w9V4vxau30Xo4NIk1AGEkeLB2KqVMyYMQHSPUQpHKq0Kqw24VVeghgNAwAKA7XIdQLzABuPuFQw0GDIKDKiJay5VyqXUk6aAyh6DIGgDLCUP5iSyX1upNxYo-EBNQMEzEwAaDwAMSdcSz4bKwgZIwUmxliEBTIfTUKOpgBUIAIzRVDkhBhQcXy5EyQUbJHYQzsPwikuGRiuE+D+JtXWMAZJbSRAJUgIACx0gZOLNp3CmL-SqjVXaDJbqfXuuQUGiZxHtWvO9MevV1niRmXNM2IMS7mXytgwG81gacMMR09+0AoEoypNXDG4zO5HTxjcwmw1iY-zueAwkVNKTPwvmkoKk5kCcFZpAR+BTgqJkodAfmMEaEel3r6f0+9J5sGPtLe4edngK3WSrQ86t9yTW1rrLeETDbGxaKbYGeSt621IPbL5p9nau06tAT2wxva+39oHdCIcEJIXDhw8gUc5jzGmJANgOw9gHGlbK8uswOC50eM8QuHx9nKvyP-CeLzlSNyziqluqNR5LPHpilUGl1TGDnmFPUhp0nQFNPgL86cvyMi-GvDeV4gIEkxo0moW8-gAB4rX6hha-W5V9zA6qpIGr+bZg1AQUKlLAbyX4fIvrMq6v9jW6sIYeGYhgIkwEQO+CwmrqwFROn8MBxAVWQILcAf+S5sHTOOd0gRN0rUPWOUxQlKDOLbJkWMxUnd5EDT+l2j63w1FyX0asjpIDR16JKkpLx0AWVWu0ugy814hjB2vNgEmJkO35WdTIep8BGm5L-JbfJIEKEOpKRFYY5TkV0KqShGpfw6mJpyc0iOVzUmJn2Y2KA10Fl9pWQOtZ-VlZ+MgFBmAo6+LjqxsqKdYkZ3fD4VDcay6lGdNmoRjxF7s1ODLi201VJM3Aqo6mVl38cm-MbYWh5QKgGXtBQZFmnUoWlm6FGl9xS+bUJdFLNV+cjSiEVoh16qsSWa3MuSzAKaA4G3oEbZ6dLFoMptnbdSjs8Dsrdlyr2PsCj8qwIK79kBRWtIlQsZB8xODXnXOnMJ5RkA4tls8XzCN3i+fIt564vmkB6r7QScLwBIt0YhMQIISAOShd5Ml4wABJfA8IkTwDAJALk6NeLgFoCyYrtJMuQAAHJVY1ASXzaBciGExD0JLQQct5YK7VGe8Ijjlcq33ardW7ValfUxZ1po9CerXtVgNWSk2abDRG0ThogufNjVAOLkX9UNKWwFNNlbMCag24x9bQRbmXR2nc4kCWi0HhLWW6AFbGxiDOzxi7xgQEsAiylyBd2UttsoE1sDZ1Z2UV7QanGqyWqKfapIudcpahjqtTh364HZ1rsXRu4jgRvt1qR9JcJGjN0qW3epXdZ4MFXkGOEk9Z7yCg-abgl817ANNMM4FU+YnwpQU-bQyp8VGG1JvXe4DYrmdTMTKFvhv34v-dO0ESaCGfoI+vI4kq+PIDE7zDQdx6PFHa7C8SUN6XaiRtlycmXQQQFxbN8rlolvbdg+1yA-XpBRC6GiEk43PEGBu5a21kTTOXcs6+yYT5O2gcAqpNV1LofjDvIj7cjwrGybR-gnRxPkfGOs7BQJtmwnRNwtfRJpF7p48+lNJXzr+AfSlPWMsRY7wCRuny4VobyENEBgGzQCrqKe9t5G0ESWNppO4vVXgD7Hwrd-BZfHukaEqD8UxPxOoMAMQAW6EyprQeeD8TMWAVAViOoklhOAOAiAUC+ecTtcOMn8VBQU2rolatsQazJTrDTettO6ZNoclzkyiyqZkwOZpytyryjZtIAKsHA5k5pHDMJKsoAAOzjDDCQA8rzBfjLCwDJwABscAcy2u-meK0+l2dYIWyufCsAcyvm0W0O1qI+dqBItBea9BseHW2W+AaWju481WXW3sPWRWfWpWg2ohzBNcFEDWpA2ye2WGbA08tqIi88k2fGFoVotoDo82YuB2Ia0A4ajB+osEkhx8z6Kem2GYbBO0vmd8BY1wj8zy+2pMmmR2b2SuSeWaFhOayGaeJMZMp+dQwm-sWEqUz2aOM+NaUuXS0A12Nwth1h8RQQwOni5Okhe654B6N40RxEvGjMfw+C-8XOJeRSfOkmcEQu1SSUvE74rChYmUrSORPC+ykOMGjB-a+GJycOr+w6+46G-EaOuyeGhQfC2OQSS68GNuBOEMKsFqMk66ikqR8CO6jBGRNOh69OnujOTRIK+RSQuhQGxRz6pe4m76AuFRwqwuf6QyHOxkDR2UOxUxwQhOcRMA5uVAEIww8IPe4xJUregwQQo2UhAyV+i0thOIHhzx7SEeF0dBQQEI7xkJKu3RCifwmyWAug7xIR-CW0P+MAAh+Ahu4kc+0h3BOeKuTxICBJIOYe0uTxtyiRkAHBDyXBkAOWkJye2uqe3ybGjJzJ1MOe7y+e-GEKgm0KxxpRvMCK5RBJDeTeiwE+AW5B0x3CVBFQfCVudIm8ZAqAkAog-EIJ1WdIQyIyJwCI9AV4oAuApJbJ+AD+k+smBK8Ob+KmX+FKQEVKOmNKtQ+m5sD6JCpAwBJmXM4BHsVmfKMBdmcBQuCBUwSBCw3QKGYCgyuwJABwiZJUOm0AAAUvQNeNrjsH3iyKQVPkwJEZgGqTrnwrmdeBwfYQmowUKGVomZgLAPQGAhWTaeyQSDWaQPyWao1nwR8QSd1p3qIUWV3vCJIVOTIXIVakobRBqONsUmoXsRaDIFofaDoRzstgYRGrBJwM2VgG2R2aGAAOoKBZZer2hFjpypCwAADS6KbospPwjezesY5hXJlhdMvZth2ED8wmTht6ehqa6aJ2gpXh35PhjYfhPygRwRTiek4RjBkF52buhOAAVnmX2fCX+ckYQrAlugSWsVkUekwAziwGiASMHIeSvsee2cenEvcNABeVeV+OErefeQ+Tntck6nxoUYQkcVzLzlKVBBXhcbFD+glNcSwgBdcPcTSSqeVODp0d2sAFDhOoanBp0YOs6b0UTjxBhoMfDnsljnMSThMbpflE0bMfOijgsWTssZTqsdTmRZsaelRbxWBsKezotocf6U+iJScWUecQ5lUUwjcf5U0opd5dCcbnwpRLYQSUiWIvpYjliRhtSUMU8eRFiY5ZqTZbSTEflTjosXAt4viUEDEoEsEpAKEtcHFXSfxXsYJfWcJTziFWJeUeFb+tUXJYQrFY8RHiApsjrHQM9tXIeMHKfvJQ4rEYxWIEsZVXUUCPuPrh2RkSlO+IfqNHMKwCZI8cKakB5KlF5D5KQB1eQl1W+pFBUpcRFS+NtWlBdUNcVbkSNYTnXCQAHNsqMFNQHrNYQhrBtYwE1SpQlSonMNUEkcYAiUOalV0c9G1P+pwDDZYiZi4hrO8USUVeqSouRuSXlJSYToTeDbkfSZ8vhcYIlhyVBeWUTF-P4TANTZmLHmhTxsKczKKUXhzBKcwKcYiq6IqWQWWe9TXCSekKQOAPxEDfWSDYtQAOSoBTVJJxzIBBl5xWJMBTVOIIBgnVXXA3Z5jDwBx1Jq2DDGT2lKlMBOk9HKYf6kpazf6aaen-60qAGBWMrGYOwhkCIcphk8rWZ+yRk1JCpSWOYgbirxlSoab8Spn7DADTAr7MDRBMCpCcCHlaCaYlmyZRAOCUEkn52kiEKHSsH2Al0sn2ornCmuruqereoOjKB1Brap0F0+FbaQDF2HTjZp2cnF3u4V0QjF0pED2u77I0BD0dEjFI1DqQYCJ9Ja6jp64G45WqUz3nKHI6Uz3cwV0gJk1j3xUkmT1p092FUoko3IaoZ0ha5Ykr1ihLRr2Q2b2XKFWl5p370XLQy90xCu5SR8In08A93-2z36Xz0PCtiX7SL33KCP2mWnJDSE0gO70f2k1f3jQ-2kZG7F0MkV202YP90V3clM0-Ij3s04NZpc3gqQrinBWSlvpC00Ii2lkC2dDvCH0mJt3p2Z0sA0Cu3YS7i1X0CrW6BBH6TBxWrW2i1cOOQv6omGUo5njgMawb4HiMBvFDnYk9RMoO1QBO1qYu2-7wDUp6ae3+Te3MrBlsr+0WaQHB22Zh3wEgZAA

## Phase 3 Chess Web API

Phase 3 provides an in-memory HTTP API for user sessions and chess game management.
Each request passes through a handler, service, and data access layer so that HTTP,
application, and storage responsibilities remain separate.

| Method | Path | Operation | Authentication |
| --- | --- | --- | --- |
| `DELETE` | `/db` | Clear all application data | None |
| `POST` | `/user` | Register a user | None |
| `POST` | `/session` | Log in | None |
| `DELETE` | `/session` | Log out | `authorization` header |
| `POST` | `/game` | Create a game | `authorization` header |
| `GET` | `/game` | List games | `authorization` header |
| `PUT` | `/game` | Join a game | `authorization` header |

The server stores users, authentication records, and games in memory. Data is reset
whenever the server process restarts. Responses are serialized as JSON, and failures
use a consistent JSON error format.

The Phase 3 checkpoint passes all 23 official API tests and all 245 selected project
tests. The service layer has 100% line and branch coverage for classes that contain
branches.

## Modules

The application has three modules.

- **Client**: The command-line entry point that will become the interactive chess client in later phases.
- **Server**: The HTTP server, request handlers, services, in-memory data access objects, and server tests.
- **Shared**: Chess rules and data models shared by the client and server.

## Starter Code

As you create your chess application you will move through specific phases of development. This starts with implementing the moves of chess and finishes with sending game moves over the network between your client and server. You will start each phase by copying course provided [starter-code](starter-code/) for that phase into the source code of the project. Do not copy a phases' starter code before you are ready to begin work on that phase.

## IntelliJ Support

Open the project directory in IntelliJ in order to develop, run, and debug your code using an IDE.

## Development Setup

This repository uses IntelliJ module files and requires JDK 23.

1. Open the repository root in IntelliJ IDEA.
2. Select JDK 23 as the project SDK.
3. Run `server.ServerMain` to start the server on port `8080`.
4. Run `passoff.server.StandardAPITests` to execute the official Phase 3 API tests.
5. Run the `chess`, `passoff.chess`, `dataaccess`, `model`, `server`, and `service` test packages for the complete project verification set.

Because Phase 3 uses in-memory storage, restarting the server creates an empty application state.
