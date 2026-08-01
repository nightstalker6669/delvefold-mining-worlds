package com.nightsta69.delvefold.audit;

import java.util.ArrayDeque;
import java.util.Objects;

/** Nest-safe, caller-thread attribution captured before an audit mutation enters the async queue. */
final class AuditActorContext {
    // Actor stacks belong to this service instance, not to all class loaders in the process.
    @SuppressWarnings("ThreadLocalUsage")
    private final ThreadLocal<ArrayDeque<Scope>> stacks = new ThreadLocal<>();

    DelvefoldAuditService.ActorScope push(String actor) {
        String validated = AuditMutation.validatedActor(actor);
        ArrayDeque<Scope> stack = stacks.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            stacks.set(stack);
        }
        Scope scope = new Scope(Thread.currentThread(), validated);
        stack.push(scope);
        return scope;
    }

    String currentActorOrServer() {
        ArrayDeque<Scope> stack = stacks.get();
        Scope current = stack == null ? null : stack.peek();
        return current == null ? "server" : current.actor;
    }

    AuditMutation capture(AuditMutation mutation) {
        Objects.requireNonNull(mutation, "mutation");
        ArrayDeque<Scope> stack = stacks.get();
        Scope current = stack == null ? null : stack.peek();
        if (current == null) {
            return mutation;
        }
        return new AuditMutation(
                current.actor,
                mutation.operation(),
                mutation.affectedObjectType(),
                mutation.affectedObjectId(),
                mutation.oldRevision(),
                mutation.newRevision());
    }

    private final class Scope implements DelvefoldAuditService.ActorScope {
        private final Thread owner;
        private final String actor;
        private boolean closed;

        private Scope(Thread owner, String actor) {
            this.owner = owner;
            this.actor = actor;
        }

        @Override
        // Scope closure intentionally validates the exact owner thread and stack node.
        @SuppressWarnings("ReferenceEquality")
        public void close() {
            if (closed) {
                return;
            }
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("An audit actor scope must close on its owning thread");
            }
            ArrayDeque<Scope> stack = stacks.get();
            if (stack == null || stack.peek() != this) {
                throw new IllegalStateException("Audit actor scopes must close in nesting order");
            }
            stack.pop();
            closed = true;
            if (stack.isEmpty()) {
                stacks.remove();
            }
        }
    }
}
