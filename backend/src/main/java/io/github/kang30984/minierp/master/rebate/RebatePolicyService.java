package io.github.kang30984.minierp.master.rebate;

import io.github.kang30984.minierp.common.BusinessException;
import io.github.kang30984.minierp.common.SearchText;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RebatePolicyService {

    private final RebatePolicyMapper mapper;
    private final Clock clock;

    public RebatePolicyService(RebatePolicyMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    public List<GradeResponse> grades() {
        return mapper.findGrades(LocalDate.now(clock));
    }

    public List<RebatePolicyResponse> policies(String gradeCode) {
        return mapper.findPolicies(SearchText.blankToNull(gradeCode), LocalDate.now(clock));
    }

    /**
     * 요율 변경 등록.
     * 기존 마지막 정책의 종료일을 새 정책 시작일로 닫고, 새 정책을 종료일 없이 추가한다.
     *
     * 1. 시작일은 내일 이후만 허용한다. 오늘이나 과거로 등록하면 이미 출고된 주문에 적용될 요율이 바뀐다.
     * 2. 마지막 정책 행을 FOR UPDATE로 잠가서, 같은 등급을 동시에 변경하는 요청을 순서대로 처리한다.
     * 3. 그래도 기간이 겹치면 DB의 EXCLUDE 제약이 막아 준다.
     */
    @Transactional
    public List<RebatePolicyResponse> register(RebatePolicyRequest request) {
        LocalDate today = LocalDate.now(clock);
        if (!request.validFrom().isAfter(today)) {
            throw BusinessException.badRequest("INVALID_VALID_FROM",
                    "적용 시작일은 내일 이후 날짜로 지정해 주세요. 지난 날짜의 요율을 바꾸면 이미 출고된 주문의 정산 결과가 달라집니다.");
        }
        if (!mapper.existsGrade(request.gradeCode())) {
            throw BusinessException.badRequest("INVALID_GRADE", "존재하지 않는 등급입니다: " + request.gradeCode());
        }

        Optional<OpenPolicy> open = mapper.findOpenPolicyForUpdate(request.gradeCode());
        if (open.isPresent()) {
            LocalDate openFrom = open.get().validFrom();
            if (!request.validFrom().isAfter(openFrom)) {
                throw BusinessException.badRequest("INVALID_VALID_FROM",
                        "적용 시작일은 현재 마지막 정책의 시작일(" + openFrom + ")보다 뒤여야 합니다.");
            }
            mapper.closePolicy(open.get().id(), request.validFrom());
        }

        try {
            mapper.insertPolicy(request.gradeCode(), request.rebateRate(), request.validFrom());
        } catch (DataIntegrityViolationException e) {
            throw BusinessException.conflict("POLICY_OVERLAP",
                    "같은 기간에 적용되는 정책이 이미 있습니다. 목록을 새로 고친 뒤 다시 등록해 주세요.");
        }
        return mapper.findPolicies(request.gradeCode(), today);
    }
}
