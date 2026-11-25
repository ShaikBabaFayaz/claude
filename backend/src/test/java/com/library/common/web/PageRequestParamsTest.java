package com.library.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.library.common.error.FieldErrorDto;
import com.library.common.error.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PageRequestParamsTest {

    @Test
    @DisplayName("AC-2.BE.3 defaults are page 0, size 20")
    void AC_2_BE_3_defaults() {
        PageRequestParams params = PageRequestParams.parse(null, null);

        assertThat(params.page()).isZero();
        assertThat(params.size()).isEqualTo(20);
        assertThat(PageRequestParams.defaults()).isEqualTo(params);
    }

    @Test
    @DisplayName("AC-2.BE.3 accepts the boundaries page 0, size 1 and size 100")
    void AC_2_BE_3_boundariesAccepted() {
        assertThat(PageRequestParams.parse("0", "1")).isEqualTo(new PageRequestParams(0, 1));
        assertThat(PageRequestParams.parse("7", "100")).isEqualTo(new PageRequestParams(7, 100));
    }

    @ParameterizedTest(name = "AC-2.BE.3 size={0} is rejected, not clamped")
    @ValueSource(strings = {"101", "0", "-1", "abc", "", "2.5", " 5", "99999999999"})
    void AC_2_BE_3_invalidSizeRejected(String size) {
        assertThatThrownBy(() -> PageRequestParams.parse(null, size))
                .isInstanceOfSatisfying(ValidationException.class, e ->
                        assertThat(e.getFieldErrors()).extracting(FieldErrorDto::field).containsExactly("size"));
    }

    @ParameterizedTest(name = "AC-2.BE.3 page={0} is rejected")
    @ValueSource(strings = {"-1", "abc", "", "1.0", "99999999999"})
    void AC_2_BE_3_invalidPageRejected(String page) {
        assertThatThrownBy(() -> PageRequestParams.parse(page, null))
                .isInstanceOfSatisfying(ValidationException.class, e ->
                        assertThat(e.getFieldErrors()).extracting(FieldErrorDto::field).containsExactly("page"));
    }

    @Test
    @DisplayName("AC-2.BE.3 both offending parameters are listed")
    void AC_2_BE_3_bothInvalidListed() {
        assertThatThrownBy(() -> PageRequestParams.parse("-1", "101"))
                .isInstanceOfSatisfying(ValidationException.class, e ->
                        assertThat(e.getFieldErrors()).extracting(FieldErrorDto::field)
                                .containsExactlyInAnyOrder("page", "size"));
    }

    @Test
    @DisplayName("field error messages never echo the raw value")
    void messagesDoNotEchoInput() {
        assertThatThrownBy(() -> PageRequestParams.parse("secret-page", "secret-size"))
                .isInstanceOfSatisfying(ValidationException.class, e ->
                        assertThat(e.getFieldErrors()).extracting(FieldErrorDto::message)
                                .noneMatch(m -> m.contains("secret")));
    }

    @Test
    @DisplayName("toPageable carries page, size and sort")
    void toPageable() {
        Pageable pageable = new PageRequestParams(2, 30).toPageable(Sort.by("id"));

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(30);
        assertThat(pageable.getSort()).isEqualTo(Sort.by("id"));
    }
}
