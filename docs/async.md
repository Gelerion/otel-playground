# Async and Context Propagation

Context does not automatically flow across threads. Use wrapping helpers or capture and re-activate the parent.

## CompletableFuture
```java
Span parent = Span.current();

CompletableFuture.supplyAsync(() -> {
  try (Scope __ = parent.makeCurrent()) {
    Span child = tracer.spanBuilder("async-work").setSpanKind(SpanKind.INTERNAL).startSpan();
    try (Scope ___ = child.makeCurrent()) {
      // work
    } finally {
      child.end();
    }
    return result;
  }
});
```

## Executors
Wrap tasks to carry Context:
```java
Runnable wrapped = Context.current().wrap(task);
executor.submit(wrapped);
```

## Schedulers and reactive libs
Consult library instrumentation or use composition operators that preserve context; verify with traces.

## Pitfalls
- Leaked scopes when exceptions skip closing; prefer try-with-resources.
- Reusing mutable carriers across threads.
