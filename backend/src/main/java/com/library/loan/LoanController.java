package com.library.loan;

import com.library.common.error.FieldErrorDto;
import com.library.common.web.PageRequestParams;
import com.library.common.web.PageResponse;
import com.library.common.web.QueryParams;
import com.library.loan.dto.CheckoutRequest;
import com.library.loan.dto.LoanResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API of the loans (architecture 6.3). The literal paths {@code /active} and {@code /overdue} are more specific
 * than {@code /{id}}, so Spring always picks them first (DR-13); a non-numeric id elsewhere is a 400 type mismatch.
 */
@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanService service;

    public LoanController(LoanService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<LoanResponse> checkout(@Valid @RequestBody CheckoutRequest request) {
        LoanResponse created = service.checkout(request);
        return ResponseEntity.created(URI.create("/api/loans/" + created.id())).body(created);
    }

    @PostMapping("/{id}/return")
    public LoanResponse returnLoan(@PathVariable Long id) {
        return service.returnLoan(id);
    }

    @GetMapping("/active")
    public PageResponse<LoanResponse> active(
            @RequestParam(name = "memberId", required = false) String memberId,
            @RequestParam(name = "bookId", required = false) String bookId,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size) {
        List<FieldErrorDto> errors = new ArrayList<>();
        Long member = QueryParams.optionalId("memberId", memberId, errors);
        Long book = QueryParams.optionalId("bookId", bookId, errors);
        PageRequestParams paging = QueryParams.paging(page, size, errors);
        QueryParams.failIfAny(errors);
        return service.listActive(member, book, paging);
    }

    @GetMapping("/overdue")
    public List<LoanResponse> overdue() {
        return service.listOverdue();
    }

    @GetMapping("/{id}")
    public LoanResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
