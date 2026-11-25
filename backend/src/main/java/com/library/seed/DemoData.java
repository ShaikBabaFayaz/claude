package com.library.seed;

import java.util.List;

/**
 * Fixture definitions of the demo data set (architecture 8, FR-24). Pure data: loan dates are offsets from "today" so the
 * seeder can compute them from the injected {@code Clock}; nothing here is a calendar date.
 */
final class DemoData {

    record BookSpec(String title, String author, String isbn, int totalCopies) {
    }

    record MemberSpec(String name, String email) {
    }

    /** Indexes refer to {@link #BOOKS} / {@link #MEMBERS}; offsets are days relative to today. */
    record LoanSpec(int memberIndex, int bookIndex, int checkoutDaysFromToday, int dueDaysFromToday) {
    }

    /** Check-digit-valid ISBNs: nine ISBN-13 and one ISBN-10. "Design Patterns" has a single copy, which is on loan. */
    static final List<BookSpec> BOOKS = List.of(
            new BookSpec("Clean Code", "Robert C. Martin", "9780132350884", 3),
            new BookSpec("Effective Java", "Joshua Bloch", "9780134685991", 2),
            new BookSpec("Design Patterns", "Erich Gamma et al.", "9780201633610", 1),
            new BookSpec("The Pragmatic Programmer", "David Thomas, Andrew Hunt", "9780135957059", 2),
            new BookSpec("Head First Design Patterns", "Eric Freeman, Elisabeth Robson", "9780596007126", 4),
            new BookSpec("Introduction to Algorithms", "Thomas H. Cormen et al.", "9780262033848", 5),
            new BookSpec("Domain-Driven Design", "Eric Evans", "9780321125217", 2),
            new BookSpec("Spring in Action", "Craig Walls", "9781617294945", 3),
            new BookSpec("The Mythical Man-Month", "Frederick P. Brooks Jr.", "9780201835953", 2),
            new BookSpec("Sample Handbook (ISBN-10 edition)", "Various authors", "0306406152", 1));

    static final List<MemberSpec> MEMBERS = List.of(
            new MemberSpec("Ada Lovelace", "ada.lovelace@example.org"),
            new MemberSpec("Grace Hopper", "grace.hopper@example.org"),
            new MemberSpec("Alan Turing", "alan.turing@example.org"),
            new MemberSpec("Margaret Hamilton", "margaret.hamilton@example.org"),
            new MemberSpec("Linus Torvalds", "linus.torvalds@example.org"));

    static final List<LoanSpec> LOANS = List.of(
            // (a) Ada, Design Patterns (its only copy): checked out 20 days ago, due 6 days ago -> overdue by 6 days
            new LoanSpec(0, 2, -20, -6),
            // (b) Grace, Clean Code: checked out 14 days ago, due today -> NOT overdue (boundary of the definition)
            new LoanSpec(1, 0, -14, 0),
            // (c) Alan, Effective Java: checked out 3 days ago, due in 11 days
            new LoanSpec(2, 1, -3, 11));

    private DemoData() {
    }
}
