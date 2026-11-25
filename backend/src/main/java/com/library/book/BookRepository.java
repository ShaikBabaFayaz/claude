package com.library.book;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * ISBN lookups use the <em>normalised</em> ISBN string only (Q-A1, ADR-22). The search specification is added by T-13
 * ({@link BookSpecifications}). Writing services call {@code saveAndFlush}/{@code flush} on this repository so that a
 * constraint violation arrives as a translated {@code DataIntegrityViolationException} (DR-5).
 */
public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    Optional<Book> findByIsbn(String isbn);

    boolean existsByIsbn(String isbn);

    /** True if a book other than {@code id} already has this ISBN (update keeps its own ISBN, AC-1.BE.4). */
    boolean existsByIsbnAndIdNot(String isbn, Long id);
}
