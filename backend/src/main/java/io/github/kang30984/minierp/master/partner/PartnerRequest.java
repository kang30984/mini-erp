package io.github.kang30984.minierp.master.partner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 거래처 등록·수정 요청. 수정할 때 code는 바꾸지 않는다.
 */
public record PartnerRequest(
        @NotBlank(message = "거래처 코드를 입력해 주세요.")
        @Pattern(regexp = "^P\\d{4}$", message = "거래처 코드는 P와 숫자 4자리로 입력해 주세요. (예: P0004)")
        String code,

        @NotBlank(message = "거래처명을 입력해 주세요.")
        @Size(max = 100, message = "거래처명은 100자 이하로 입력해 주세요.")
        String name,

        @NotBlank(message = "등급을 선택해 주세요.")
        String gradeCode,

        @NotBlank(message = "거래 상태를 선택해 주세요.")
        @Pattern(regexp = "ACTIVE|INACTIVE", message = "거래 상태 값이 올바르지 않습니다.")
        String status
) {
}
