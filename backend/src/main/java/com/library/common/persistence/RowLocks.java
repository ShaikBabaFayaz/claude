package com.library.common.persistence;

import com.library.book.Book;
import com.library.common.error.NotFoundException;
import com.library.loan.Loan;
import com.library.member.Member;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;
import org.hibernate.engine.spi.EntityKey;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Lock first, then read" (architecture 7.3, ADR-24, DR-2). Every write path to an existing {@code book}, {@code member}
 * or {@code loan} row takes its row lock here before it reads the state it decides on.
 *
 * <p>{@link #lock} returns state read <em>after</em> the lock was granted, so a request that had to wait sees the other
 * request's committed result:
 * <ul>
 *   <li>entity not yet in the persistence context: {@code find(type, id, PESSIMISTIC_WRITE)};</li>
 *   <li>entity already managed (for example loaded earlier in the same transaction): a plain locking {@code find} would
 *       only lock the row and keep the stale in-memory state, so {@code refresh(entity, PESSIMISTIC_WRITE)} re-reads it.
 *       A refresh discards unflushed changes of that entity: always lock before modifying.</li>
 * </ul>
 * An uninitialised lazy proxy (for example {@code loan.getMember()}) counts as "not yet loaded": the locking {@code find}
 * loads the real row under the lock.
 *
 * <p>Annotated {@code @Repository} so that Spring translates lock timeouts to {@code PessimisticLockingFailureException}
 * (mapped to 409 {@code CONCURRENT_UPDATE}). Requires an active transaction ({@code MANDATORY}).
 */
@Repository
public class RowLocks {

    private final EntityManager entityManager;

    public RowLocks(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * @return the locked entity with post-lock state
     * @throws NotFoundException {@code BOOK_NOT_FOUND}, {@code MEMBER_NOT_FOUND} or {@code LOAN_NOT_FOUND} when the row
     *                           does not exist
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T lock(Class<T> type, Object id) {
        if (id == null) {
            throw notFound(type, null);
        }
        Object managed = managedInstance(type, id);
        try {
            if (managed != null) {
                entityManager.refresh(managed, LockModeType.PESSIMISTIC_WRITE);
                return type.cast(managed);
            }
            T entity = entityManager.find(type, id, LockModeType.PESSIMISTIC_WRITE);
            if (entity == null) {
                throw notFound(type, id);
            }
            return entity;
        } catch (EntityNotFoundException e) {
            // the row disappeared between the earlier load and this refresh
            throw notFound(type, id);
        }
    }

    /** The fully loaded instance of this row in the current persistence context, or null (proxies do not count). */
    private Object managedInstance(Class<?> type, Object id) {
        SharedSessionContractImplementor session = entityManager.unwrap(SharedSessionContractImplementor.class);
        EntityPersister persister = session.getFactory().getMappingMetamodel().getEntityDescriptor(type);
        EntityKey key = session.generateEntityKey(id, persister);
        return session.getPersistenceContextInternal().getEntity(key);
    }

    private static NotFoundException notFound(Class<?> type, Object id) {
        if (type == Book.class) {
            return NotFoundException.book(id);
        }
        if (type == Member.class) {
            return NotFoundException.member(id);
        }
        if (type == Loan.class) {
            return NotFoundException.loan(id);
        }
        throw new IllegalArgumentException("No row lock support for " + type.getName());
    }
}
