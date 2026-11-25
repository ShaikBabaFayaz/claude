package com.library.loan;

import java.time.LocalDate;

/** Loan rules fixed by the requirements (FR-15, FR-16). Constants on purpose: not configurable (architecture 10.4). */
public final class LoanPolicy {

    public static final int LOAN_PERIOD_DAYS = 14;
    public static final int MAX_ACTIVE_LOANS = 3;

    private LoanPolicy() {
    }

    public static LocalDate dueDate(LocalDate checkoutDate) {
        return checkoutDate.plusDays(LOAN_PERIOD_DAYS);
    }
}
