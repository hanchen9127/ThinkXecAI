# ADR 056: The backend runs with an English default locale

- **Status:** Accepted, 2026-11-03

## Context

The browser check before the Phase 13 pull request showed a failed database-tool query in the English UI as `relation "t_orders" does not exist 位置：37`, and another with `建议：Perhaps…`. PostgreSQL's own message was English; the labels "Position" and "Hint" are added by the PostgreSQL JDBC driver, which translates them into the JVM's default language, here Chinese because the machine is. These texts reach the model as tool results and the user in the chat (ADR 055: the UI is English). CI runs in English, so no test saw it.

## Options considered

- **Set the default locale in `main`** (chosen): holds however the backend is started (`mvn spring-boot:run`, an IDE, `java -jar`).
- **A JVM flag (`-Duser.language=en`) in the Maven plugin**: only applies to `mvn spring-boot:run`.
- **Leave it**: only a machine set to another language sees it.

## Decision

`ThinkXecAiApplication.main` sets `Locale.setDefault(Locale.ENGLISH)` before Spring starts. The driver picks its language once, at its first translated message, so it must be set before any database work. All locale categories are set, so number and date formatting does not depend on the machine either. A test sets a Chinese default, shows that the driver's translations would then be found, calls the method, and checks that the driver finds none and keeps its English text.

## Trade-offs

- Every message the JVM or a library localizes is English, including log lines; that matches the rest of the project.
- Tests do not start through `main`; they run with the machine's locale, as before. The method is tested on its own.
