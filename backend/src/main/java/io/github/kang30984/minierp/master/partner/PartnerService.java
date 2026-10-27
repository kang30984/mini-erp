package io.github.kang30984.minierp.master.partner;

import io.github.kang30984.minierp.common.BusinessException;
import io.github.kang30984.minierp.common.PageParams;
import io.github.kang30984.minierp.common.PageResponse;
import io.github.kang30984.minierp.common.SearchText;
import io.github.kang30984.minierp.master.rebate.RebatePolicyMapper;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PartnerService {

    private final PartnerMapper partnerMapper;
    private final RebatePolicyMapper rebatePolicyMapper;

    public PartnerService(PartnerMapper partnerMapper, RebatePolicyMapper rebatePolicyMapper) {
        this.partnerMapper = partnerMapper;
        this.rebatePolicyMapper = rebatePolicyMapper;
    }

    public PageResponse<PartnerResponse> search(String keyword, String gradeCode, String status, PageParams page) {
        String pattern = SearchText.containsPattern(keyword);
        String gradeCond = SearchText.blankToNull(gradeCode);
        String statusCond = SearchText.blankToNull(status);

        long total = partnerMapper.count(pattern, gradeCond, statusCond);
        List<PartnerResponse> items = total == 0
                ? List.of()
                : partnerMapper.search(pattern, gradeCond, statusCond, page.size(), page.offset());
        return PageResponse.of(items, page, total);
    }

    public PartnerResponse get(long id) {
        return partnerMapper.findById(id)
                .orElseThrow(() -> BusinessException.notFound("거래처를 찾을 수 없습니다."));
    }

    @Transactional
    public PartnerResponse create(PartnerRequest request) {
        validateGrade(request.gradeCode());
        if (partnerMapper.existsByCode(request.code())) {
            throw duplicateCode(request.code());
        }
        try {
            partnerMapper.insert(request.code(), request.name().strip(), request.gradeCode(), request.status());
        } catch (DuplicateKeyException e) {
            throw duplicateCode(request.code());
        }
        return partnerMapper.findByCode(request.code()).orElseThrow();
    }

    /**
     * 등급을 바꿔도 이미 접수된 주문에는 영향이 없다.
     * 주문에 주문 시점 등급(partner_grade_code)을 따로 저장하기 때문이다.
     */
    @Transactional
    public PartnerResponse update(long id, PartnerRequest request) {
        validateGrade(request.gradeCode());
        int updated = partnerMapper.update(id, request.name().strip(), request.gradeCode(), request.status());
        if (updated == 0) {
            throw BusinessException.notFound("거래처를 찾을 수 없습니다.");
        }
        return get(id);
    }

    private void validateGrade(String gradeCode) {
        if (!rebatePolicyMapper.existsGrade(gradeCode)) {
            throw BusinessException.badRequest("INVALID_GRADE", "존재하지 않는 등급입니다: " + gradeCode);
        }
    }

    private BusinessException duplicateCode(String code) {
        return BusinessException.conflict("DUPLICATE_PARTNER_CODE", "이미 등록된 거래처 코드입니다: " + code);
    }
}
