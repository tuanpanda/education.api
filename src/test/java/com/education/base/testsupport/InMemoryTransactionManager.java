package com.education.base.testsupport;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.SmartTransactionObject;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Transaction manager giả trong bộ nhớ cho unit test: giữ nguyên ngữ nghĩa propagation / rollback-only của
 * {@link AbstractPlatformTransactionManager} (REQUIRED tham gia, REQUIRES_NEW treo tx ngoài, participant ném lỗi
 * -> global rollback-only -> commit ngoài ném UnexpectedRollbackException) mà không cần DB.
 */
public class InMemoryTransactionManager extends AbstractPlatformTransactionManager {

    public static final class Tx {
        boolean rollbackOnly;
    }

    static final class Holder implements SmartTransactionObject {
        Tx tx;

        Holder(Tx tx) {
            this.tx = tx;
        }

        @Override
        public boolean isRollbackOnly() {
            return tx != null && tx.rollbackOnly;
        }

        @Override
        public void flush() {
        }
    }

    private final ThreadLocal<Deque<Tx>> stack = ThreadLocal.withInitial(ArrayDeque::new);
    public int begun;
    public int commits;
    public int rollbacks;

    @Override
    protected Object doGetTransaction() {
        return new Holder(stack.get().peek());
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) {
        return ((Holder) transaction).tx != null;
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        Tx tx = new Tx();
        ((Holder) transaction).tx = tx;
        stack.get().push(tx);
        begun++;
    }

    @Override
    protected Object doSuspend(Object transaction) {
        ((Holder) transaction).tx = null;
        return stack.get().pop();
    }

    @Override
    protected void doResume(Object transaction, Object suspendedResources) {
        stack.get().push((Tx) suspendedResources);
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        stack.get().pop();
        commits++;
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        stack.get().pop();
        rollbacks++;
    }

    @Override
    protected void doSetRollbackOnly(DefaultTransactionStatus status) {
        ((Holder) status.getTransaction()).tx.rollbackOnly = true;
    }

    public int depth() {
        return stack.get().size();
    }
}
