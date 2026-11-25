package com.library.member;

import com.library.common.error.FieldErrorDto;
import com.library.common.web.PageRequestParams;
import com.library.common.web.PageResponse;
import com.library.common.web.QueryParams;
import com.library.member.dto.MemberRequest;
import com.library.member.dto.MemberResponse;
import com.library.member.dto.MemberSearchCriteria;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST API of the members (architecture 6.3). Controllers hold no transaction; the service does. */
@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService service;

    public MemberController(MemberService service) {
        this.service = service;
    }

    /**
     * Paginated member list (AC-3.BE.8) with optional {@code status} filter and the {@code search} extension (D9).
     * Every bad parameter is named in one 400; unknown parameters are ignored.
     */
    @GetMapping
    public PageResponse<MemberResponse> list(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size) {
        List<FieldErrorDto> errors = new ArrayList<>();
        MemberSearchCriteria criteria = MemberSearchCriteria.parse(status, search, errors);
        PageRequestParams paging = QueryParams.paging(page, size, errors);
        QueryParams.failIfAny(errors);
        return service.list(criteria, paging);
    }

    @GetMapping("/{id}")
    public MemberResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<MemberResponse> register(@Valid @RequestBody MemberRequest request) {
        MemberResponse created = service.register(request);
        return ResponseEntity.created(URI.create("/api/members/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public MemberResponse update(@PathVariable Long id, @Valid @RequestBody MemberRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/deactivate")
    public MemberResponse deactivate(@PathVariable Long id) {
        return service.deactivate(id);
    }

    @PostMapping("/{id}/reactivate")
    public MemberResponse reactivate(@PathVariable Long id) {
        return service.reactivate(id);
    }
}
